package com.myfinancemanager.dto.dashboard;

import java.math.BigDecimal;

public record MonthlyTrendPoint(
        int year,
        int month,
        String label,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net
) {
}
