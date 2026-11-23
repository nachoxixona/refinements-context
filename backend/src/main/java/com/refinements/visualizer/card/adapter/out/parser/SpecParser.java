package com.refinements.visualizer.card.adapter.out.parser;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.refinements.visualizer.card.adapter.out.fetch.SpecFetcher;
import com.refinements.visualizer.card.model.SpecFormat;
import com.refinements.visualizer.shared.VisualizerProperties;
import com.refinements.visualizer.shared.errors.FetchFailedException;
import com.refinements.visualizer.shared.errors.ParseError;
import com.refinements.visualizer.shared.errors.PayloadTooLargeException;
import com.refinements.visualizer.shared.errors.SpecInvalidException;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;

/**
 * Deterministic spec parsing with {@code io.swagger.parser.v3:swagger-parser} (research R1, Principle I).
 *
 * <p>{@code OpenAPIParser.readContents} has no base-location overload in the pinned 2.1.x version (checked
 * during T034), so external {@code $ref}s use the fallback R1 describes: the parser never touches the
 * network itself. Every referenced document is fetched through {@link SpecFetcher} (same host as the spec
 * URL, or a host on {@code visualizer.refs.remote-allowlist}), staged in a private temporary directory with
 * its refs rewritten to local files, and the parser reads only those local files. Refs that are not allowed
 * or cannot be found are reported with their location and the spec is rejected as a whole.
 */
@Component
public class SpecParser {

    private static final int MAX_DOCUMENTS = 50;
    private static final Pattern ATTRIBUTE_MESSAGE = Pattern.compile("^attribute (\\S+)\\s.*$");
    private static final Pattern REF_PROBLEM =
            Pattern.compile("\\$ref|reference|resolv|could not find", Pattern.CASE_INSENSITIVE);

    private final SpecFetcher fetcher;
    private final long maxBytes;
    private final List<String> remoteAllowlist;
    private final ObjectMapper yaml;
    private final ObjectMapper json = new ObjectMapper();

    public SpecParser(SpecFetcher fetcher, VisualizerProperties props) {
        this.fetcher = fetcher;
        this.maxBytes = props.spec().maxBytes();
        this.remoteAllowlist = props.refs().remoteAllowlist().stream()
                .map(h -> h.toLowerCase(Locale.ROOT))
                .toList();
        LoaderOptions loader = new LoaderOptions();
        loader.setCodePointLimit((int) Math.min(Integer.MAX_VALUE, maxBytes * 2));
        this.yaml = new ObjectMapper(YAMLFactory.builder().loaderOptions(loader).build());
    }

    /**
     * @param content raw JSON or YAML spec text
     * @param baseLocation the URL the spec was fetched from, or {@code null} for pasted specs
     */
    public ParsedSpec parse(String content, URI baseLocation) {
        if (content == null || content.isBlank()) {
            throw new SpecInvalidException(null, "The spec is empty.");
        }
        JsonNode root = readTree(content, null);
        if (!root.isObject() || !(root.has("openapi") || root.has("swagger"))) {
            throw new SpecInvalidException(
                    null, "Not an OpenAPI or Swagger document: the root has no 'openapi' or 'swagger' key.");
        }
        SpecFormat format = detectFormat(root);

        Staging staging = new Staging(baseLocation);
        staging.collect((ObjectNode) root, baseLocation, label(baseLocation, true));
        if (!staging.errors.isEmpty()) {
            throw new SpecInvalidException(staging.errors);
        }

        SwaggerParseResult result = staging.documents.isEmpty() ? parseInline(root) : parseStaged(root, staging);
        OpenAPI model = result == null ? null : result.getOpenAPI();
        List<ParseError> errors = new ArrayList<>();
        List<String> messages = result == null || result.getMessages() == null ? List.of() : result.getMessages();
        if (model == null) {
            messages.forEach(m -> errors.add(toParseError(m)));
            if (errors.isEmpty()) {
                errors.add(new ParseError(null, "The document could not be parsed as OpenAPI or Swagger."));
            }
            throw new SpecInvalidException(errors);
        }
        messages.stream().filter(m -> REF_PROBLEM.matcher(m).find()).forEach(m -> errors.add(toParseError(m)));
        errors.addAll(findUnresolvedRefs(model));
        if (!errors.isEmpty()) {
            throw new SpecInvalidException(errors);
        }
        return new ParsedSpec(model, format);
    }

