package com.myfinancemanager.repository;

import com.myfinancemanager.domain.AutoCaptureQueueItem;
import com.myfinancemanager.domain.AutoCaptureStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutoCaptureQueueRepository extends JpaRepository<AutoCaptureQueueItem, UUID> {

    List<AutoCaptureQueueItem> findByUserId(UUID userId);

    Page<AutoCaptureQueueItem> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, AutoCaptureStatus status, Pageable pageable);

    Page<AutoCaptureQueueItem> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<AutoCaptureQueueItem> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndStatus(UUID userId, AutoCaptureStatus status);
}
