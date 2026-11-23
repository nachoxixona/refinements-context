package com.refinements.visualizer.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.refinements.visualizer.shared.errors.PayloadTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects request bodies larger than {@code visualizer.http.max-body-bytes} with 413 (T011). This is the
 * HTTP envelope limit; the 10 MB spec limit is checked separately on the decoded spec content.
 */
@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    /** Connections documents are capped at 2 MB of text; the envelope gets a little room for JSON escaping. */
    static final String CONNECTIONS_DOCUMENT_PATH = "/api/connections-document";

    private final long maxBytes;
    private final long connectionsDocumentMaxBytes;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(VisualizerProperties props, ObjectMapper objectMapper) {
        this.maxBytes = props.http().maxBodyBytes();
        this.connectionsDocumentMaxBytes = props.connectionsDocument().maxBytes()
                + props.connectionsDocument().maxBytes() / 4;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit =
                request.getRequestURI().startsWith(CONNECTIONS_DOCUMENT_PATH) ? connectionsDocumentMaxBytes : maxBytes;
        long declared = request.getContentLengthLong();
        if (declared > limit) {
            reject(response, limit);
            return;
        }
        chain.doFilter(new LimitedRequest(request, limit, message(limit)), response);
    }

    private static String message(long limit) {
        return "The request body exceeds the " + Math.max(1, limit / (1024 * 1024)) + " MB limit.";
    }

    private void reject(HttpServletResponse response, long limit) throws IOException {
        ProblemDetail pd =
                ApiExceptionHandler.build(HttpStatus.PAYLOAD_TOO_LARGE, "too-large", "Too large", message(limit));
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), pd);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long max;
        private final String message;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long max, String message) {
            super(request);
            this.max = max;
            this.message = message;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                ServletInputStream delegate = super.getInputStream();
                stream = new ServletInputStream() {
                    private long count;

                    private void add(long n) {
                        count += n;
                        if (count > max) {
                            throw new PayloadTooLargeException(message);
                        }
                    }

                    @Override
                    public int read() throws IOException {
                        int b = delegate.read();
                        if (b >= 0) add(1);
                        return b;
                    }

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        int n = delegate.read(b, off, len);
                        if (n > 0) add(n);
                        return n;
                    }

                    @Override
                    public boolean isFinished() {
                        return delegate.isFinished();
                    }

                    @Override
                    public boolean isReady() {
                        return delegate.isReady();
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        delegate.setReadListener(listener);
                    }
                };
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
