package com.myfinancemanager.dto.autocapture;

import com.myfinancemanager.domain.AutoCaptureQueueItem;
import com.myfinancemanager.domain.AutoCaptureSource;
import com.myfinancemanager.domain.AutoCaptureStatus;
import com.myfinancemanager.domain.StagedTransactionType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AutoCaptureItemResponse(
        UUID id,
        AutoCaptureSource sourceType,
        String sender,
        String rawText,
        StagedTransactionType parsedType,
        Map<String, Object> parsedData,
        AutoCaptureStatus status,
        Double confidence,
        UUID committedRecordId,
        Instant createdAt,
        Instant updatedAt
) {
    public static AutoCaptureItemResponse from(AutoCaptureQueueItem item) {
        return new AutoCaptureItemResponse(
                item.getId(),
                item.getSourceType(),
                item.getSender(),
                item.getRawText(),
                item.getParsedType(),
                item.getParsedData(),
                item.getStatus(),
                item.getConfidence(),
                item.getCommittedRecordId(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
