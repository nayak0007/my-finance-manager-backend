package com.myfinancemanager.integration.statement;

import com.fasterxml.jackson.databind.JsonNode;
import com.myfinancemanager.domain.PaymentMode;
import com.myfinancemanager.domain.StagedTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Maps the statement-parsing response of the AI provider into the internal
 * {@link ParsedTransaction} representation.
 *
 * <p>OpenRouter is asked for {@code {"transactions": [...]}} but its answer is not guaranteed to
 * sit at the top level, so {@link #findTransactionArray} hunts for the array through the usual
 * wrapper keys, and each element is read through a list of aliases ({@code narration} vs
 * {@code description}, {@code credit}/{@code debit} vs {@code amount}, and so on).
 */
public final class StatementResponseMapper {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT),
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH));

    private static final List<String> AMOUNT_KEYS =
            List.of("amount", "transactionAmount", "txnAmount", "value", "amt");
    private static final List<String> CREDIT_KEYS = List.of("credit", "deposit", "creditAmount");
    private static final List<String> DEBIT_KEYS =
            List.of("debit", "withdrawal", "debitAmount", "withdrawalAmount");
    private static final List<String> DATE_KEYS =
            List.of("date", "transactionDate", "txnDate", "valueDate", "postedDate");
    private static final List<String> DESCRIPTION_KEYS =
            List.of("description", "narration", "particulars", "remark", "details", "transactionDescription");
    private static final List<String> MERCHANT_KEYS = List.of("merchant", "payee", "name", "merchantName");
    private static final List<String> TYPE_KEYS = List.of("type", "transactionType", "txnType", "drCr", "direction");
    private static final List<String> CATEGORY_KEYS = List.of("category", "categoryName");
    private static final List<String> MODE_KEYS = List.of("paymentMode", "mode", "channel", "paymentMethod");

    private StatementResponseMapper() {
    }

    /**
     * Finds the transaction array in a parser response, unwrapping the wrapper objects the AI
     * tends to add ({@code {"data": {...}}}, {@code {"result": [...]}}, …).
     *
     * @throws IllegalArgumentException when no array of transaction-looking objects exists
     */
    public static JsonNode findTransactionArray(JsonNode root) {
        if (root == null || root.isNull()) {
            throw new IllegalArgumentException("Empty statement parsing response");
        }
        if (root.isArray() && looksLikeTransactionArray(root)) {
            return root;
        }
        for (String key : List.of("transactions", "data", "result", "records", "items", "statement")) {
            JsonNode candidate = root.get(key);
            if (candidate == null) {
                continue;
            }
            if (candidate.isArray() && looksLikeTransactionArray(candidate)) {
                return candidate;
            }
            if (candidate.isObject()) {
                JsonNode nested = findTransactionArray(candidate);
                if (nested != null) {
                    return nested;
                }
            }
        }
        for (JsonNode child : root) {
            if (child.isArray() && looksLikeTransactionArray(child)) {
                return child;
            }
        }
        throw new IllegalArgumentException("No transaction array found in statement parsing response");
    }

    private static boolean looksLikeTransactionArray(JsonNode array) {
        if (!array.isArray() || array.isEmpty()) {
            return false;
        }
        JsonNode first = array.get(0);
        if (!first.isObject()) {
            return false;
        }
        return first.has("amount") || first.has("transactionAmount") || first.has("debit")
                || first.has("credit") || first.has("description") || first.has("narration");
    }

    public static ParsedTransaction map(JsonNode node) {
        String description = text(node, DESCRIPTION_KEYS);
        String merchant = text(node, MERCHANT_KEYS);
        String typeText = text(node, TYPE_KEYS);

        BigDecimal credit = amount(node, CREDIT_KEYS);
        BigDecimal debit = amount(node, DEBIT_KEYS);
        BigDecimal amount = amount(node, AMOUNT_KEYS);
        if (amount == null) {
            amount = credit != null ? credit : debit;
        }

        StagedTransactionType type = resolveType(typeText, credit, debit, amount);
        LocalDate date = date(node);

        return new ParsedTransaction(
                type,
                date,
                description != null ? description : merchant,
                merchant != null ? merchant : description,
                amount,
                text(node, CATEGORY_KEYS),
                paymentMode(text(node, MODE_KEYS)),
                null,
                node.toString(),
                node.has("confidence") ? node.get("confidence").asDouble() : null);
    }

    private static StagedTransactionType resolveType(String typeText, BigDecimal credit,
                                                     BigDecimal debit, BigDecimal amount) {
        String normalized = typeText == null ? "" : typeText.toLowerCase(Locale.ROOT);
        if (normalized.contains("invest")) {
            return StagedTransactionType.INVESTMENT;
        }
        if (normalized.contains("credit") || normalized.contains("deposit")
                || normalized.contains("income") || normalized.contains("salary")
                || normalized.contains("refund") || normalized.equals("cr")) {
            return StagedTransactionType.INCOME;
        }
        if (normalized.contains("debit") || normalized.contains("withdrawal")
                || normalized.contains("expense") || normalized.contains("purchase")
                || normalized.contains("payment") || normalized.equals("dr")) {
            return StagedTransactionType.EXPENSE;
        }
        if (credit != null && credit.signum() > 0 && (debit == null || debit.signum() == 0)) {
            return StagedTransactionType.INCOME;
        }
        if (debit != null && debit.signum() > 0) {
            return StagedTransactionType.EXPENSE;
        }
        if (amount != null && amount.signum() > 0) {
            return StagedTransactionType.INCOME;
        }
        return StagedTransactionType.EXPENSE;
    }

    private static PaymentMode paymentMode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("upi")) {
            return PaymentMode.UPI;
        }
        if (normalized.contains("cash")) {
            return PaymentMode.CASH;
        }
        if (normalized.contains("card") || normalized.contains("pos") || normalized.contains("atm")) {
            return PaymentMode.CARD;
        }
        if (normalized.contains("neft") || normalized.contains("imps") || normalized.contains("rtgs")
                || normalized.contains("transfer")) {
            return PaymentMode.BANK_TRANSFER;
        }
        if (normalized.contains("netbank") || normalized.contains("net banking") || normalized.contains("netbanking")) {
            return PaymentMode.NET_BANKING;
        }
        if (normalized.contains("wallet")) {
            return PaymentMode.WALLET;
        }
        return PaymentMode.OTHER;
    }

    private static String text(JsonNode node, List<String> keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private static BigDecimal amount(JsonNode node, List<String> keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            BigDecimal parsed = parseAmount(value.asText());
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    static BigDecimal parseAmount(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isBlank() || cleaned.equals("-") || cleaned.equals(".")) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDate date(JsonNode node) {
        String raw = text(node, DATE_KEYS);
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (Exception ignored) {
                // try the next formatter
            }
        }
        return null;
    }
}
