package com.myfinancemanager.dto.imports;

import com.myfinancemanager.domain.ImportedTransaction;
import com.myfinancemanager.domain.PaymentMode;
import com.myfinancemanager.domain.StagedTransactionStatus;
import com.myfinancemanager.domain.StagedTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ImportedTransactionResponse(
        UUID id,
        StagedTransactionType transactionType,
        LocalDate transactionDate,
        String description,
        String merchant,
        BigDecimal amount,
        String category,
        PaymentMode paymentMode,
        String source,
        StagedTransactionStatus status,
        boolean duplicate,
        UUID duplicateOfId,
        Double confidence,
        String rawLine,
        UUID committedRecordId
) {
    public static ImportedTransactionResponse from(ImportedTransaction transaction) {
        return new ImportedTransactionResponse(
                transaction.getId(),
                transaction.getTransactionType(),
                transaction.getTransactionDate(),
                transaction.getDescription(),
                transaction.getMerchant(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getPaymentMode(),
                transaction.getSource(),
                transaction.getStatus(),
                transaction.isDuplicate(),
                transaction.getDuplicateOfId(),
                transaction.getConfidence(),
                transaction.getRawLine(),
                transaction.getCommittedRecordId());
    }
}
