package com.myfinancemanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myfinancemanager.repository.ImportBatchRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the smart-import happy path end to end against a stand-in for the OpenRouter API.
 *
 * <p>The existing suite deliberately avoids the parse ("no OpenRouter key in the test profile"),
 * which left the persist stage of {@code ImportProcessingService} unexercised — the stage that
 * the transient-entity bug lived in. This serves a canned chat completion over HTTP so the real
 * {@code OpenRouterClient}, the real async parse and the real staging all run, and asserts the
 * rows actually land in the database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:myfinance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmartImportIntegrationTest {

    private static HttpServer openRouter;
    private static String openRouterContent;
    private static final AtomicInteger requestsServed = new AtomicInteger();

    @BeforeAll
    static void startOpenRouterStandIn() throws Exception {
        // The assistant content is itself a JSON string, exactly as the real API delivers it.
        String transactions = """
                {"transactions":[
                  {"type":"EXPENSE","date":"2026-09-01","description":"COFFEE DAY","merchant":"Cafe Coffee Day",
                   "amount":120.00,"category":"food","paymentMode":"UPI","confidence":0.92},
                  {"type":"INCOME","date":"2026-09-01","description":"SALARY SEP","merchant":"Acme Corp",
                   "amount":50000.00,"category":"salary","paymentMode":"NEFT","confidence":0.97},
                  {"type":"EXPENSE","date":"2026-09-03","description":"AMAZON","merchant":"Amazon",
                   "amount":1499.50,"category":"shopping","paymentMode":"CARD","confidence":0.88}
                ]}""".replaceAll("\\s+", " ");

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("id", "chatcmpl-test");
        root.put("model", "test/model");
        ArrayNode choices = root.putArray("choices");
        ObjectNode message = choices.addObject().put("index", 0).putObject("message");
        message.put("role", "assistant");
        message.put("content", transactions);
        ObjectNode usage = root.putObject("usage");
        usage.put("prompt_tokens", 512);
        usage.put("completion_tokens", 96);
        usage.put("total_tokens", 608);
        openRouterContent = mapper.writeValueAsString(root);

        openRouter = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        openRouter.createContext("/api/v1/chat/completions", SmartImportIntegrationTest::handleCompletion);
        openRouter.start();
    }

    private static void handleCompletion(HttpExchange exchange) throws java.io.IOException {
        requestsServed.incrementAndGet();
        byte[] body = openRouterContent.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @AfterAll
    static void stopOpenRouterStandIn() {
        if (openRouter != null) {
            openRouter.stop(0);
        }
    }

    @DynamicPropertySource
    static void openRouterProperties(DynamicPropertyRegistry registry) {
        // Same stand-in as FinanceApiIntegrationTest: a local JWKS endpoint so the real
        // Neon Auth decoder verifies the minted token.
        registry.add("app.auth.neon.base-url", NeonAuthTestSupport::baseUrl);
        registry.add("app.openrouter.api-key", () -> "test-key");
        registry.add("app.openrouter.base-url",
                () -> "http://127.0.0.1:" + openRouter.getAddress().getPort() + "/api/v1");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Test
    void uploadingAStatementStagesTheParsedTransactionsForReview() throws Exception {
        String token = NeonAuthTestSupport.token(UUID.randomUUID().toString(), uniqueEmail("import.flow"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String csv = """
                date,description,amount
                2026-09-01,COFFEE DAY,120.00
                2026-09-01,SALARY SEP,50000.00
                2026-09-03,AMAZON,1499.50
                """;

        MvcResult created = mockMvc.perform(multipart("/api/v1/imports")
                        .file(new MockMultipartFile("file", "statement.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn();
        UUID batchId = UUID.fromString(objectMapper
                .readTree(created.getResponse().getContentAsString()).get("id").asText());

        // The parse runs on the import executor; wait for it to settle before asserting.
        String status = awaitBatchStatus(batchId, token, Duration.ofSeconds(20));
        assertThat(status)
                .as("batch status after the async parse")
                .isEqualTo("READY_FOR_REVIEW");

        MvcResult detail = mockMvc.perform(get("/api/v1/imports/" + batchId + "/detail")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(detail.getResponse().getContentAsString())
                .get("transactions")).hasSize(3);

        // The stand-in must actually have been called, otherwise the rows would be missing
        // for the wrong reason.
        assertThat(requestsServed.get()).isGreaterThan(0);

        // The staged rows are owned by the batch, so they must survive as real rows.
        assertThat(importBatchRepository.findById(batchId))
                .hasValueSatisfying(batch -> assertThat(batch.getTotalTransactions()).isEqualTo(3));
    }

    private String awaitBatchStatus(UUID batchId, String token, Duration timeout)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/imports/" + batchId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            String status = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("status").asText();
            if ("READY_FOR_REVIEW".equals(status) || "FAILED".equals(status)
                    || "CANCELLED".equals(status) || "COMMITTED".equals(status)) {
                return status;
            }
            Thread.sleep(150);
        }
        throw new IllegalStateException("Import batch " + batchId + " never settled");
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
