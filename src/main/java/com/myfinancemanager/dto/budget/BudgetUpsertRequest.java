package com.myfinancemanager.dto.budget;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Sets the monthly limit for the category in the request path. There is no category field: the
 * path segment is the key, so a repeated call updates rather than creating a second budget.
 */
public record BudgetUpsertRequest(
        @NotNull @Positive(message = "monthlyLimit must be greater than zero") BigDecimal monthlyLimit
) {
}
