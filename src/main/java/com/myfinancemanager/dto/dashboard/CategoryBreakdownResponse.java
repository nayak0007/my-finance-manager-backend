package com.myfinancemanager.dto.dashboard;

import java.math.BigDecimal;

public record CategoryBreakdownResponse(
        String category,
        BigDecimal amount,
        BigDecimal percentage
) {
}
