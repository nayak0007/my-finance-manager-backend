package com.myfinancemanager.domain;

import com.myfinancemanager.domain.converter.JsonMapConverter;
import com.myfinancemanager.domain.converter.JsonStringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "auto_capture_settings")
public class AutoCaptureSettings extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled = false;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = false;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "sender_allow_list", columnDefinition = "text")
    private List<String> senderAllowList = new ArrayList<>();

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "sender_block_list", columnDefinition = "text")
    private List<String> senderBlockList = new ArrayList<>();

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "metadata", columnDefinition = "text")
    private java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
}
