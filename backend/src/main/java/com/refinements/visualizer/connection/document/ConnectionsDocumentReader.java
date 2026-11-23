package com.refinements.visualizer.connection.document;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refinements.visualizer.connection.ConnectionEndpoints;
import com.refinements.visualizer.connection.ConnectionKey;
import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.connection.FlowColor;
import com.refinements.visualizer.connection.document.ConnectionsDocumentModel.ConnectionEntry;
import com.refinements.visualizer.connection.document.ConnectionsDocumentModel.FlowEntry;
import com.refinements.visualizer.connection.document.ConnectionsDocumentModel.MicroserviceEntry;
import com.refinements.visualizer.shared.Names;
import com.refinements.visualizer.shared.VisualizerProperties;
import com.refinements.visualizer.shared.errors.ConnectionsDocumentInvalidException;
import com.refinements.visualizer.shared.errors.DocumentError;
import com.refinements.visualizer.shared.errors.DocumentError.Kind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Parses a connections document from text and validates it as a whole (FR-023, research R4–R6, data-model.md §4).
 * Every error is collected with its JSON Pointer, display path, line and column; any error rejects the
 * document.
 */
@Component
public class ConnectionsDocumentReader {

    public static final int FORMAT_VERSION = 2;

    private static final String PALETTE = java.util.Arrays.stream(FlowColor.values())
            .map(FlowColor::documentValue)
            .collect(java.util.stream.Collectors.joining(", "));

    private static final Set<String> MICROSERVICE = Set.of("name", "description", "team", "repository", "docs");
    private static final Set<String> FLOW = Set.of("name", "color");
    private static final Set<String> CONNECTION =
            Set.of("name", "source", "target", "kind", "description", "flow", "endpoint", "step");

    private final ObjectMapper mapper;
    private final int maxConnections;
    private final int maxMicroservices;

    public ConnectionsDocumentReader(VisualizerProperties props) {
        this.mapper = new ObjectMapper();
        this.mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.maxConnections = props.connectionsDocument().maxConnections();
        this.maxMicroservices = props.connectionsDocument().maxMicroservices();
    }

    /** @throws ConnectionsDocumentInvalidException with every error when the document is not valid */
    public ConnectionsDocumentModel read(String text) {
        JsonNode root;
        try {
            root = mapper.readTree(text == null ? "" : text);
        } catch (JsonProcessingException e) {
            var loc = e.getLocation();
            throw new ConnectionsDocumentInvalidException(List.of(new DocumentError(
                    Kind.SYNTAX,
                    null,
                    null,
                    loc == null || loc.getLineNr() < 1 ? null : loc.getLineNr(),
                    loc == null || loc.getColumnNr() < 1 ? null : loc.getColumnNr(),
                    "Not valid JSON: " + e.getOriginalMessage())));
        }
        if (root == null || root.isMissingNode()) {
            throw new ConnectionsDocumentInvalidException(
                    List.of(new DocumentError(Kind.SYNTAX, null, null, 1, 1, "The document is empty.")));
        }
        Run run = new Run(JsonLocator.of(text));
        ConnectionsDocumentModel model = run.document(root);
        if (!run.errors.isEmpty()) {
            run.errors.sort(Comparator.comparing((DocumentError e) -> e.line() == null ? 0 : e.line())
                    .thenComparing(e -> e.column() == null ? 0 : e.column()));
            throw new ConnectionsDocumentInvalidException(run.errors);
        }
        return model;
    }

    private final class Run {
        private final JsonLocator locator;
        private final List<DocumentError> errors = new ArrayList<>();

        Run(JsonLocator locator) {
            this.locator = locator;
        }

        ConnectionsDocumentModel document(JsonNode root) {
            if (!root.isObject()) {
                error(Kind.WRONG_TYPE, "", "(document)", "The document must be a JSON object.");
                return null;
            }
            JsonNode version = root.get("formatVersion");
            unknown(root, Set.of("formatVersion", "microservices", "flows", "connections"), "", "");
            if (version == null || !version.isIntegralNumber() || version.asInt() != FORMAT_VERSION) {
                error(
                        Kind.UNSUPPORTED_VERSION,
                        version == null ? "" : "/formatVersion",
                        "formatVersion",
                        version == null
                                ? "formatVersion is missing; it must be " + FORMAT_VERSION + "."
                                : "formatVersion " + version + " is not supported; it must be " + FORMAT_VERSION + ".");
            }
            List<MicroserviceEntry> ms = microservices(root.get("microservices"));
            List<FlowEntry> flows = flows(root.get("flows"));
            Set<String> flowNames = new java.util.HashSet<>();
            flows.forEach(g -> flowNames.add(Names.norm(g.name())));
            List<ConnectionEntry> connections = connections(root.get("connections"), flowNames, flows);
            return new ConnectionsDocumentModel(ms, flows, connections);
        }

