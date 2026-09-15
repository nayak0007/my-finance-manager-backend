package com.myfinancemanager.domain;

import com.myfinancemanager.domain.converter.JsonMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "provider_subject", length = 255)
    private String providerSubject;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "preferences", columnDefinition = "text")
    private Map<String, Object> preferences = new LinkedHashMap<>();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
