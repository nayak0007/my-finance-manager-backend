package com.myfinancemanager.service;

import com.myfinancemanager.domain.User;
import com.myfinancemanager.repository.UserRepository;
import com.myfinancemanager.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges Neon Auth identities to local user rows.
 *
 * <p>Neon Auth is the only authority on who someone is; our {@code users} table is the owner of
 * their financial data. The link is stored in {@code users.auth_subject} — the JWT's {@code sub}
 * claim, which is the {@code neon_auth.user.id}.
 *
 * <p>The local row deliberately keeps its own generated id rather than reusing the subject, so
 * that every existing record keeps pointing at the same owner no matter how the identity
 * arrived. Linking by email means an account created before the Neon Auth migration is adopted
 * rather than duplicated (the unique index on lower(email) would otherwise reject the insert).
 *
 * <p>This runs on the first authenticated request, so it must be idempotent and safe under
 * concurrency: two parallel requests from a fresh sign-in are entirely normal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProvisioningService {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_NAME = "name";
    static final String CLAIM_EMAIL_VERIFIED = "emailVerified";

    private final UserRepository userRepository;

    @Transactional
    public UserPrincipal principalFor(Jwt jwt) {
        String subject = subjectOf(jwt);
        User user = userRepository.findByAuthSubject(subject)
                .map(existing -> syncFromToken(existing, jwt))
                .orElseGet(() -> linkOrCreate(subject, jwt));
        return UserPrincipal.from(user);
    }

    private String subjectOf(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new JwtException("Neon Auth token has no sub claim");
        }
        return subject;
    }

    private User linkOrCreate(String subject, Jwt jwt) {
        String email = emailOf(jwt);
        User user = userRepository.findByEmailIgnoreCase(email).orElseGet(User::new);
        boolean isNew = user.getId() == null;
        user.setAuthSubject(subject);
        user.setEmail(email);

        // A profile name the user edited in the app outranks the one Neon Auth holds.
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            user.setFullName(fullNameOf(jwt));
        }
        applyVerifiedFlag(user, jwt);

        try {
            User saved = userRepository.saveAndFlush(user);
            log.info("{} local user {} for Neon Auth subject {} (email {})",
                    isNew ? "Created" : "Linked", saved.getId(), subject, email);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            // Another request provisioned this identity between our read and write.
            return userRepository.findByAuthSubject(subject).orElseThrow(() -> ex);
        }
    }

    /** Refreshes the mutable claims on every request; the token is the source of truth for them. */
    private User syncFromToken(User user, Jwt jwt) {
        String tokenEmail = jwt.getClaimAsString(CLAIM_EMAIL);
        if (tokenEmail != null && !tokenEmail.isBlank() && !tokenEmail.equalsIgnoreCase(user.getEmail())) {
            user.setEmail(tokenEmail);
        }
        boolean emailVerified = isEmailVerified(jwt);
        if (user.isEmailVerified() != emailVerified) {
            applyVerifiedFlag(user, jwt);
        }
        return userRepository.save(user);
    }

    private void applyVerifiedFlag(User user, Jwt jwt) {
        user.setEmailVerified(isEmailVerified(jwt));
    }

    private boolean isEmailVerified(Jwt jwt) {
        Object claim = jwt.getClaim(CLAIM_EMAIL_VERIFIED);
        return claim instanceof Boolean value && value;
    }

    private String emailOf(Jwt jwt) {
        String email = jwt.getClaimAsString(CLAIM_EMAIL);
        if (email == null || email.isBlank()) {
            throw new JwtException("Neon Auth token has no email claim");
        }
        return email.trim().toLowerCase();
    }

    private String fullNameOf(Jwt jwt) {
        String name = jwt.getClaimAsString(CLAIM_NAME);
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        String email = emailOf(jwt);
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