        List<FlowEntry> flows(JsonNode node) {
            List<FlowEntry> out = new ArrayList<>();
            if (node == null || node.isNull()) {
                return out;
            }
            if (!node.isArray()) {
                error(Kind.WRONG_TYPE, "/flows", "flows", "flows must be an array.");
                return out;
            }
            Map<String, Integer> seen = new HashMap<>();
            for (int i = 0; i < node.size(); i++) {
                String ptr = "/flows/" + i;
                String path = "flows[" + i + "]";
                JsonNode e = node.get(i);
                if (!e.isObject()) {
                    error(Kind.WRONG_TYPE, ptr, path, path + " must be an object.");
                    continue;
                }
                unknown(e, FLOW, ptr, path);
                String name = name(e, "name", ptr, path);
                FlowColor color = color(e, ptr, path);
                if (name == null || color == null) {
                    continue;
                }
                Integer first = seen.putIfAbsent(Names.norm(name), i);
                if (first != null) {
                    error(
                            Kind.DUPLICATE_FLOW,
                            ptr + "/name",
                            path + ".name",
                            path + ".name '" + name + "' is already listed at flows[" + first + "].");
                    continue;
                }
                out.add(new FlowEntry(name, color));
            }
            return out;
        }

        FlowColor color(JsonNode e, String ptr, String path) {
            JsonNode v = e.get("color");
            String p = ptr + "/color";
            String d = path + ".color";
            if (v == null || v.isNull()) {
                error(Kind.MISSING, ptr, d, d + " is missing");
                return null;
            }
            FlowColor c = v.isTextual() ? FlowColor.fromDocument(v.asText()) : null;
            if (c == null) {
                error(Kind.INVALID_COLOR, p, d, d + " is " + v + "; it must be one of " + PALETTE);
            }
            return c;
        }

        List<MicroserviceEntry> microservices(JsonNode node) {
            List<MicroserviceEntry> out = new ArrayList<>();
            if (node == null || node.isNull()) {
                return out;
            }
            if (!node.isArray()) {
                error(Kind.WRONG_TYPE, "/microservices", "microservices", "microservices must be an array.");
                return out;
            }
            if (node.size() > maxMicroservices) {
                error(
                        Kind.TOO_MANY,
                        "/microservices",
                        "microservices",
                        "At most " + maxMicroservices + " microservices are allowed; found " + node.size() + ".");
            }
            Map<String, Integer> seen = new HashMap<>();
            for (int i = 0; i < node.size(); i++) {
                String ptr = "/microservices/" + i;
                String path = "microservices[" + i + "]";
                JsonNode e = node.get(i);
                if (!e.isObject()) {
                    error(Kind.WRONG_TYPE, ptr, path, path + " must be an object.");
                    continue;
                }
                unknown(e, MICROSERVICE, ptr, path);
                String name = name(e, "name", ptr, path);
                JsonNode d = e.get("description");
                String description = description(e, ptr, path);
                String team = team(e, ptr, path);
                String repository = url(e, "repository", ptr, path);
                String docs = url(e, "docs", ptr, path);
                if (name == null) {
                    continue;
                }
                Integer first = seen.putIfAbsent(Names.norm(name), i);
                if (first != null) {
                    error(
                            Kind.DUPLICATE_MICROSERVICE,
                            ptr + "/name",
                            path + ".name",
                            path + ".name '" + name + "' is already listed at microservices[" + first + "].");
                    continue;
                }
                out.add(new MicroserviceEntry(
                        name,
                        description,
                        d != null,
                        team,
                        e.has("team"),
                        repository,
                        e.has("repository"),
                        docs,
                        e.has("docs")));
            }
            return out;
        }

