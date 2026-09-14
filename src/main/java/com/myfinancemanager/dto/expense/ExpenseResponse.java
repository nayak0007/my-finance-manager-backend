package com.myfinancemanager.dto.expense;

import com.myfinancemanager.domain.ExpenseRecord;
import com.myfinancemanager.domain.PaymentMode;
import com.myfinancemanager.domain.TransactionOrigin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        BigDecimal amount,
        String merchant,
        String category,
        PaymentMode paymentMode,
        LocalDate transactionDate,
        TransactionOrigin origin,
        boolean recurring,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static ExpenseResponse from(ExpenseRecord record) {
        return new ExpenseResponse(
                record.getId(),
                record.getAmount(),
                record.getMerchant(),
                record.getCategory(),
                record.getPaymentMode(),
                record.getTransactionDate(),
                record.getOrigin(),
                record.isRecurring(),
                record.getNotes(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
