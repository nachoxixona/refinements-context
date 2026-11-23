package com.refinements.visualizer.card.adapter.out.fetch;

import com.refinements.visualizer.card.application.port.out.SpecSource;
import com.refinements.visualizer.shared.VisualizerProperties;
import com.refinements.visualizer.shared.errors.BadRequestException;
import com.refinements.visualizer.shared.errors.FetchFailedException;
import com.refinements.visualizer.shared.errors.PayloadTooLargeException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Fetches spec documents over HTTP (research R2): http(s) only, connect/read timeouts, a redirect cap, a
 * streaming read capped at {@code visualizer.spec.max-bytes}, the optional host allowlist, and no
 * credentials. Also used for every {@code $ref} document the parser needs (research R1).
 */
@Component
public class SpecFetcher implements SpecSource {

    private final RestClient restClient;
    private final long maxBytes;
    private final int maxRedirects;
    private final List<String> allowedHosts;

    public SpecFetcher(VisualizerProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.fetch().connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.fetch().readTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.maxBytes = props.spec().maxBytes();
        this.maxRedirects = props.fetch().maxRedirects();
        this.allowedHosts = props.fetch().allowedHosts().stream()
                .map(h -> h.toLowerCase(Locale.ROOT))
                .toList();
    }

    /** Validates that a user-supplied URL is an absolute http(s) URL. */
    public static URI validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BadRequestException("A URL is required.");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Not a valid URL: " + url);
        }
        if (!isHttp(uri) || uri.getHost() == null) {
            throw new BadRequestException("Only absolute http and https URLs are supported.");
        }
        return uri;
    }

    public static boolean isHttp(URI uri) {
        String scheme = uri.getScheme();
        return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
    }

    public String fetch(String url) {
        return fetch(validateUrl(url));
    }

    @Override
    public URI validate(String url) {
        return validateUrl(url);
    }

    @Override
    public String fetch(URI start) {
        URI uri = start;
        for (int hop = 0; ; hop++) {
            checkHost(uri);
            final URI current = uri;
            Result result;
            try {
                result = restClient
                        .get()
                        .uri(current)
                        .header(HttpHeaders.ACCEPT, "application/json, application/yaml, text/yaml, */*;q=0.5")
                        .exchange((req, res) -> {
                            int status = res.getStatusCode().value();
                            if (status >= 300 && status < 400) {
                                String location = res.getHeaders().getFirst(HttpHeaders.LOCATION);
                                if (location == null) {
                                    throw new FetchFailedException(
                                            "The server at " + current + " redirected without a location.");
                                }
                                return new Result(null, current.resolve(location));
                            }
                            if (status == 401 || status == 403) {
                                throw new FetchFailedException("The URL " + current + " requires credentials (HTTP "
                                        + status + "), which are not supported.");
                            }
                            if (status < 200 || status >= 300) {
                                throw new FetchFailedException("The URL " + current + " returned HTTP " + status + ".");
                            }
                            return new Result(readCapped(res.getBody()), null);
                        });
            } catch (ResourceAccessException e) {
                throw new FetchFailedException(describe(current, e), e);
            }
            if (result == null) {
                throw new FetchFailedException("The URL " + current + " returned no content.");
            }
            if (result.redirect == null) {
                return result.body;
            }
            if (hop >= maxRedirects) {
                throw new FetchFailedException("Too many redirects (more than " + maxRedirects + ") from " + start);
            }
            if (!isHttp(result.redirect)) {
                throw new FetchFailedException("Redirect to a non-http(s) URL is not allowed: " + result.redirect);
            }
            uri = result.redirect;
        }
    }

    private void checkHost(URI uri) {
        if (!allowedHosts.isEmpty()) {
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!allowedHosts.contains(host)) {
                throw new FetchFailedException("The host " + host + " is not in visualizer.fetch.allowed-hosts.");
            }
        }
    }

    private String readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > maxBytes) {
                throw new PayloadTooLargeException("The spec exceeds the " + (maxBytes / (1024 * 1024)) + " MB limit.");
            }
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String describe(URI uri, ResourceAccessException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException) {
                return "The URL " + uri + " timed out.";
            }
            if (t instanceof ConnectException || t instanceof UnknownHostException) {
                return "The URL " + uri + " is unreachable.";
            }
        }
        return "The URL " + uri + " could not be fetched: "
                + e.getMostSpecificCause().getMessage();
    }

    private record Result(String body, URI redirect) {}
}
