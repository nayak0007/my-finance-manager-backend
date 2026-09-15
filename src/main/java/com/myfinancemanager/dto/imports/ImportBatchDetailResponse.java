package com.myfinancemanager.dto.imports;

import java.util.List;

public record ImportBatchDetailResponse(
        ImportBatchResponse batch,
        List<ImportedTransactionResponse> transactions
) {
    public static ImportBatchDetailResponse of(ImportBatchResponse batch,
                                               List<ImportedTransactionResponse> transactions) {
        return new ImportBatchDetailResponse(batch, transactions);
    }
}
