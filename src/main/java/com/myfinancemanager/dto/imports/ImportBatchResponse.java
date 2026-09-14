package com.myfinancemanager.dto.imports;

import com.myfinancemanager.domain.ExtractionMethod;
import com.myfinancemanager.domain.ImportBatch;
import com.myfinancemanager.domain.ImportStatus;

import java.time.Instant;
import java.util.UUID;

public record ImportBatchResponse(
        UUID id,
        String fileName,
        String contentType,
        Long fileSize,
        ImportStatus status,
        ExtractionMethod extractionMethod,
        int totalTransactions,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static ImportBatchResponse from(ImportBatch batch) {
        return new ImportBatchResponse(
                batch.getId(),
                batch.getSourceFileName(),
                batch.getContentType(),
                batch.getFileSize(),
                batch.getStatus(),
                batch.getExtractionMethod(),
                batch.getTotalTransactions(),
                batch.getErrorMessage(),
                batch.getCreatedAt(),
                batch.getUpdatedAt());
    }
}
