package com.myfinancemanager.repository;

import com.myfinancemanager.domain.ImportBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    List<ImportBatch> findByUserId(UUID userId);

    Page<ImportBatch> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<ImportBatch> findByIdAndUserId(UUID id, UUID userId);
}
