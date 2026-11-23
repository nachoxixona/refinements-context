package com.refinements.visualizer.connection.document;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * One streaming pass that records where every value starts, keyed by JSON Pointer (research R6). For an object
 * member the position is that of its key, so errors point at the line a person would look at.
 */
public final class JsonLocator {

    public record Position(int line, int column) {}

    private static final JsonFactory FACTORY = new JsonFactory();

    private final Map<String, Position> positions;

    private JsonLocator(Map<String, Position> positions) {
        this.positions = positions;
    }

    /** Locates every value in {@code text}. Text that is not valid JSON yields the positions read so far. */
    public static JsonLocator of(String text) {
        Map<String, Position> out = new HashMap<>();
        try (JsonParser p = FACTORY.createParser(text)) {
            JsonToken t;
            while ((t = p.nextToken()) != null) {
                if (t == JsonToken.END_OBJECT || t == JsonToken.END_ARRAY) {
                    continue;
                }
                // a container token has already entered its new context; its own path is the parent's
                JsonStreamContext ctx = t == JsonToken.START_OBJECT || t == JsonToken.START_ARRAY
                        ? p.getParsingContext().getParent()
                        : p.getParsingContext();
                String pointer = ctx == null ? "" : ctx.pathAsPointer().toString();
                JsonLocation loc = p.currentTokenLocation();
                out.putIfAbsent(pointer, new Position(loc.getLineNr(), loc.getColumnNr()));
            }
        } catch (IOException e) {
            // syntax errors are reported by the reader; keep what was located
        }
        return new JsonLocator(out);
    }

    /** The position of {@code pointer}, or of its nearest located ancestor; null if nothing is known. */
    public Position locate(String pointer) {
        String p = pointer;
        while (true) {
            Position pos = positions.get(p);
            if (pos != null) {
                return pos;
            }
            if (p.isEmpty()) {
                return null;
            }
            int slash = p.lastIndexOf('/');
            p = slash <= 0 ? "" : p.substring(0, slash);
        }
    }
}
