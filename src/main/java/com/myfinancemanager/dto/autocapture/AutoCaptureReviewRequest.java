package com.myfinancemanager.dto.autocapture;

import com.myfinancemanager.domain.PaymentMode;
import com.myfinancemanager.domain.StagedTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AutoCaptureReviewRequest(
        StagedTransactionType transactionType,
        LocalDate transactionDate,
        String description,
        String merchant,
        String category,
        PaymentMode paymentMode,
        String source,
        BigDecimal amount
) {
}
