package com.myfinancemanager.dto.imports;

import com.myfinancemanager.domain.PaymentMode;
import com.myfinancemanager.domain.StagedTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CommitImportRequest(
        List<CommitItem> items
) {
    public record CommitItem(
            UUID id,
            Boolean include,
            StagedTransactionType transactionType,
            LocalDate transactionDate,
            String description,
            String merchant,
            BigDecimal amount,
            String category,
            PaymentMode paymentMode,
            String source
    ) {
    }
}
