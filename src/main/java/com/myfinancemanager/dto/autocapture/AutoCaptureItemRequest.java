package com.myfinancemanager.dto.autocapture;

import com.myfinancemanager.domain.AutoCaptureSource;
import com.myfinancemanager.domain.StagedTransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record AutoCaptureItemRequest(
        @NotNull AutoCaptureSource sourceType,
        @Size(max = 320) String sender,
        @Size(max = 20000) String rawText,
        StagedTransactionType parsedType,
        Map<String, Object> parsedData,
        Double confidence
) {
}
