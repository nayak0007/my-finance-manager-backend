package com.myfinancemanager.security;

import com.myfinancemanager.service.UserProvisioningService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Turns a Neon Auth access token into a Spring Security authentication.
 *
 * <p>The app obtains the JWT from Neon Auth directly (it never sends us a password), and this
 * filter is the single place that trusts it: the signature is verified against Neon Auth's
 * JWKS, then {@link UserProvisioningService} maps the token's {@code sub} onto a local user
 * row so that every financial record still has a stable owner.
 *
 * <p>Any failure leaves the request anonymous — the chain reports 401 on its own. Nothing here
 * writes a response, so a malformed token can never turn into a 500.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NeonAuthAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final UserProvisioningService userProvisioningService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Jwt jwt = jwtDecoder.decode(header.substring(BEARER_PREFIX.length()));
                UserPrincipal principal = userProvisioningService.principalFor(jwt);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // Expired, forged, wrong issuer/audience, or not a JWT at all.
                log.debug("Rejected bearer token for {} {}: {}",
                        request.getMethod(), request.getRequestURI(), ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
