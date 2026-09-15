package com.myfinancemanager.dto.insights;

import com.myfinancemanager.domain.InsightStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateInsightStatusRequest(
        @NotNull InsightStatus status
) {
}
