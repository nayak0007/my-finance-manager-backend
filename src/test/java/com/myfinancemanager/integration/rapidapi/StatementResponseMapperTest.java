package com.myfinancemanager.integration.rapidapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinancemanager.domain.PaymentMode;
import com.myfinancemanager.domain.StagedTransactionType;
import com.myfinancemanager.integration.statement.ParsedTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatementResponseMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void locatesNestedTransactionArray() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {"status":"success","data":{"transactions":[
                  {"date":"2026-01-10","description":"Salary","credit":"50000.00"},
                  {"date":"2026-01-11","description":"BigBasket","debit":"1200.50","mode":"UPI"}
                ]}}
                """);

        JsonNode array = StatementResponseMapper.findTransactionArray(root);

        assertThat(array).hasSize(2);
    }

    @Test
    void mapsCreditToIncome() throws Exception {
        JsonNode node = objectMapper.readTree(
                "{\"date\":\"2026-01-10\",\"narration\":\"Salary Credit\",\"credit\":\"50000.00\"}");

        ParsedTransaction parsed = StatementResponseMapper.map(node);

        assertThat(parsed.type()).isEqualTo(StagedTransactionType.INCOME);
        assertThat(parsed.amount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(parsed.transactionDate()).isEqualTo(LocalDate.of(2026, 1, 10));
    }

    @Test
    void mapsDebitWithPaymentModeToExpense() throws Exception {
        JsonNode node = objectMapper.readTree(
                "{\"date\":\"11/01/2026\",\"description\":\"BigBasket\",\"debit\":\"1,200.50\",\"mode\":\"UPI\"}");

        ParsedTransaction parsed = StatementResponseMapper.map(node);

        assertThat(parsed.type()).isEqualTo(StagedTransactionType.EXPENSE);
        assertThat(parsed.amount()).isEqualByComparingTo(new BigDecimal("1200.50"));
        assertThat(parsed.paymentMode()).isEqualTo(PaymentMode.UPI);
        assertThat(parsed.transactionDate()).isEqualTo(LocalDate.of(2026, 1, 11));
    }

    @Test
    void mapsInvestmentKeyword() throws Exception {
        JsonNode node = objectMapper.readTree(
                "{\"date\":\"2026-02-01\",\"description\":\"Mutual Fund SIP\",\"type\":\"investment\",\"amount\":\"5000\"}");

        ParsedTransaction parsed = StatementResponseMapper.map(node);

        assertThat(parsed.type()).isEqualTo(StagedTransactionType.INVESTMENT);
        assertThat(parsed.amount()).isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    void parsesAmountWithCurrencySymbols() {
        assertThat(StatementResponseMapper.parseAmount("₹12,345.67")).isEqualByComparingTo(new BigDecimal("12345.67"));
        assertThat(StatementResponseMapper.parseAmount("(500.00)")).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(StatementResponseMapper.parseAmount("abc")).isNull();
    }

    @Test
    void parsesTextBlobResponseIntoTransactions() throws Exception {
        // The verified /processDocument response shape: rows flattened into one text blob
        // keyed by the requested field, direction marked by CR/DR suffixes.
        JsonNode root = objectMapper.readTree(
                "{\"transactions\":\"01/09/2026 SALARY CREDIT SEPT 85000.00 CR " +
                "02/09/2026 UBER INDIA SYSTEMS 320.50 DR " +
                "05/09/2026 SWIGGY BANGALORE 486.75 DR\"}");

        List<ParsedTransaction> parsed = StatementResponseMapper.mapTextResponse(root);

        assertThat(parsed).hasSize(3);

        ParsedTransaction salary = parsed.get(0);
        assertThat(salary.type()).isEqualTo(StagedTransactionType.INCOME);
        assertThat(salary.amount()).isEqualByComparingTo(new BigDecimal("85000.00"));
        assertThat(salary.transactionDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(salary.description()).isEqualTo("SALARY CREDIT SEPT");

        ParsedTransaction uber = parsed.get(1);
        assertThat(uber.type()).isEqualTo(StagedTransactionType.EXPENSE);
        assertThat(uber.amount()).isEqualByComparingTo(new BigDecimal("320.50"));
        assertThat(uber.transactionDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(uber.description()).isEqualTo("UBER INDIA SYSTEMS");

        ParsedTransaction swiggy = parsed.get(2);
        assertThat(swiggy.type()).isEqualTo(StagedTransactionType.EXPENSE);
        assertThat(swiggy.amount()).isEqualByComparingTo(new BigDecimal("486.75"));
        assertThat(swiggy.transactionDate()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    void textBlobParserHandlesIsoDatesAndWrappers() throws Exception {
        JsonNode root = objectMapper.readTree(
                "{\"data\":{\"transactions\":\"2026-09-01 REFUND 250.00 CR 2026-09-03 ATM WITHDRAWAL 1000.00 DR\"}}");

        List<ParsedTransaction> parsed = StatementResponseMapper.mapTextResponse(root);

        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0).transactionDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(parsed.get(0).type()).isEqualTo(StagedTransactionType.INCOME);
        assertThat(parsed.get(1).type()).isEqualTo(StagedTransactionType.EXPENSE);
        assertThat(parsed.get(1).amount()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void textBlobParserReturnsEmptyForMissingBlob() throws Exception {
        assertThat(StatementResponseMapper.mapTextResponse(objectMapper.readTree("{}"))).isEmpty();
        assertThat(StatementResponseMapper.mapTextResponse(
                objectMapper.readTree("{\"transactions\":\"\"}"))).isEmpty();
        assertThat(StatementResponseMapper.mapTextResponse(null)).isEmpty();
    }
}
