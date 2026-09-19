package com.myfinancemanager.integration.openrouter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinancemanager.common.exception.ExternalServiceException;
import com.myfinancemanager.common.exception.ServiceUnavailableException;
import com.myfinancemanager.config.OpenRouterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Thin client for the OpenRouter unified LLM API. The underlying model is read from
 * configuration ({@code OPENROUTER_MODEL}) and never hardcoded, so it can be swapped
 * per environment without a code deployment.
 */
@Slf4j
@Component
public class OpenRouterClient {

    private final OpenRouterProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenRouterClient(OpenRouterProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.timeout().toMillis());
        requestFactory.setReadTimeout((int) properties.timeout().toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public String model() {
        return properties.model();
    }

    public String complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt, false);
    }

    public String completeJson(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt, true);
    }

    private String complete(String systemPrompt, String userPrompt, boolean jsonMode) {
        if (!properties.isConfigured()) {
            throw new ServiceUnavailableException("AI features are not configured on this server");
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        try {
            String response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("HTTP-Referer", "https://myfinancemanager.app")
                    .header("X-Title", "My Finance Manager")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return extractContent(response);
        } catch (RestClientResponseException ex) {
            log.warn("[OpenRouter] HTTP {} from {} with model {}: {}",
                    ex.getStatusCode().value(), properties.baseUrl(), properties.model(),
                    ex.getResponseBodyAsString());
            throw new ExternalServiceException(
                    "AI service returned HTTP " + ex.getStatusCode().value(), ex);
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[OpenRouter] Request to {} failed: {}", properties.baseUrl(), ex.toString());
            throw new ExternalServiceException("AI service is temporarily unavailable", ex);
        }
    }

    private String extractContent(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new ExternalServiceException("AI service returned an unexpected response");
            }
            return choices.get(0).path("message").path("content").asText("");
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalServiceException("Unable to parse AI service response", ex);
        }
    }
}
