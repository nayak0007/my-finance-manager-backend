package com.myfinancemanager.integration.google;

public record GoogleAccount(
        String subject,
        String email,
        String fullName,
        boolean emailVerified
) {
}
