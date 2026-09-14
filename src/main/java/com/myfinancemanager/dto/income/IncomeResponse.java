package com.myfinancemanager.dto.income;

import com.myfinancemanager.domain.IncomeRecord;
import com.myfinancemanager.domain.TransactionOrigin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record IncomeResponse(
        UUID id,
        BigDecimal amount,
        String source,
        String category,
        LocalDate transactionDate,
        TransactionOrigin origin,
        boolean recurring,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static IncomeResponse from(IncomeRecord record) {
        return new IncomeResponse(
                record.getId(),
                record.getAmount(),
                record.getSource(),
                record.getCategory(),
                record.getTransactionDate(),
                record.getOrigin(),
                record.isRecurring(),
                record.getNotes(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
