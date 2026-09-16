package com.myfinancemanager.integration.rapidapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.myfinancemanager.domain.PaymentMode;
import com.myfinancemanager.domain.StagedTransactionType;
import com.myfinancemanager.integration.statement.ParsedTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps heterogeneous statement-parsing API responses (RapidAPI or OpenRouter fallback)
 * into the internal {@link ParsedTransaction} representation.
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

    // ---- Text-blob responses -------------------------------------------------------------

    /**
     * Date-like token that starts a transaction row in the provider's text response:
     * "01/09/2026 SALARY CREDIT SEPT 85000.00 CR 02/09/2026 UBER ... 320.50 DR". The formats
     * mirror {@link #DATE_FORMATTERS} so every token found here can also be parsed back.
     */
    private static final Pattern DATE_TOKEN = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}"                      // 2026-09-01
                + "|\\d{4}/\\d{2}/\\d{2}"                // 2026/09/01
                + "|\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}"     // 01/09/2026, 1-9-26
                + "|\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4}" // 01 Sep 2026
                + "|[A-Za-z]{3,9}\\s+\\d{1,2},?\\s+\\d{2,4}"); // Sep 1, 2026

    /** A bare number, optionally grouped and with decimals — candidate amount inside a row. */
    private static final Pattern MONEY_TOKEN =
            Pattern.compile("\\d+(?:,\\d{2,3})*(?:\\.\\d+)?");

    /**
     * Interprets the provider's verified response shape, where the extracted rows come back
     * as one text block keyed by the requested field — {@code {"transactions": "01/09/2026
     * SALARY ... 85000.00 CR 02/09/2026 ..."}} — rather than as a JSON array.
     *
     * <p>Rows are split on date tokens: a transaction starts at each date and runs to the
     * next one. Within a row the amount is the last standalone number (after the date, and
     * typically the balance columns real statements carry come before the closing CR/DR flag
     * in text dumps — the flag is what decides the direction), and the direction comes from
     * the CR/DR suffix: CR means money in (INCOME), DR or nothing means money out (EXPENSE).
     *
     * @return the parsed rows; empty when the response holds no text blob to interpret
     */
    public static List<ParsedTransaction> mapTextResponse(JsonNode root) {
        String blob = extractTextBlob(root);
        List<ParsedTransaction> transactions = new ArrayList<>();
        if (blob == null || blob.isBlank()) {
            return transactions;
        }

        Matcher dates = DATE_TOKEN.matcher(blob);
        List<int[]> spans = new ArrayList<>();
        while (dates.find()) {
            spans.add(new int[]{dates.start(), dates.end()});
        }
        for (int i = 0; i < spans.size(); i++) {
            String dateText = blob.substring(spans.get(i)[0], spans.get(i)[1]);
            int bodyEnd = i + 1 < spans.size() ? spans.get(i + 1)[0] : blob.length();
            ParsedTransaction parsed = mapTextRecord(dateText, blob.substring(spans.get(i)[1], bodyEnd));
            if (parsed != null) {
                transactions.add(parsed);
            }
        }
        return transactions;
    }

    /**
     * Locates the extracted text block in a response. The documented shape is
     * {@code {"transactions": "..."}}; provider wrappers ({@code data}/{@code result}/…)
     * are unwrapped one level defensively.
     */
    private static String extractTextBlob(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode direct = root.get("transactions");
        if (direct != null && direct.isTextual()) {
            return direct.asText();
        }
        for (String key : List.of("data", "result", "output", "response")) {
            JsonNode nested = root.get(key);
            if (nested != null && nested.isObject()) {
                String found = extractTextBlob(nested);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Maps one text row: a date token plus everything up to the next date. */
    private static ParsedTransaction mapTextRecord(String dateText, String body) {
        LocalDate date = parseDateText(dateText);
        BigDecimal amount = lastAmount(body);
        if (date == null || amount == null || amount.signum() <= 0) {
            return null;
        }
        String upper = body.toUpperCase(Locale.ROOT);
        boolean credit = upper.matches(".*\\bCR\\b.*");
        StagedTransactionType type = credit ? StagedTransactionType.INCOME : StagedTransactionType.EXPENSE;

        // " SALARY CREDIT SEPT 85000.00 CR " -> "SALARY CREDIT SEPT": drop the trailing
        // direction flag, then the trailing amount, so the description reads as the narrative.
        String description = body;
        description = description.replaceFirst("(?i)\\b(CR|DR|CREDIT|DEBIT)\\b\\s*$", " ");
        description = description.replaceFirst("[\\d.,]+\\s*$", " ").trim();
        if (description.isBlank()) {
            description = "Imported transaction";
        }
        return new ParsedTransaction(
                type,
                date,
                description,
                description,
                amount,
                null,
                null,
                null,
                (dateText + " " + body).trim(),
                null);
    }

    /** The last standalone number in the row: the statement amount, not the leading date. */
    private static BigDecimal lastAmount(String body) {
        Matcher amounts = MONEY_TOKEN.matcher(body);
        BigDecimal last = null;
        while (amounts.find()) {
            BigDecimal candidate = parseAmount(amounts.group());
            if (candidate != null && candidate.signum() > 0) {
                last = candidate;
            }
        }
        return last;
    }

    /** Parses a date token using the same formatters the structured mapper accepts. */
    private static LocalDate parseDateText(String raw) {
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

        ParsedTransaction parsed = new ParsedTransaction(
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
        return parsed;
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
