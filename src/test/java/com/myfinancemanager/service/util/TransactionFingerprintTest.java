package com.myfinancemanager.service.util;

import com.myfinancemanager.domain.StagedTransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionFingerprintTest {

    @Test
    void producesSameFingerprintForEquivalentTransactions() {
        String first = TransactionFingerprint.of(
                StagedTransactionType.EXPENSE, new BigDecimal("123.40"),
                LocalDate.of(2026, 1, 15), "Starbucks #1234");
        String second = TransactionFingerprint.of(
                StagedTransactionType.EXPENSE, new BigDecimal("123.4"),
                LocalDate.of(2026, 1, 15), "  starbucks 1234 ");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void producesDifferentFingerprintForDifferentAmount() {
        String first = TransactionFingerprint.of(
                StagedTransactionType.EXPENSE, new BigDecimal("10.00"),
                LocalDate.of(2026, 1, 15), "Uber");
        String second = TransactionFingerprint.of(
                StagedTransactionType.EXPENSE, new BigDecimal("11.00"),
                LocalDate.of(2026, 1, 15), "Uber");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void producesDifferentFingerprintForDifferentType() {
        String income = TransactionFingerprint.of(
                StagedTransactionType.INCOME, new BigDecimal("500"),
                LocalDate.of(2026, 2, 1), "Deposit");
        String expense = TransactionFingerprint.of(
                StagedTransactionType.EXPENSE, new BigDecimal("500"),
                LocalDate.of(2026, 2, 1), "Deposit");

        assertThat(income).isNotEqualTo(expense);
    }
}