        List<ConnectionEntry> connections(JsonNode node, Set<String> flowNames, List<FlowEntry> flows) {
            List<ConnectionEntry> out = new ArrayList<>();
            if (node == null || node.isNull()) {
                error(
                        Kind.MISSING,
                        "",
                        "connections",
                        "connections is missing; use [] for a document without connections.");
                return out;
            }
            if (!node.isArray()) {
                error(Kind.WRONG_TYPE, "/connections", "connections", "connections must be an array.");
                return out;
            }
            if (node.size() > maxConnections) {
                error(
                        Kind.TOO_MANY,
                        "/connections",
                        "connections",
                        "At most " + maxConnections + " connections are allowed; found " + node.size() + ".");
            }
            Map<ConnectionKey, Integer> seen = new HashMap<>();
            for (int i = 0; i < node.size(); i++) {
                String ptr = "/connections/" + i;
                String path = "connections[" + i + "]";
                JsonNode e = node.get(i);
                if (!e.isObject()) {
                    error(Kind.WRONG_TYPE, ptr, path, path + " must be an object.");
                    continue;
                }
                unknown(e, CONNECTION, ptr, path);
                String name = name(e, "name", ptr, path);
                String source = name(e, "source", ptr, path);
                String target = name(e, "target", ptr, path);
                ConnectionKind kind = kind(e, ptr, path);
                String description = description(e, ptr, path);
                String flow = flow(e, ptr, path, flowNames, flows);
                String endpoint = endpoint(e, ptr, path);
                Integer step = step(e, ptr, path);
                if (step != null
                        && flow == null
                        && !(e.get("flow") != null && !e.get("flow").isNull())) {
                    error(
                            Kind.STEP_WITHOUT_FLOW,
                            ptr + "/step",
                            path + ".step",
                            path + ".step needs a flow; steps order the connections of a flow");
                }
                if (name == null || source == null || target == null) {
                    continue;
                }
                if (Names.norm(source).equals(Names.norm(target))) {
                    error(
                            Kind.SELF_CONNECTION,
                            ptr,
                            path,
                            path + " goes from '" + source
                                    + "' to itself; a connection must go to a different microservice.");
                    continue;
                }
                ConnectionEntry entry =
                        new ConnectionEntry(name, source, target, kind, description, flow, endpoint, step);
                Integer first = seen.putIfAbsent(entry.key(), i);
                if (first != null) {
                    error(
                            Kind.DUPLICATE_CONNECTION,
                            ptr,
                            path,
                            path + " repeats connections[" + first + "]: '" + name + "' from " + source + " to "
                                    + target + " is already declared.");
                    continue;
                }
                out.add(entry);
            }
            return out;
        }

        String name(JsonNode e, String field, String ptr, String path) {
            JsonNode v = e.get(field);
            String p = ptr + "/" + field;
            String d = path + "." + field;
            if (v == null || v.isNull()) {
                error(Kind.MISSING, ptr, d, d + " is missing");
                return null;
            }
            if (!v.isTextual()) {
                error(Kind.WRONG_TYPE, p, d, d + " must be a string");
                return null;
            }
            String clean = Names.clean(v.asText());
            if (clean.isEmpty()) {
                error(Kind.MISSING, p, d, d + " is empty");
                return null;
            }
            if (clean.length() > Names.MAX_NAME) {
                error(Kind.TOO_LONG, p, d, d + " is longer than " + Names.MAX_NAME + " characters");
                return null;
            }
            return clean;
        }

        /** The listed flow's own spelling, or null; an unlisted name is UNKNOWN_FLOW (FR-052). */
        String flow(JsonNode e, String ptr, String path, Set<String> flowNames, List<FlowEntry> flows) {
            JsonNode v = e.get("flow");
            if (v == null || v.isNull()) {
                return null;
            }
            String p = ptr + "/flow";
            String d = path + ".flow";
            if (!v.isTextual()) {
                error(Kind.WRONG_TYPE, p, d, d + " must be a string");
                return null;
            }
            String norm = Names.norm(v.asText());
            if (!flowNames.contains(norm)) {
                error(Kind.UNKNOWN_FLOW, p, d, d + " '" + v.asText() + "' is not listed in flows");
                return null;
            }
            for (FlowEntry g : flows) {
                if (Names.norm(g.name()).equals(norm)) {
                    return g.name();
                }
            }
            return null;
        }

