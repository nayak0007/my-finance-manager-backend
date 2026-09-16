package com.myfinancemanager.integration.rapidapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myfinancemanager.common.exception.ExternalServiceException;
import com.myfinancemanager.config.RapidApiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Client for the Rapid Bank Statement Parsing API (RapidAPI), the primary extraction
 * engine for uploaded statements.
 *
 * <p>Verified request contract of the {@code /processDocument} endpoint (a multipart
 * body is rejected with "No body provided"): a JSON object carrying the statement as
 * base64 plus an {@code extractionDetails} block that names the fields to extract,
 * for example:
 * <pre>{@code
 * {
 *   "file": "<base64>",
 *   "extractionDetails": {
 *     "name": "transactions",
 *     "language": "English",
 *     "fields": [{ "key": "transactions", "description": "..." }]
 *   }
 * }}</pre>
 *
 * <p>The response is {@code {"transactions": "<text blob>"}} — the extracted rows as one
 * text block, not a JSON array — so {@link StatementResponseMapper#mapTextResponse} does
 * the interpretation. The provider accepts PDF documents; CSV/XLSX uploads fail here and
 * fall through to the OpenRouter extraction path in {@code StatementParserService}.
 */
@Slf4j
@Component
public class RapidApiClient {

    private final RapidApiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RapidApiClient(RapidApiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.timeout().toMillis());
        requestFactory.setReadTimeout((int) properties.timeout().toMillis());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public JsonNode parseStatement(Path file, String originalFilename, String contentType) {
        if (!properties.isConfigured()) {
            throw new ExternalServiceException("Primary statement parsing API is not configured");
        }
        try {
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(file));
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("file", base64);
            ObjectNode details = payload.putObject("extractionDetails");
            details.put("name", "transactions");
            details.put("language", "English");
            ObjectNode field = details.putArray("fields").addObject();
            field.put("key", "transactions");
            field.put("description",
                    "all bank transactions with date, description, and debit or credit amount");

            RestClient.RequestBodySpec request = restClient.post()
                    .uri(properties.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-RapidAPI-Key", properties.apiKey());
            if (properties.host() != null && !properties.host().isBlank()) {
                request = request.header("X-RapidAPI-Host", properties.host());
            }
            String rawResponse = request
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new ExternalServiceException("Primary statement parsing API returned an empty response");
            }
            return objectMapper.readTree(rawResponse);
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("RapidAPI statement parsing failed: {}", ex.getMessage());
            throw new ExternalServiceException("Primary statement parsing API call failed", ex);
        }
    }
}
