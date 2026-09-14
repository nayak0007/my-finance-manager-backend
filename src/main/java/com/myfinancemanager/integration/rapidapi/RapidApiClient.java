package com.myfinancemanager.integration.rapidapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinancemanager.common.exception.ExternalServiceException;
import com.myfinancemanager.config.RapidApiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;

/**
 * Client for the Rapid Bank Statement Parsing API (RapidAPI), the primary extraction
 * engine for uploaded statements. The response shape varies between providers, so the
 * transaction array is located defensively by {@link StatementResponseMapper}.
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
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add(properties.fileFieldName(), new FileSystemResource(file));

            String rawResponse = restClient.post()
                    .uri(properties.url())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("X-RapidAPI-Key", properties.apiKey())
                    .header("X-RapidAPI-Host", properties.host())
                    .body(parts)
                    .retrieve()
                    .body(String.class);

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new ExternalServiceException("Primary statement parsing API returned an empty response");
            }
            return StatementResponseMapper.findTransactionArray(objectMapper.readTree(rawResponse));
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("RapidAPI statement parsing failed: {}", ex.getMessage());
            throw new ExternalServiceException("Primary statement parsing API call failed", ex);
        }
    }
}
