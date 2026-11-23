package com.refinements.visualizer.shared;

import com.refinements.visualizer.shared.errors.BadRequestException;
import com.refinements.visualizer.shared.errors.ConflictException;
import com.refinements.visualizer.shared.errors.ConnectionsDocumentInvalidException;
import com.refinements.visualizer.shared.errors.FetchFailedException;
import com.refinements.visualizer.shared.errors.NotFoundException;
import com.refinements.visualizer.shared.errors.PayloadTooLargeException;
import com.refinements.visualizer.shared.errors.SpecInvalidException;
import com.refinements.visualizer.shared.errors.StaleVersionException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps domain exceptions to RFC 9457 Problem Details (contracts/openapi.yaml, T010). Bean Validation
 * failures are handled by {@link ResponseEntityExceptionHandler} and become 400.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static ResponseEntity<Object> problem(HttpStatus status, String type, String title, String detail) {
        return problem(build(status, type, title, detail));
    }

    static ProblemDetail build(HttpStatus status, String type, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("urn:problem:" + type));
        pd.setTitle(title);
        return pd;
    }

    static ResponseEntity<Object> problem(ProblemDetail pd) {
        return ResponseEntity.status(pd.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Object> notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<Object> badRequest(BadRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Invalid request", e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> constraint(ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Invalid request", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Object> conflict(ConflictException e) {
        ProblemDetail pd = build(HttpStatus.CONFLICT, "conflict", "Conflict", e.getMessage());
        if (e.getConflict() != null) {
            pd.setProperty("conflict", e.getConflict());
        }
        e.getProperties().forEach(pd::setProperty);
        return problem(pd);
    }

    @ExceptionHandler(ConnectionsDocumentInvalidException.class)
    ResponseEntity<Object> documentInvalid(ConnectionsDocumentInvalidException e) {
        ProblemDetail pd = build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "connections-document-invalid",
                "Invalid connections document",
                e.getMessage());
        pd.setProperty("errors", e.getErrors());
        return problem(pd);
    }

    @ExceptionHandler({StaleVersionException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Object> stale(RuntimeException e) {
        return problem(
                HttpStatus.CONFLICT,
                "stale-version",
                "Changed since it was loaded",
                e instanceof StaleVersionException
                        ? e.getMessage()
                        : "The content changed since it was loaded (for example in another tab).");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> integrity(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "conflict", "Conflict", "The change conflicts with existing data.");
    }

    @ExceptionHandler(SpecInvalidException.class)
    ResponseEntity<Object> specInvalid(SpecInvalidException e) {
        ProblemDetail pd = build(HttpStatus.UNPROCESSABLE_ENTITY, "spec-invalid", "Invalid spec", e.getMessage());
        pd.setProperty("errors", e.getErrors());
        return problem(pd);
    }

    @ExceptionHandler(FetchFailedException.class)
    ResponseEntity<Object> fetchFailed(FetchFailedException e) {
        return problem(HttpStatus.BAD_GATEWAY, "fetch-failed", "Could not fetch the spec", e.getMessage());
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    ResponseEntity<Object> tooLarge(PayloadTooLargeException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "too-large", "Too large", e.getMessage());
    }

    /** Bean Validation failures: 400 with every field error spelled out. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getAllErrors().stream()
                .map(err -> err instanceof FieldError fe
                        ? fe.getField() + ": " + fe.getDefaultMessage()
                        : err.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Invalid request", detail);
    }

    /** A body cut off by {@link RequestSizeLimitFilter} surfaces as an unreadable message. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof PayloadTooLargeException tooLarge) {
                return tooLarge(tooLarge);
            }
        }
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Malformed request body", rootMessage(ex));
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        if (msg == null) {
            return "The request body could not be read.";
        }
        int nl = msg.indexOf('\n');
        return nl > 0 ? msg.substring(0, nl) : msg;
    }
}
