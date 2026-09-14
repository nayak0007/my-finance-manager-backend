package com.myfinancemanager.dto.investment;

import com.myfinancemanager.domain.InvestmentType;
import com.myfinancemanager.domain.TransactionOrigin;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentRequest(
        @NotBlank @Size(max = 200) String instrumentName,
        InvestmentType investmentType,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amountInvested,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal currentValue,
        @NotNull LocalDate transactionDate,
        @Size(max = 200) String broker,
        TransactionOrigin origin,
        @Size(max = 2000) String notes
) {
}
