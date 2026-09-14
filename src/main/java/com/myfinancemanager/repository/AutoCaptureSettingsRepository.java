package com.myfinancemanager.repository;

import com.myfinancemanager.domain.AutoCaptureSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AutoCaptureSettingsRepository extends JpaRepository<AutoCaptureSettings, UUID> {

    Optional<AutoCaptureSettings> findByUserId(UUID userId);
}
