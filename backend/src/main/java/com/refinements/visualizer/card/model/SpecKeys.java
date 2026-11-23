package com.refinements.visualizer.card.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Endpoint keys and content hashes (research R1). The service and edge keys of 002 are gone: cards are
 * identified by their UUID, which prefixes every endpoint key.
 */
public final class SpecKeys {

    private static final Pattern PATH_PARAM = Pattern.compile("\\{[^}/]*}");

    private SpecKeys() {}

    /** {@code {cardId}:{METHOD} {normalizedPath}}, with path templates normalized to {@code {}}. */
    public static String endpointKey(String cardId, String method, String path) {
        return cardId + ":" + method.toUpperCase(Locale.ROOT) + " " + normalizePath(path);
    }

    public static String normalizePath(String path) {
        String p = path == null || path.isBlank() ? "/" : path.trim();
        return PATH_PARAM.matcher(p).replaceAll("{}");
    }

    /** SHA-256 of spec content with line endings normalized and surrounding whitespace trimmed. */
    public static String contentHash(String content) {
        return sha256Hex(content.replace("\r\n", "\n").strip());
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
