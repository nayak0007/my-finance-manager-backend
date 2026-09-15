package com.myfinancemanager.dto.insights;

import com.myfinancemanager.domain.AIInsight;
import com.myfinancemanager.domain.InsightCategory;
import com.myfinancemanager.domain.InsightStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AIInsightResponse(
        UUID id,
        String title,
        String insightText,
        InsightCategory category,
        String modelUsed,
        Instant generatedAt,
        InsightStatus status,
        Map<String, Object> metadata
) {
    public static AIInsightResponse from(AIInsight insight) {
        return new AIInsightResponse(
                insight.getId(),
                insight.getTitle(),
                insight.getInsightText(),
                insight.getCategory(),
                insight.getModelUsed(),
                insight.getGeneratedAt(),
                insight.getStatus(),
                insight.getMetadata());
    }
}
