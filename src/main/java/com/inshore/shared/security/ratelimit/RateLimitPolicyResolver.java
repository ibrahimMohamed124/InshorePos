package com.inshore.shared.security.ratelimit;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Decides, for a given request:
 * <ul>
 *     <li>whether it should be rate limited at all (excluded paths short-circuit to "no");</li>
 *     <li>which named policy applies (login / signup / authenticatedApi / publicApi);</li>
 *     <li>the identity the bucket is keyed on - the authenticated user when we have one
 *     (populated earlier in the chain by {@code JwtValidator}), otherwise the client IP.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RateLimitPolicyResolver {

    private static final String LOGIN_PATH = "/auth/login";
    private static final String SIGNUP_PATH = "/auth/signup";
    private static final String API_PATTERN = "/api/**";

    private final RateLimitProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public Optional<ResolvedRateLimit> resolve(HttpServletRequest request) {
        String path = normalizedPath(request);

        for (String excluded : properties.getExcludedPaths()) {
            if (pathMatcher.match(excluded, path)) {
                return Optional.empty();
            }
        }

        if (pathMatcher.match(LOGIN_PATH, path)) {
            return Optional.of(new ResolvedRateLimit("login", properties.getLogin(), clientIp(request)));
        }

        if (pathMatcher.match(SIGNUP_PATH, path)) {
            return Optional.of(new ResolvedRateLimit("signup", properties.getSignup(), clientIp(request)));
        }

        if (pathMatcher.match(API_PATTERN, path)) {
            String identity = authenticatedIdentity().orElseGet(() -> clientIp(request));
            return Optional.of(new ResolvedRateLimit("authenticatedApi", properties.getAuthenticatedApi(), identity));
        }

        return Optional.of(new ResolvedRateLimit("publicApi", properties.getPublicApi(), clientIp(request)));
    }

    private String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path.isEmpty() ? "/" : path;
    }

    private Optional<String> authenticatedIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null) {
            return Optional.empty();
        }
        return Optional.of("user:" + authentication.getName());
    }

    private String clientIp(HttpServletRequest request) {
        if (properties.isTrustForwardedHeader()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwardedFor)) {
                // The header can carry a chain ("client, proxy1, proxy2") - the original
                // client is always the first entry.
                return forwardedFor.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
