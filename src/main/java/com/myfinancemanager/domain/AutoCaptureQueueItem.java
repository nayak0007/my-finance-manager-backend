package com.myfinancemanager.domain;

import com.myfinancemanager.domain.converter.JsonMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "auto_capture_queue", indexes = {
        @Index(name = "idx_auto_capture_user_status", columnList = "user_id, status")
})
public class AutoCaptureQueueItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private AutoCaptureSource sourceType;

    @Column(name = "sender", length = 320)
    private String sender;

    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;

    @Enumerated(EnumType.STRING)
    @Column(name = "parsed_type", length = 20)
    private StagedTransactionType parsedType;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "parsed_data", columnDefinition = "text")
    private Map<String, Object> parsedData = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AutoCaptureStatus status = AutoCaptureStatus.PENDING;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "committed_record_id")
    private UUID committedRecordId;
}
