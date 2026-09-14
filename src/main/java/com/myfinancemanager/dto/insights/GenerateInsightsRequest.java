package com.myfinancemanager.dto.insights;

public record GenerateInsightsRequest(
        Integer months,
        String focus
) {
}
