package com.myfinancemanager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinancemanager.domain.User;
import com.myfinancemanager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

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

    @Autowired
    private UserRepository userRepository;

    /**
     * Points the production decoder at a local JWKS endpoint serving a generated Ed25519 key.
     * The issuer and JWKS URL are derived from this URL, so the derivation is covered too.
     */
    @DynamicPropertySource
    static void neonAuthProperties(DynamicPropertyRegistry registry) {
        registry.add("app.auth.neon.base-url", NeonAuthTestSupport::baseUrl);
    }

    @Test
    void validTokenProvisionsAUserThenTracksIncomeAndBuildsTheDashboard() throws Exception {
        String email = uniqueEmail("jane.doe");
        String token = NeonAuthTestSupport.token(UUID.randomUUID().toString(), email);

        // The first authenticated request has to create the local profile row on the fly.
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.currency").value("INR"));

        mockMvc.perform(post("/api/v1/incomes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":2500.00,"source":"Salary","category":"salary","transactionDate":"2026-09-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty());

        mockMvc.perform(post("/api/v1/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":400.00,"merchant":"BigBasket","category":"food",
                                 "paymentMode":"UPI","transactionDate":"2026-09-02"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + token)
                        .param("period", "this_month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(2500.00))
                .andExpect(jsonPath("$.totalExpenses").value(400.00))
                .andExpect(jsonPath("$.netSavings").value(2100.00));

        mockMvc.perform(get("/api/v1/dashboard/expenses-by-category")
                        .header("Authorization", "Bearer " + token)
                        .param("period", "this_month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("food"))
                .andExpect(jsonPath("$[0].amount").value(400.00));
    }

    @Test
    void repeatedRequestsProvisionTheIdentityOnlyOnce() throws Exception {
        String subject = UUID.randomUUID().toString();
        String email = uniqueEmail("repeat");
        String token = NeonAuthTestSupport.token(subject, email);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        // Three requests must not leave three profiles behind.
        assertThat(userRepository.findByAuthSubject(subject)).isPresent();
        assertThat(userRepository.findAll().stream()
                .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                .count()).isEqualTo(1);
    }

    @Test
    void anAccountCreatedBeforeTheMigrationIsLinkedRatherThanDuplicated() throws Exception {
        String email = uniqueEmail("legacy");
        User legacy = new User();
        legacy.setEmail(email);
        legacy.setFullName("Legacy Owner");
        legacy = userRepository.saveAndFlush(legacy);

        String subject = UUID.randomUUID().toString();
        String token = NeonAuthTestSupport.token(subject, email);

        String body = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Same row, now carrying the Neon Auth subject: the profile id must not change, because
        // every record already references it.
        assertThat(objectMapper.readTree(body).get("id").asText()).isEqualTo(legacy.getId().toString());
        // A name the account owner set in the app outranks the one from the token.
        assertThat(objectMapper.readTree(body).get("fullName").asText()).isEqualTo("Legacy Owner");
        assertThat(userRepository.findByAuthSubject(subject))
                .get()
                .extracting(User::getId)
                .isEqualTo(legacy.getId());
    }

    @Test
    void budgetUpsertIsIdempotentAndScopedToTheUser() throws Exception {
        String token = tokenForNewUser("budget.owner");

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
        String otherToken = tokenForNewUser("budget.other");
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
        String token = tokenForNewUser("budget.invalid");

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
    protected void protectedEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/expenses")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/insights")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/budgets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokensThatFailVerificationAreRejected() throws Exception {
        String subject = UUID.randomUUID().toString();
        String email = uniqueEmail("rejected");

        assertRejected("not-a-jwt-at-all");
        assertRejected(NeonAuthTestSupport.tokenWithWrongAudience(subject, email));
        assertRejected(NeonAuthTestSupport.tokenSignedByUnknownKey(subject, email));
        assertRejected(NeonAuthTestSupport.expiredToken(subject, email));

        // None of those attempts may have created a profile.
        assertThat(userRepository.findByAuthSubject(subject)).isEmpty();
    }

    @Test
    void deletingTheAccountRemovesTheProfile() throws Exception {
        String subject = UUID.randomUUID().toString();
        String email = uniqueEmail("deleteme");
        String token = NeonAuthTestSupport.token(subject, email);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(userRepository.findByAuthSubject(subject)).isPresent();

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByAuthSubject(subject)).isEmpty();
    }

    private void assertRejected(String token) throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    /** Mints a token for a brand new identity and returns it. */
    private String tokenForNewUser(String prefix) {
        return NeonAuthTestSupport.token(UUID.randomUUID().toString(), uniqueEmail(prefix));
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "+" + System.nanoTime() + "@example.com";
    }
}
