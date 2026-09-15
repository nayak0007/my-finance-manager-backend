package com.myfinancemanager.service.util;

import com.myfinancemanager.domain.StagedTransactionType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * Builds a stable fingerprint for a transaction so that duplicates originating from
 * different sources (manual, SMS, email, statement import) can be detected.
 */
public final class TransactionFingerprint {

    private static final String SEPARATOR = "|";
    private static final int MAX_PART_LENGTH = 120;

    private TransactionFingerprint() {
    }

    public static String of(StagedTransactionType type, BigDecimal amount, LocalDate date, String descriptor) {
        String normalized = String.join(SEPARATOR,
                type != null ? type.name() : "",
                normalizeAmount(amount),
                date != null ? date.toString() : "",
                normalizeText(descriptor));
        return sha256Hex(normalized);
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() > MAX_PART_LENGTH) {
            cleaned = cleaned.substring(0, MAX_PART_LENGTH);
        }
        return cleaned;
    }

    public static String normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
