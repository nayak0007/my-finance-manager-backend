package com.myfinancemanager.dto.investment;

import com.myfinancemanager.domain.InvestmentType;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioSummaryResponse(
        BigDecimal totalInvested,
        BigDecimal currentValue,
        BigDecimal gainLoss,
        BigDecimal gainLossPercent,
        List<AllocationItem> allocation,
        long count
) {
    public record AllocationItem(
            InvestmentType type,
            BigDecimal invested,
            BigDecimal currentValue,
            BigDecimal percentage
    ) {
    }
}