        /** {@code METHOD /path} (FR-056), normalized; anything else is INVALID_ENDPOINT. */
        String endpoint(JsonNode e, String ptr, String path) {
            JsonNode v = e.get("endpoint");
            if (v == null || v.isNull()) {
                return null;
            }
            String p = ptr + "/endpoint";
            String d = path + ".endpoint";
            String normalized = v.isTextual() ? ConnectionEndpoints.normalize(v.asText()) : null;
            if (normalized == null) {
                error(Kind.INVALID_ENDPOINT, p, d, d + " is " + v + "; it must look like \"POST /invoices\"");
            }
            return normalized;
        }

        /** A whole number ≥ 1 (spec 004 data-model.md §5); anything else is INVALID_STEP. */
        Integer step(JsonNode e, String ptr, String path) {
            JsonNode v = e.get("step");
            if (v == null || v.isNull()) {
                return null;
            }
            if (!v.isIntegralNumber() || !v.canConvertToInt() || v.asInt() < 1) {
                error(
                        Kind.INVALID_STEP,
                        ptr + "/step",
                        path + ".step",
                        path + ".step is " + v + "; it must be a whole number of at least 1");
                return null;
            }
            return v.asInt();
        }

        String team(JsonNode e, String ptr, String path) {
            JsonNode v = e.get("team");
            if (v == null || v.isNull()) {
                return null;
            }
            String p = ptr + "/team";
            String d = path + ".team";
            if (!v.isTextual()) {
                error(Kind.WRONG_TYPE, p, d, d + " must be a string");
                return null;
            }
            String clean = Names.clean(v.asText());
            if (clean.length() > com.refinements.visualizer.microservice.Ownership.MAX_TEAM) {
                error(
                        Kind.TOO_LONG,
                        p,
                        d,
                        d + " is longer than " + com.refinements.visualizer.microservice.Ownership.MAX_TEAM
                                + " characters");
                return null;
            }
            return clean.isEmpty() ? null : clean;
        }

        String url(JsonNode e, String field, String ptr, String path) {
            JsonNode v = e.get(field);
            if (v == null || v.isNull()) {
                return null;
            }
            String p = ptr + "/" + field;
            String d = path + "." + field;
            if (!v.isTextual()
                    || !com.refinements.visualizer.microservice.Ownership.isValidUrl(
                            v.asText().trim())) {
                error(Kind.INVALID_URL, p, d, d + " is " + v + "; it must be an http or https URL");
                return null;
            }
            return v.asText().trim();
        }

        ConnectionKind kind(JsonNode e, String ptr, String path) {
            JsonNode v = e.get("kind");
            if (v == null || v.isNull()) {
                return null;
            }
            String p = ptr + "/kind";
            String d = path + ".kind";
            if (!v.isTextual()) {
                error(Kind.WRONG_TYPE, p, d, d + " must be \"request\" or \"event\"");
                return null;
            }
            ConnectionKind kind = ConnectionKind.fromDocument(v.asText());
            if (kind == null) {
                error(Kind.INVALID_KIND, p, d, d + " is '" + v.asText() + "'; it must be \"request\" or \"event\"");
            }
            return kind;
        }

        String description(JsonNode e, String ptr, String path) {
            JsonNode v = e.get("description");
            if (v == null || v.isNull()) {
                return null;
            }
            String p = ptr + "/description";
            String d = path + ".description";
            if (!v.isTextual()) {
                error(Kind.WRONG_TYPE, p, d, d + " must be a string");
                return null;
            }
            if (v.asText().length() > Names.MAX_DESCRIPTION) {
                error(Kind.TOO_LONG, p, d, d + " is longer than " + Names.MAX_DESCRIPTION + " characters");
                return null;
            }
            return v.asText();
        }

        void unknown(JsonNode obj, Set<String> allowed, String ptr, String path) {
            for (Iterator<String> it = obj.fieldNames(); it.hasNext(); ) {
                String f = it.next();
                if (!allowed.contains(f)) {
                    String d = path.isEmpty() ? f : path + "." + f;
                    error(Kind.UNKNOWN_PROPERTY, ptr + "/" + escape(f), d, d + " is not a known property");
                }
            }
        }

        void error(Kind kind, String pointer, String path, String message) {
            JsonLocator.Position pos = locator.locate(pointer);
            errors.add(new DocumentError(
                    kind, pointer, path, pos == null ? null : pos.line(), pos == null ? null : pos.column(), message));
        }
    }

    private static String escape(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }
}
