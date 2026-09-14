package com.myfinancemanager.dto.income;

import com.myfinancemanager.domain.TransactionOrigin;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IncomeRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @Size(max = 200) String source,
        @Size(max = 100) String category,
        @NotNull LocalDate transactionDate,
        TransactionOrigin origin,
        boolean recurring,
        @Size(max = 2000) String notes
) {
}
