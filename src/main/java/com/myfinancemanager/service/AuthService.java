package com.myfinancemanager.service;

import com.myfinancemanager.common.exception.ConflictException;
import com.myfinancemanager.common.exception.UnauthorizedException;
import com.myfinancemanager.config.JwtProperties;
import com.myfinancemanager.domain.AuthProvider;
import com.myfinancemanager.domain.RefreshToken;
import com.myfinancemanager.domain.User;
import com.myfinancemanager.dto.auth.AuthResponse;
import com.myfinancemanager.dto.auth.GoogleLoginRequest;
import com.myfinancemanager.dto.auth.LoginRequest;
import com.myfinancemanager.dto.auth.RegisterRequest;
import com.myfinancemanager.dto.user.UserResponse;
import com.myfinancemanager.integration.google.GoogleAccount;
import com.myfinancemanager.integration.google.GoogleTokenVerifier;
import com.myfinancemanager.repository.RefreshTokenRepository;
import com.myfinancemanager.repository.UserRepository;
import com.myfinancemanager.security.JwtService;
import com.myfinancemanager.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final GoogleTokenVerifier googleTokenVerifier;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEmailVerified(false);
        user = userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        user.setLastLoginAt(Instant.now());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        GoogleAccount account = googleTokenVerifier.verify(request.idToken());
        User user = userRepository.findByProviderSubject(account.subject())
                .or(() -> userRepository.findByEmailIgnoreCase(account.email()))
                .orElseGet(() -> {
                    User created = new User();
                    created.setEmail(account.email().toLowerCase());
                    created.setFullName(account.fullName());
                    created.setAuthProvider(AuthProvider.GOOGLE);
                    created.setProviderSubject(account.subject());
                    created.setEmailVerified(account.emailVerified());
                    return created;
                });
        user.setProviderSubject(account.subject());
        if (user.getAuthProvider() == AuthProvider.LOCAL) {
            user.setEmailVerified(true);
        }
        user.setLastLoginAt(Instant.now());
        user = userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        String hash = TokenHasher.sha256Hex(refreshTokenValue);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (existing.isRevoked() || existing.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token expired or revoked");
        }
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);
        return issueTokens(existing.getUser());
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(refreshTokenValue))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void logoutAll(User user) {
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshTokenValue();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(TokenHasher.sha256Hex(rawRefreshToken));
        refreshToken.setExpiresAt(jwtService.refreshTokenExpiry());
        refreshTokenRepository.save(refreshToken);
        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtProperties.accessTokenTtl().toSeconds(),
                UserResponse.from(user));
    }
}
