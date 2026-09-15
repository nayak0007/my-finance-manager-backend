package com.myfinancemanager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        // Pinned so OS-level SPRING_DATASOURCE_* / DATABASE_* variables exported in a
        // developer shell or CI cannot leak into this test context (they outrank
        // application-test.yml in Spring's property precedence; inlined test
        // properties do not).
        "spring.datasource.url=jdbc:h2:mem:myfinance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinanceApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerThenTrackIncomeAndFetchDashboard() throws Exception {
        String email = "jane.doe+" + System.nanoTime() + "@example.com";
        String registerBody = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("email", email);
            put("password", "Password123");
            put("fullName", "Jane Doe");
        }});

        String registerResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn().getResponse().getContentAsString();

        JsonNode auth = objectMapper.readTree(registerResponse);
        String accessToken = auth.get("accessToken").asText();

        mockMvc.perform(post("/api/v1/incomes")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":2500.00,"source":"Salary","category":"salary","transactionDate":"2026-09-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty());

        mockMvc.perform(post("/api/v1/expenses")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":400.00,"merchant":"BigBasket","category":"food",
                                 "paymentMode":"UPI","transactionDate":"2026-09-02"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("period", "this_month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(2500.00))
                .andExpect(jsonPath("$.totalExpenses").value(400.00))
                .andExpect(jsonPath("$.netSavings").value(2100.00));

        mockMvc.perform(get("/api/v1/dashboard/expenses-by-category")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("period", "this_month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("food"))
                .andExpect(jsonPath("$[0].amount").value(400.00));
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/expenses"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/insights"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleLoginReturnsServiceUnavailableWhenNotConfigured() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"dummy\"}"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        JsonNode error = objectMapper.readTree(response);
        assertThat(error.get("message").asText()).contains("Google Sign-In");
    }
}
