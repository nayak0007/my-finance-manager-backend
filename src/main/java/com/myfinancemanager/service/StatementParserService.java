package com.myfinancemanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinancemanager.common.exception.BadRequestException;
import com.myfinancemanager.common.exception.ExternalServiceException;
import com.myfinancemanager.domain.ExtractionMethod;
import com.myfinancemanager.integration.openrouter.OpenRouterClient;
import com.myfinancemanager.integration.rapidapi.RapidApiClient;
import com.myfinancemanager.integration.rapidapi.StatementResponseMapper;
import com.myfinancemanager.integration.statement.ParsedTransaction;
import com.myfinancemanager.integration.statement.StatementTextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the two-stage statement parsing strategy described in the PRD:
 * <ol>
 *   <li>Primary: Rapid Bank Statement Parsing API.</li>
 *   <li>Fallback: raw text extraction followed by OpenRouter AI categorization.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementParserService {

    private static final int MAX_PROMPT_TEXT_LENGTH = 12000;

    private final RapidApiClient rapidApiClient;
    private final OpenRouterClient openRouterClient;
    private final StatementTextExtractor textExtractor;
    private final ObjectMapper objectMapper;

    public ParseResult parse(Path file, String originalFilename, String contentType) {
        if (rapidApiClient.isConfigured()) {
            try {
                JsonNode root = rapidApiClient.parseStatement(file, originalFilename, contentType);
                // The provider returns the rows as one text block ({"transactions": "..."});
                // a structured array is the secondary interpretation for other shapes.
                List<ParsedTransaction> transactions = StatementResponseMapper.mapTextResponse(root);
                if (transactions.isEmpty() && root != null) {
                    try {
                        transactions = mapArray(StatementResponseMapper.findTransactionArray(root));
                    } catch (IllegalArgumentException ignored) {
                        // No transaction array in the response either; fall through to OpenRouter.
                    }
                }
                if (!transactions.isEmpty()) {
                    return new ParseResult(ExtractionMethod.RAPID_API, transactions);
                }
                log.info("RapidAPI returned no transactions for {}; falling back to OpenRouter", originalFilename);
            } catch (ExternalServiceException ex) {
                log.info("RapidAPI parsing failed for {}; falling back to OpenRouter: {}",
                        originalFilename, ex.getMessage());
            }
        }

        if (!openRouterClient.isConfigured()) {
            throw new BadRequestException(
                    "No statement parsing provider is available. Configure RapidAPI or OpenRouter.");
        }

        String rawText = textExtractor.extract(file, contentType, originalFilename);
        if (rawText == null || rawText.isBlank()) {
            throw new BadRequestException("No readable content could be extracted from the statement");
        }
        List<ParsedTransaction> transactions = parseWithOpenRouter(rawText);
        if (transactions.isEmpty()) {
            throw new BadRequestException("No transactions could be identified in the statement");
        }
        return new ParseResult(ExtractionMethod.OPENROUTER_FALLBACK, transactions);
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

    private List<ParsedTransaction> parseWithOpenRouter(String rawText) {
        String truncated = rawText.length() > MAX_PROMPT_TEXT_LENGTH
                ? rawText.substring(0, MAX_PROMPT_TEXT_LENGTH)
                : rawText;
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

        String content = openRouterClient.completeJson(systemPrompt, userPrompt);
        String json = stripCodeFences(content);
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode array = StatementResponseMapper.findTransactionArray(root);
            return mapArray(array);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
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

    public record ParseResult(ExtractionMethod method, List<ParsedTransaction> transactions) {
    }
}
