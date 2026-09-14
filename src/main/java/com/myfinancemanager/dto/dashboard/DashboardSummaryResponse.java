package com.myfinancemanager.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardSummaryResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal totalInvestments,
        BigDecimal netSavings,
        long incomeCount,
        long expenseCount,
        long investmentCount
) {
}
