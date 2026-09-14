package com.myfinancemanager.repository;

import com.myfinancemanager.domain.ImportedTransaction;
import com.myfinancemanager.domain.StagedTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportedTransactionRepository extends JpaRepository<ImportedTransaction, UUID> {

    List<ImportedTransaction> findByBatchIdOrderByTransactionDateAscIdAsc(UUID batchId);

    List<ImportedTransaction> findByBatchIdAndStatus(UUID batchId, StagedTransactionStatus status);

    Optional<ImportedTransaction> findByIdAndBatchUserId(UUID id, UUID userId);

    long countByBatchIdAndStatus(UUID batchId, StagedTransactionStatus status);
}
