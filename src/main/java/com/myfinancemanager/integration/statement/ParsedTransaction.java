package com.myfinancemanager.integration.statement;

import com.myfinancemanager.domain.PaymentMode;
import com.myfinancemanager.domain.StagedTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedTransaction(
        StagedTransactionType type,
        LocalDate transactionDate,
        String description,
        String merchant,
        BigDecimal amount,
        String category,
        PaymentMode paymentMode,
        String source,
        String rawLine,
        Double confidence
) {
}
