package com.refinements.visualizer.card.adapter.out.parser;

import com.refinements.visualizer.card.model.Field;
import com.refinements.visualizer.card.model.FieldLocation;
import com.refinements.visualizer.card.model.HttpMethod;
import com.refinements.visualizer.card.model.ParsedEndpoint;
import com.refinements.visualizer.card.model.SpecKeys;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Turns a resolved OpenAPI model into flat, path-qualified input and output fields per endpoint (research
 * R3). Computed once on the backend so the browser never parses schemas (Principle V).
 */
@Component
public class FieldFlattener {

    /** Fields at this depth are not expanded further and are marked {@code truncated}. */
    public static final int MAX_DEPTH = 6;

    private static final String COMPONENT_PREFIX = "#/components/schemas/";

    public List<ParsedEndpoint> flatten(OpenAPI api, String serviceKey) {
        return new Run(api).flatten(serviceKey);
    }

    static ApiResponse lowestSuccess(ApiResponses responses) {
        if (responses == null) {
            return null;
        }
        ApiResponse best = null;
        int bestCode = Integer.MAX_VALUE;
        for (Map.Entry<String, ApiResponse> e : responses.entrySet()) {
            String code = e.getKey().trim().toUpperCase(Locale.ROOT);
            int numeric;
            if (code.equals("2XX")) {
                numeric = 299;
            } else {
                try {
                    numeric = Integer.parseInt(code);
                } catch (NumberFormatException ex) {
                    continue;
                }
            }
            if (numeric >= 200 && numeric < 300 && numeric < bestCode) {
                bestCode = numeric;
                best = e.getValue();
            }
        }
        return best;
    }

