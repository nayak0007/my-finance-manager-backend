package com.myfinancemanager.repository;

import com.myfinancemanager.domain.AIInsight;
import com.myfinancemanager.domain.InsightStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AIInsightRepository extends JpaRepository<AIInsight, UUID> {

    List<AIInsight> findByUserId(UUID userId);

    Page<AIInsight> findByUserIdOrderByGeneratedAtDesc(UUID userId, Pageable pageable);

    Page<AIInsight> findByUserIdAndStatusOrderByGeneratedAtDesc(UUID userId, InsightStatus status, Pageable pageable);

    Optional<AIInsight> findByIdAndUserId(UUID id, UUID userId);
}
