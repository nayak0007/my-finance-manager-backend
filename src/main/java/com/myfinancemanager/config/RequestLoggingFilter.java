package com.myfinancemanager.config;

import com.myfinancemanager.security.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Logs one line per API request so that activity on the deployed service is
 * observable (Render only shows what the application writes to stdout).
 *
 * Registered inside the Spring Security chain, immediately after
 * {@code JwtAuthenticationFilter}, so the authenticated user is available once
 * the request has been handled.
 *
 * Never logs request bodies, passwords or tokens.
 *
 * Deliberately NOT a Spring bean: {@code @Component} filters are picked up by
 * Boot's servlet registration as well, which would run this in the servlet chain
 * (before authentication) instead of inside the security chain.
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String[] SKIPPED_PREFIXES = {
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs",
            "/swagger-ui"
    };

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : SKIPPED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        // Pre-flight requests carry no useful information.
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startedAt;
            UUID userId = SecurityUtils.currentUserIdOrNull();
            log.info("{} {} -> {} ({} ms) user={} ip={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    userId == null ? "anonymous" : userId,
                    clientIp(request));
        }
    }

    private String clientIp(HttpServletRequest request) {
        // `server.forward-headers-strategy: framework` resolves the real client
        // address behind the platform proxy, so getRemoteAddr() is authoritative.
        return request.getRemoteAddr();
    }
}