    static SpecFormat detectFormat(JsonNode root) {
        if (root.has("swagger")) {
            return SpecFormat.SWAGGER_2_0;
        }
        return root.path("openapi").asText("").startsWith("3.1") ? SpecFormat.OPENAPI_3_1 : SpecFormat.OPENAPI_3_0;
    }

    private static ParseOptions options() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        options.setResolveCombinators(false);
        options.setValidateInternalRefs(true);
        return options;
    }

    private SwaggerParseResult parseInline(JsonNode root) {
        return new OpenAPIParser().readContents(toJson(root), null, options());
    }

    private SwaggerParseResult parseStaged(JsonNode root, Staging staging) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("spec-staging-");
            for (StagedDocument doc : staging.documents.values()) {
                Files.writeString(dir.resolve(doc.fileName), toJson(doc.tree), StandardCharsets.UTF_8);
            }
            Path rootFile = dir.resolve("root.json");
            Files.writeString(rootFile, toJson(root), StandardCharsets.UTF_8);
            return new OpenAPIParser().readLocation(rootFile.toUri().toString(), null, options());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(dir);
        }
    }

    private String toJson(JsonNode node) {
        try {
            return json.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode readTree(String content, String docLabel) {
        try {
            JsonNode node = yaml.readTree(content);
            if (node == null || node.isMissingNode()) {
                throw new SpecInvalidException(docLabel, "The document is empty.");
            }
            return node;
        } catch (JsonProcessingException e) {
            JsonLocation loc = e.getLocation();
            String where = loc == null ? null : "line " + loc.getLineNr() + ", column " + loc.getColumnNr();
            String location = docLabel == null ? where : where == null ? docLabel : docLabel + " " + where;
            throw new SpecInvalidException(location, "Not valid JSON or YAML: " + e.getOriginalMessage());
        }
    }

    static ParseError toParseError(String message) {
        Matcher m = ATTRIBUTE_MESSAGE.matcher(message);
        return new ParseError(m.matches() ? m.group(1) : null, message);
    }

    private static String label(URI uri, boolean root) {
        if (uri == null) {
            return root ? "" : "?";
        }
        return root ? "" : uri.toString();
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // best effort clean-up of the private staging directory
        }
    }

    // ---------------------------------------------------------------- external $ref staging

    private record StagedDocument(URI uri, String fileName, JsonNode tree) {}

    private final class Staging {
        private final URI rootUri;
        private final String rootHost;
        private final Map<URI, StagedDocument> documents = new LinkedHashMap<>();
        private final List<ParseError> errors = new ArrayList<>();
        private long totalBytes;

        Staging(URI rootUri) {
            this.rootUri = rootUri;
            this.rootHost = rootUri == null || rootUri.getHost() == null
                    ? null
                    : rootUri.getHost().toLowerCase(Locale.ROOT);
        }

        /** Walks {@code doc}, validates every ref, loads external documents and rewrites refs to local files. */
        void collect(ObjectNode docRoot, URI docUri, String docLabel) {
            Deque<Object[]> stack = new ArrayDeque<>();
            stack.push(new Object[] {docRoot, ""});
            while (!stack.isEmpty()) {
                Object[] item = stack.pop();
                JsonNode node = (JsonNode) item[0];
                String path = (String) item[1];
                if (node.isObject()) {
                    JsonNode ref = node.get("$ref");
                    if (ref != null && ref.isTextual()) {
                        handleRef((ObjectNode) node, ref.asText(), docRoot, docUri, location(docLabel, path));
                    }
                    List<Map.Entry<String, JsonNode>> children = new ArrayList<>();
                    node.fields().forEachRemaining(children::add);
                    Collections.reverse(children);
                    for (Map.Entry<String, JsonNode> e : children) {
                        if (e.getValue().isContainerNode()) {
                            stack.push(
                                    new Object[] {e.getValue(), path.isEmpty() ? e.getKey() : path + "." + e.getKey()});
                        }
                    }
                } else if (node.isArray()) {
                    for (int i = node.size() - 1; i >= 0; i--) {
                        if (node.get(i).isContainerNode()) {
                            stack.push(new Object[] {node.get(i), path + "[" + i + "]"});
                        }
                    }
                }
            }
        }

        private String location(String docLabel, String path) {
            if (docLabel == null || docLabel.isEmpty()) {
                return path.isEmpty() ? null : path;
            }
            return path.isEmpty() ? docLabel : docLabel + " " + path;
        }

        private void handleRef(ObjectNode holder, String ref, JsonNode docRoot, URI docUri, String location) {
            int hash = ref.indexOf('#');
            String filePart = hash < 0 ? ref : ref.substring(0, hash);
            String fragment = hash < 0 ? null : ref.substring(hash + 1);

            if (filePart.isEmpty()) {
                if (!pointerExists(docRoot, fragment)) {
                    errors.add(new ParseError(
                            location, "Unresolved reference '" + ref + "': nothing exists at that location."));
                }
                return;
            }

            URI target;
            try {
                URI refUri = URI.create(filePart);
                if (refUri.isAbsolute()) {
                    target = refUri;
                } else if (docUri == null) {
                    errors.add(
                            new ParseError(
                                    location,
                                    "Unresolved reference '" + ref
                                            + "': relative references cannot be resolved in a pasted spec. Register the spec by URL instead."));
                    return;
                } else {
                    target = docUri.resolve(refUri);
                }
            } catch (IllegalArgumentException e) {
                errors.add(new ParseError(location, "Unresolved reference '" + ref + "': not a valid URI."));
                return;
            }

            if (!SpecFetcher.isHttp(target) || target.getHost() == null) {
                errors.add(new ParseError(
                        location, "Unresolved reference '" + ref + "': only http(s) references are followed."));
                return;
            }
            String host = target.getHost().toLowerCase(Locale.ROOT);
            boolean sameHost = rootHost != null && host.equals(rootHost);
            if (!sameHost && !remoteAllowlist.contains(host)) {
                errors.add(
                        new ParseError(
                                location,
                                "Unresolved reference '" + ref + "': references to host '" + host
                                        + "' are not allowed (it is not the spec's host and not on visualizer.refs.remote-allowlist)."));
                return;
            }

            StagedDocument doc = load(target, location, ref);
            if (doc == null) {
                return;
            }
            if (fragment != null && !fragment.isEmpty() && !pointerExists(doc.tree, fragment)) {
                errors.add(new ParseError(
                        location, "Unresolved reference '" + ref + "': nothing exists at that location."));
                return;
            }
            holder.put("$ref", "./" + doc.fileName + (fragment == null ? "" : "#" + fragment));
        }

        private StagedDocument load(URI target, String location, String ref) {
            URI key = stripFragment(target);
            StagedDocument existing = documents.get(key);
            if (existing != null) {
                return existing;
            }
            if (documents.size() >= MAX_DOCUMENTS) {
                errors.add(new ParseError(
                        location,
                        "Unresolved reference '" + ref + "': more than " + MAX_DOCUMENTS + " referenced documents."));
                return null;
            }
            String content;
            try {
                content = fetcher.fetch(key);
            } catch (FetchFailedException | PayloadTooLargeException e) {
                errors.add(new ParseError(location, "Unresolved reference '" + ref + "': " + e.getMessage()));
                return null;
            }
            totalBytes += content.getBytes(StandardCharsets.UTF_8).length;
            if (totalBytes > maxBytes) {
                errors.add(new ParseError(
                        location,
                        "Unresolved reference '" + ref
                                + "': the referenced documents together exceed the size limit."));
                return null;
            }
            JsonNode tree;
            try {
                tree = readTree(content, key.toString());
            } catch (SpecInvalidException e) {
                errors.addAll(e.getErrors());
                return null;
            }
            StagedDocument doc = new StagedDocument(key, "doc-" + (documents.size() + 1) + ".json", tree);
            documents.put(key, doc);
            if (tree.isObject()) {
                collect((ObjectNode) tree, key, relativeLabel(key));
            }
            return doc;
        }

        private String relativeLabel(URI uri) {
            if (rootUri != null) {
                String rootStr = rootUri.toString();
                String base = rootStr.substring(0, rootStr.lastIndexOf('/') + 1);
                if (uri.toString().startsWith(base)) {
                    return uri.toString().substring(base.length()) + ":";
                }
            }
            return uri + ":";
        }
    }

    private static URI stripFragment(URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null);
        } catch (Exception e) {
            return uri;
        }
    }

    private static boolean pointerExists(JsonNode doc, String fragment) {
        if (fragment == null || fragment.isEmpty()) {
            return true;
        }
        String decoded = URLDecoder.decode(fragment.replace("+", "%2B"), StandardCharsets.UTF_8);
        try {
            return !doc.at(JsonPointer.compile(decoded)).isMissingNode();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- post-parse check

    /**
     * Any schema that still carries a {@code $ref} after full resolution means a ref was not resolved. The
     * one exception is a ref to an existing component schema: swagger-parser keeps those to break recursive
     * schemas, and {@link FieldFlattener} follows them with its cycle guard.
     */
    private static List<ParseError> findUnresolvedRefs(OpenAPI model) {
        List<ParseError> errors = new ArrayList<>();
        if (model.getPaths() == null) {
            return errors;
        }
        Set<String> componentRefs = new java.util.HashSet<>();
        if (model.getComponents() != null && model.getComponents().getSchemas() != null) {
            model.getComponents().getSchemas().keySet().forEach(n -> componentRefs.add("#/components/schemas/" + n));
        }
        Set<Schema<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        model.getPaths().forEach((path, item) -> {
            String base = "paths." + path;
            checkParams(item.getParameters(), base, seen, componentRefs, errors);
            for (Map.Entry<PathItem.HttpMethod, Operation> e :
                    item.readOperationsMap().entrySet()) {
                String opBase = base + "." + e.getKey().name().toLowerCase(Locale.ROOT);
                Operation op = e.getValue();
                checkParams(op.getParameters(), opBase, seen, componentRefs, errors);
                if (op.getRequestBody() != null) {
                    if (op.getRequestBody().get$ref() != null) {
                        errors.add(unresolved(
                                opBase + ".requestBody", op.getRequestBody().get$ref()));
                    } else if (op.getRequestBody().getContent() != null) {
                        checkContent(
                                op.getRequestBody().getContent(),
                                opBase + ".requestBody.content",
                                seen,
                                componentRefs,
                                errors);
                    }
                }
                if (op.getResponses() != null) {
                    for (Map.Entry<String, ApiResponse> r : op.getResponses().entrySet()) {
                        String rBase = opBase + ".responses." + r.getKey();
                        if (r.getValue().get$ref() != null) {
                            errors.add(unresolved(rBase, r.getValue().get$ref()));
                        } else if (r.getValue().getContent() != null) {
                            checkContent(r.getValue().getContent(), rBase + ".content", seen, componentRefs, errors);
                        }
                    }
                }
            }
        });
        return errors;
    }

    private static void checkParams(
            List<Parameter> params, String base, Set<Schema<?>> seen, Set<String> ok, List<ParseError> errors) {
        if (params == null) {
            return;
        }
        for (Parameter p : params) {
            if (p.get$ref() != null) {
                errors.add(unresolved(base + ".parameters", p.get$ref()));
            } else if (p.getSchema() != null) {
                checkSchema(p.getSchema(), base + ".parameters." + p.getName(), seen, ok, errors);
            }
        }
    }

    private static void checkContent(
            Map<String, MediaType> content, String base, Set<Schema<?>> seen, Set<String> ok, List<ParseError> errors) {
        content.forEach((type, media) -> {
            if (media != null && media.getSchema() != null) {
                checkSchema(media.getSchema(), base + "." + type + ".schema", seen, ok, errors);
            }
        });
    }

    @SuppressWarnings("rawtypes")
    private static void checkSchema(
            Schema<?> schema, String location, Set<Schema<?>> seen, Set<String> ok, List<ParseError> errors) {
        if (schema == null || !seen.add(schema)) {
            return;
        }
        if (schema.get$ref() != null) {
            if (!ok.contains(schema.get$ref())) {
                errors.add(unresolved(location, schema.get$ref()));
            }
            return;
        }
        if (schema.getProperties() != null) {
            for (Iterator<Map.Entry<String, Schema>> it =
                            schema.getProperties().entrySet().iterator();
                    it.hasNext(); ) {
                Map.Entry<String, Schema> e = it.next();
                checkSchema(e.getValue(), location + ".properties." + e.getKey(), seen, ok, errors);
            }
        }
        if (schema.getItems() != null) {
            checkSchema(schema.getItems(), location + ".items", seen, ok, errors);
        }
        for (String kind : List.of("allOf", "oneOf", "anyOf")) {
            List<Schema> members =
                    switch (kind) {
                        case "allOf" -> schema.getAllOf();
                        case "oneOf" -> schema.getOneOf();
                        default -> schema.getAnyOf();
                    };
            if (members != null) {
                for (int i = 0; i < members.size(); i++) {
                    checkSchema(members.get(i), location + "." + kind + "[" + i + "]", seen, ok, errors);
                }
            }
        }
    }

    private static ParseError unresolved(String location, String ref) {
        return new ParseError(location, "Unresolved reference '" + ref + "'.");
    }
}
