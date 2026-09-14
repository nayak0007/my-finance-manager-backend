package com.myfinancemanager.dto.dashboard;

import com.myfinancemanager.domain.TransactionOrigin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecentTransactionResponse(
        UUID id,
        String kind,
        String title,
        String category,
        BigDecimal amount,
        LocalDate transactionDate,
        TransactionOrigin origin
) {
}