    /** {@code application/json}, then the first JSON-like media type, then the first one (R3). */
    static Schema<?> preferredSchema(Content content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        MediaType chosen = content.get("application/json");
        if (chosen == null) {
            chosen = content.entrySet().stream()
                    .filter(e -> e.getKey().toLowerCase(Locale.ROOT).contains("json"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(content.values().iterator().next());
        }
        return chosen == null ? null : chosen.getSchema();
    }

    static String typeOf(Schema<?> schema) {
        if (schema == null) {
            return "unknown";
        }
        if (schema.getType() != null) {
            return schema.getType();
        }
        if (schema.getTypes() != null) {
            for (Object t : schema.getTypes()) {
                if (t != null && !"null".equals(t)) {
                    return t.toString();
                }
            }
        }
        if (schema.getProperties() != null
                || schema.getAllOf() != null
                || schema.getOneOf() != null
                || schema.getAnyOf() != null
                || schema.get$ref() != null) {
            return "object";
        }
        if (schema.getItems() != null) {
            return "array";
        }
        return "unknown";
    }

    private static FieldLocation paramLocation(String in) {
        if (in == null) {
            return null;
        }
        return switch (in.toLowerCase(Locale.ROOT)) {
            case "path" -> FieldLocation.PATH;
            case "query" -> FieldLocation.QUERY;
            case "header" -> FieldLocation.HEADER;
            default -> null;
        };
    }

    /** One flattening pass; holds the component schemas that recursive {@code $ref}s point to. */
    private static final class Run {
        @SuppressWarnings("rawtypes")
        private final Map<String, Schema> components;

        private final OpenAPI api;

        Run(OpenAPI api) {
            this.api = api;
            this.components = api.getComponents() == null || api.getComponents().getSchemas() == null
                    ? Map.of()
                    : api.getComponents().getSchemas();
        }

        List<ParsedEndpoint> flatten(String serviceKey) {
            List<ParsedEndpoint> endpoints = new ArrayList<>();
            if (api.getPaths() == null) {
                return endpoints;
            }
            for (Map.Entry<String, PathItem> pathEntry : api.getPaths().entrySet()) {
                String path = pathEntry.getKey();
                PathItem item = pathEntry.getValue();
                for (Map.Entry<PathItem.HttpMethod, Operation> opEntry :
                        item.readOperationsMap().entrySet()) {
                    HttpMethod method = HttpMethod.valueOf(opEntry.getKey().name());
                    Operation op = opEntry.getValue();
                    endpoints.add(new ParsedEndpoint(
                            SpecKeys.endpointKey(serviceKey, method.name(), path),
                            method,
                            path,
                            op.getOperationId(),
                            op.getSummary(),
                            inputs(item, op),
                            outputs(op)));
                }
            }
            endpoints.sort(Comparator.comparing(ParsedEndpoint::path)
                    .thenComparing(e -> e.method().ordinal()));
            return endpoints;
        }

        private List<Field> inputs(PathItem item, Operation op) {
            Map<String, Parameter> params = new LinkedHashMap<>();
            for (List<Parameter> list : Arrays.asList(item.getParameters(), op.getParameters())) {
                if (list != null) {
                    for (Parameter p : list) {
                        params.put(p.getIn() + ":" + p.getName(), p);
                    }
                }
            }
            Collector out = new Collector();
            for (Parameter p : params.values()) {
                FieldLocation location = paramLocation(p.getIn());
                if (location == null) {
                    continue; // cookie parameters are skipped (R3)
                }
                boolean required = location == FieldLocation.PATH || Boolean.TRUE.equals(p.getRequired());
                out.add(field(p.getName(), p.getName(), deref(p.getSchema()), location, required, null, false));
            }
            if (op.getRequestBody() != null) {
                Schema<?> body = preferredSchema(op.getRequestBody().getContent());
                if (body != null) {
                    walk(body, "", FieldLocation.BODY, 1, newPathSet(), null, out);
                }
            }
            return out.fields();
        }

        private List<Field> outputs(Operation op) {
            Collector out = new Collector();
            ApiResponse response = lowestSuccess(op.getResponses());
            if (response != null) {
                Schema<?> schema = preferredSchema(response.getContent());
                if (schema != null) {
                    walk(schema, "", FieldLocation.RESPONSE, 1, newPathSet(), null, out);
                }
            }
            return out.fields();
        }

        /** Follows a recursive {@code $ref} kept by swagger-parser to its component schema. */
        private Schema<?> deref(Schema<?> schema) {
            if (schema != null && schema.get$ref() != null && schema.get$ref().startsWith(COMPONENT_PREFIX)) {
                Schema<?> target = components.get(schema.get$ref().substring(COMPONENT_PREFIX.length()));
                if (target != null) {
                    return target;
                }
            }
            return schema;
        }

        private static Set<Schema<?>> newPathSet() {
            return Collections.newSetFromMap(new IdentityHashMap<>());
        }

        /** Emits the properties of {@code schema} under {@code prefix}; merges allOf, tags oneOf/anyOf branches. */
        @SuppressWarnings({"rawtypes", "unchecked"})
        private void walk(
                Schema<?> raw,
                String prefix,
                FieldLocation location,
                int depth,
                Set<Schema<?>> onPath,
                Integer variant,
                Collector out) {
            Schema<?> schema = deref(raw);
            if (schema == null) {
                return;
            }
            if (schema.getAllOf() != null) {
                for (Schema member : schema.getAllOf()) {
                    walk(member, prefix, location, depth, onPath, variant, out);
                }
            }
            List<Schema> branches = schema.getOneOf() != null ? schema.getOneOf() : schema.getAnyOf();
            if (branches != null) {
                for (int i = 0; i < branches.size(); i++) {
                    walk(branches.get(i), prefix, location, depth, onPath, variant != null ? variant : i, out);
                }
            }
            Map<String, Schema> properties = schema.getProperties();
            if (properties != null) {
                List<String> required = schema.getRequired() == null ? List.of() : schema.getRequired();
                for (Map.Entry<String, Schema> e : properties.entrySet()) {
                    property(
                            e.getKey(),
                            e.getValue(),
                            prefix,
                            required.contains(e.getKey()),
                            location,
                            depth,
                            onPath,
                            variant,
                            out);
                }
            }
            if (prefix.isEmpty() && properties == null && "array".equals(typeOf(schema))) {
                // a top-level array body or response: its item properties live under "[]"
                Schema<?> items = deref(schema.getItems());
                if (isObjectLike(items) && onPath.add(items)) {
                    walk(items, "[]", location, depth, onPath, variant, out);
                    onPath.remove(items);
                }
            }
        }

        private void property(
                String name,
                Schema<?> raw,
                String prefix,
                boolean required,
                FieldLocation location,
                int depth,
                Set<Schema<?>> onPath,
                Integer variant,
                Collector out) {
            Schema<?> prop = deref(raw);
            String path = prefix.isEmpty() ? name : prefix + "." + name;
            Schema<?> target = null;
            String childPrefix = null;
            if (isObjectLike(prop)) {
                target = prop;
                childPrefix = path;
            } else if ("array".equals(typeOf(prop)) && prop.getItems() != null) {
                Schema<?> items = deref(prop.getItems());
                if (isObjectLike(items)) {
                    target = items;
                    childPrefix = path + "[]";
                }
            }
            boolean truncated = target != null && (depth >= MAX_DEPTH || onPath.contains(target));
            out.add(field(path, name, prop, location, required, variant, truncated));
            if (target != null && !truncated) {
                onPath.add(target);
                walk(target, childPrefix, location, depth + 1, onPath, variant, out);
                onPath.remove(target);
            }
        }

        private static Field field(
                String path,
                String name,
                Schema<?> schema,
                FieldLocation location,
                boolean required,
                Integer variant,
                boolean truncated) {
            return new Field(
                    path,
                    name,
                    FieldNormalizer.normalize(name),
                    typeOf(schema),
                    schema == null ? null : schema.getFormat(),
                    location,
                    required,
                    variant,
                    truncated);
        }

        private static boolean isObjectLike(Schema<?> schema) {
            return schema != null
                    && (schema.getProperties() != null
                            || schema.getAllOf() != null
                            || schema.getOneOf() != null
                            || schema.getAnyOf() != null);
        }
    }

    /** Keeps the first field per (path, location, variant), so allOf overlaps do not duplicate fields. */
    private static final class Collector {
        private final Map<String, Field> byKey = new LinkedHashMap<>();

        void add(Field f) {
            byKey.putIfAbsent(f.location() + "|" + f.path() + "|" + f.variant(), f);
        }

        List<Field> fields() {
            return new ArrayList<>(byKey.values());
        }
    }
}
