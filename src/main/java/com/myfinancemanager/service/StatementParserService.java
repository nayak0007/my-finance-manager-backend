package com.myfinancemanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinancemanager.common.exception.BadRequestException;
import com.myfinancemanager.common.exception.ExternalServiceException;
import com.myfinancemanager.domain.ExtractionMethod;
import com.myfinancemanager.integration.openrouter.OpenRouterClient;
import com.myfinancemanager.integration.statement.ParsedTransaction;
import com.myfinancemanager.integration.statement.StatementResponseMapper;
import com.myfinancemanager.integration.statement.StatementTextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns an uploaded statement into transactions with OpenRouter as the only engine:
 * <ol>
 *   <li>extract the statement's text on this host (PDFBox / Apache POI / CSV), then</li>
 *   <li>ask the configured AI model for the rows as JSON.</li>
 * </ol>
 *
 * <p>There is no second parsing vendor. A failure here fails the batch, which is what the
 * review screen reports; the user's escape hatch is cancelling the import.
 *
 * <p>Every stage is logged under the {@code [Import]} prefix with the batch file name, so a
 * deployed instance's logs read as a timeline instead of a bare failure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementParserService {

    private static final int MAX_PROMPT_TEXT_LENGTH = 12000;

    private final OpenRouterClient openRouterClient;
    private final StatementTextExtractor textExtractor;
    private final ObjectMapper objectMapper;

    public ParseResult parse(Path file, String originalFilename, String contentType) {
        long start = System.currentTimeMillis();
        long fileSize = fileSizeOf(file);
        log.info("[Import] Parsing \"{}\" ({} bytes, content-type={})",
                originalFilename, fileSize, contentType);

        if (!openRouterClient.isConfigured()) {
            log.error("[Import] OpenRouter is not configured; \"{}\" cannot be parsed", originalFilename);
            throw new BadRequestException(
                    "Statement parsing is unavailable. Configure OPENROUTER_API_KEY on the server.");
        }

        // ---- Stage 1: raw text extraction -----------------------------------------------
        long extractStart = System.currentTimeMillis();
        String rawText = textExtractor.extract(file, contentType, originalFilename);
        if (rawText == null || rawText.isBlank()) {
            log.warn("[Import] Text extraction produced nothing for \"{}\" ({} ms)",
                    originalFilename, System.currentTimeMillis() - extractStart);
            throw new BadRequestException("No readable content could be extracted from the statement");
        }
        log.info("[Import] Extracted {} characters of text from \"{}\" in {} ms",
                rawText.length(), originalFilename, System.currentTimeMillis() - extractStart);

        // ---- Stage 2: AI extraction ------------------------------------------------------
        List<ParsedTransaction> transactions = parseWithOpenRouter(rawText, originalFilename);
        if (transactions.isEmpty()) {
            log.warn("[Import] OpenRouter found no transactions in \"{}\" (total {} ms)",
                    originalFilename, System.currentTimeMillis() - start);
            throw new BadRequestException("No transactions could be identified in the statement");
        }
        log.info("[Import] OpenRouter extracted {} transaction(s) for \"{}\"; parse finished in {} ms",
                transactions.size(), originalFilename, System.currentTimeMillis() - start);
        return new ParseResult(ExtractionMethod.OPENROUTER, transactions);
    }

    private List<ParsedTransaction> mapArray(JsonNode array) {
        List<ParsedTransaction> transactions = new ArrayList<>();
        for (JsonNode node : array) {
            try {
                transactions.add(StatementResponseMapper.map(node));
            } catch (RuntimeException ex) {
                log.debug("Skipping unmappable transaction node: {}", ex.getMessage());
            }
        }
        return transactions;
    }

    private List<ParsedTransaction> parseWithOpenRouter(String rawText, String originalFilename) {
        String truncated = rawText.length() > MAX_PROMPT_TEXT_LENGTH
                ? rawText.substring(0, MAX_PROMPT_TEXT_LENGTH)
                : rawText;
        if (rawText.length() > MAX_PROMPT_TEXT_LENGTH) {
            log.info("[Import] Statement text for \"{}\" truncated for the AI prompt: {} -> {} characters",
                    originalFilename, rawText.length(), MAX_PROMPT_TEXT_LENGTH);
        }
        String systemPrompt = """
                You extract transactions from bank and credit card statements.
                Respond ONLY with a JSON object of the form:
                {"transactions":[{"type":"INCOME|EXPENSE|INVESTMENT","date":"YYYY-MM-DD","description":"string",
                "merchant":"string","amount":number,"category":"string","paymentMode":"CASH|CARD|UPI|BANK_TRANSFER|NET_BANKING|WALLET|OTHER","confidence":0..1}]}
                Rules:
                - amount is always a positive number.
                - Use type EXPENSE for debits, INCOME for credits, INVESTMENT for investment/broker transactions.
                - Choose a short lowercase category such as food, travel, bills, shopping, salary, freelance,
                  interest, rental, mutual_fund, stock, other.
                - Do not invent transactions that are not present in the text.
                """;
        String userPrompt = "Extract all transactions from the following statement text:\n\n" + truncated;

        long aiStart = System.currentTimeMillis();
        log.info("[Import] OpenRouter extraction starting for \"{}\" (model={})",
                originalFilename, openRouterClient.model());
        String content;
        try {
            content = openRouterClient.completeJson(systemPrompt, userPrompt);
        } catch (ExternalServiceException ex) {
            log.warn("[Import] OpenRouter call failed for \"{}\" after {} ms: {}",
                    originalFilename, System.currentTimeMillis() - aiStart, ex.getMessage());
            throw ex;
        }
        log.info("[Import] OpenRouter responded for \"{}\" in {} ms ({} characters)",
                originalFilename, System.currentTimeMillis() - aiStart,
                content == null ? -1 : content.length());

        String json = stripCodeFences(content);
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode array = StatementResponseMapper.findTransactionArray(root);
            List<ParsedTransaction> parsed = mapArray(array);
            log.info("[Import] OpenRouter response mapped to {} transaction(s) for \"{}\"",
                    parsed.size(), originalFilename);
            return parsed;
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[Import] OpenRouter response for \"{}\" could not be interpreted: {} | raw response: {}",
                    originalFilename, ex.toString(), preview(json));
            throw new ExternalServiceException("Unable to parse AI statement extraction response", ex);
        }
    }

    private String stripCodeFences(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private static long fileSizeOf(Path file) {
        try {
            return file != null ? Files.size(file) : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    private static String preview(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String flat = value.replaceAll("\\s+", " ").trim();
        return flat.length() <= 300 ? flat : flat.substring(0, 300) + "…";
    }

    public record ParseResult(ExtractionMethod method, List<ParsedTransaction> transactions) {
    }
}
