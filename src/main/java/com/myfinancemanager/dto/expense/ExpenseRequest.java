package com.myfinancemanager.dto.expense;

import com.myfinancemanager.domain.PaymentMode;
import com.myfinancemanager.domain.TransactionOrigin;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @Size(max = 200) String merchant,
        @Size(max = 100) String category,
        PaymentMode paymentMode,
        @NotNull LocalDate transactionDate,
        TransactionOrigin origin,
        boolean recurring,
        @Size(max = 2000) String notes
) {
}
