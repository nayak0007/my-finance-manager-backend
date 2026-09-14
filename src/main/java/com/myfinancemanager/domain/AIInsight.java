package com.myfinancemanager.domain;

import com.myfinancemanager.domain.converter.JsonMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "ai_insights", indexes = {
        @Index(name = "idx_ai_insights_user", columnList = "user_id, generated_at")
})
public class AIInsight extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", length = 300)
    private String title;

    @Column(name = "insight_text", columnDefinition = "text", nullable = false)
    private String insightText;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private InsightCategory category = InsightCategory.GENERAL;

    @Column(name = "model_used", length = 200)
    private String modelUsed;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InsightStatus status = InsightStatus.NEW;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "metadata", columnDefinition = "text")
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
