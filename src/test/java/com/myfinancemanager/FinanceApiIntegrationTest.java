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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void budgetUpsertIsIdempotentAndScopedToTheUser() throws Exception {
        String token = register("budget.owner+" + System.nanoTime() + "@example.com");

        mockMvc.perform(put("/api/v1/budgets/food")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyLimit\":8000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.monthlyLimit").value(8000));

        // Same category again: the limit moves, it does not add a second budget.
        mockMvc.perform(put("/api/v1/budgets/FOOD")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyLimit\":9500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyLimit").value(9500));

        mockMvc.perform(get("/api/v1/budgets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].category").value("FOOD"))
                .andExpect(jsonPath("$[0].monthlyLimit").value(9500));

        // A second account must not see the first account's budget.
        String otherToken = register("budget.other+" + System.nanoTime() + "@example.com");
        mockMvc.perform(get("/api/v1/budgets").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(delete("/api/v1/budgets/FOOD").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Replaying a delete for a category with no budget is a no-op, not a 404, so a client that
        // retries a change it already applied does not get stuck.
        mockMvc.perform(delete("/api/v1/budgets/FOOD").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/budgets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void budgetWithoutAPositiveLimitIsRejected() throws Exception {
        String token = register("budget.invalid+" + System.nanoTime() + "@example.com");

        mockMvc.perform(put("/api/v1/budgets/travel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyLimit\":0}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/budgets/travel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/expenses"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/insights"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/budgets"))
                .andExpect(status().isUnauthorized());
    }

    /** Registers a fresh account and returns its access token. */
    private String register(String email) throws Exception {
        String registerBody = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("email", email);
            put("password", "Password123");
            put("fullName", "Budget Tester");
        }});

        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
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
