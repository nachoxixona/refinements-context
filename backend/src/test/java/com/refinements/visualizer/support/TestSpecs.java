package com.refinements.visualizer.support;

import com.refinements.visualizer.common.VisualizerProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fixture access and a tiny in-test HTTP server for URL specs. */
public final class TestSpecs {

    private TestSpecs() {}

    public static String read(String resource) {
        try (InputStream in = TestSpecs.class.getResourceAsStream("/specs/" + resource)) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static VisualizerProperties props() {
        return props(List.of());
    }

    public static VisualizerProperties props(List<String> remoteAllowlist) {
        return new VisualizerProperties(
                new VisualizerProperties.Spec(10L * 1024 * 1024),
                new VisualizerProperties.ConnectionsDocument(2L * 1024 * 1024, 5000, 2000),
                new VisualizerProperties.Http(12L * 1024 * 1024),
                new VisualizerProperties.Fetch(Duration.ofSeconds(2), Duration.ofSeconds(5), 3, List.of()),
                new VisualizerProperties.Refs(remoteAllowlist));
    }

    /**
     * Serves files from {@code src/test/resources/specs} and from in-memory overrides. Overrides win, so a
     * test can change what a URL returns to simulate a spec change.
     */
    public static final class SpecServer implements AutoCloseable {
        private final HttpServer server;
        private final Map<String, String> overrides = new ConcurrentHashMap<>();
        private final Map<String, Integer> statuses = new ConcurrentHashMap<>();

        public SpecServer() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath().substring(1);
                Integer status = statuses.get(path);
                byte[] body;
                int code = 200;
                if (status != null) {
                    code = status;
                    body = new byte[0];
                } else if (overrides.containsKey(path)) {
                    body = overrides.get(path).getBytes(StandardCharsets.UTF_8);
                } else {
                    try (InputStream in = TestSpecs.class.getResourceAsStream("/specs/" + path)) {
                        if (in == null) {
                            code = 404;
                            body = new byte[0];
                        } else {
                            body = in.readAllBytes();
                        }
                    }
                }
                exchange.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
                if (body.length > 0) {
                    exchange.getResponseBody().write(body);
                }
                exchange.close();
            });
            server.start();
        }

        public String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + path;
        }

        public void override(String path, String content) {
            overrides.put(path, content);
        }

        public void status(String path, int status) {
            statuses.put(path, status);
        }

        public void clear(String path) {
            overrides.remove(path);
            statuses.remove(path);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    public static Path tempFile(String content) throws IOException {
        Path p = Files.createTempFile("spec", ".yaml");
        Files.writeString(p, content);
        return p;
    }
}
