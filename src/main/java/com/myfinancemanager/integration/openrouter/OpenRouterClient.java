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

        log.info("[OpenRouter] POST {}/chat/completions model={} response_format={} system_chars={} user_chars={}",
                properties.baseUrl(), properties.model(), jsonMode ? "json_object" : "none",
                systemPrompt.length(), userPrompt.length());
        if (log.isDebugEnabled()) {
            log.debug("[OpenRouter] Request body for model {}: {}", properties.model(), writeJson(body));
        }

        long requestStart = System.currentTimeMillis();
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
            Extracted extracted = extractContent(response);
            log.info("[OpenRouter] 200 from {} model={} in {} ms: content_chars={} tokens={}",
                    properties.baseUrl(), extracted.model(), System.currentTimeMillis() - requestStart,
                    extracted.content().length(), extracted.usage());
            if (log.isDebugEnabled()) {
                log.debug("[OpenRouter] Raw response from model {}: {}", properties.model(), response);
            }
            return extracted.content();
        } catch (RestClientResponseException ex) {
            log.warn("[OpenRouter] HTTP {} from {} with model {} after {} ms: {}",
                    ex.getStatusCode().value(), properties.baseUrl(), properties.model(),
                    System.currentTimeMillis() - requestStart, ex.getResponseBodyAsString());
            throw new ExternalServiceException(
                    "AI service returned HTTP " + ex.getStatusCode().value(), ex);
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[OpenRouter] Request to {} failed after {} ms: {}",
                    properties.baseUrl(), System.currentTimeMillis() - requestStart, ex.toString());
            throw new ExternalServiceException("AI service is temporarily unavailable", ex);
        }
    }

    private Extracted extractContent(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new ExternalServiceException("AI service returned an unexpected response");
            }
            String content = choices.get(0).path("message").path("content").asText("");
            return new Extracted(content, root.path("model").asText(properties.model()), describeUsage(root));
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalServiceException("Unable to parse AI service response", ex);
        }
    }

    /**
     * The token counts the provider charges for, as {@code prompt/completion/total}. They are
     * only carried in the response, so logging them here is the only way to see what an import
     * actually cost.
     */
    private String describeUsage(JsonNode root) {
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode() || usage.isNull()) {
            return "n/a";
        }
        return String.format("prompt=%s completion=%s total=%s",
                usage.path("prompt_tokens").asText("?"),
                usage.path("completion_tokens").asText("?"),
                usage.path("total_tokens").asText("?"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "<unserializable: " + ex + ">";
        }
    }

    private record Extracted(String content, String model, String usage) {
    }
}
