package com.myfinancemanager.dto.budget;

import com.myfinancemanager.domain.Budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        String category,
        BigDecimal monthlyLimit,
        Instant createdAt,
        Instant updatedAt
) {
    public static BudgetResponse from(Budget budget) {
        return new BudgetResponse(
                budget.getId(),
                budget.getCategory(),
                budget.getMonthlyLimit(),
                budget.getCreatedAt(),
                budget.getUpdatedAt());
    }
}
