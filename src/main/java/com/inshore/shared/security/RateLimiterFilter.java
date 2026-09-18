package com.inshore.shared.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.inshore.shared.security.ratelimit.RateLimitPolicyResolver;
import com.inshore.shared.security.ratelimit.RateLimitProperties;
import com.inshore.shared.security.ratelimit.ResolvedRateLimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Distributed rate limiter backed by Redis + Bucket4j.
 *
 * <p>One request = one attempt to consume a single token from a Redis-resident bucket, atomically
 * (Bucket4j's CAS + Lua-script protocol against Lettuce), so the limit holds correctly across every
 * instance of this service behind the load balancer - not just per-JVM.
 *
 * <p>Runs <b>after</b> {@link JwtValidator} in the chain (see {@code SecurityConfig}), so that when
 * a valid JWT is present the request is already authenticated and can be rate-limited per user
 * instead of per IP; anonymous/public/auth-endpoint traffic falls back to per-IP limiting.
 *
 * <p>This class is registered directly into Spring Security's filter chain in
 * {@code SecurityConfig}. It is still annotated {@code @Component} so it can be dependency-injected,
 * but that also means Spring Boot would otherwise auto-register it a <em>second</em> time as a plain
 * servlet filter applied to every request. That auto-registration is explicitly disabled with a
 * {@code FilterRegistrationBean} in {@code SecurityConfig} - without it, every request would be
 * rate-limited (and its headers/429 written) twice.
 *
 * <p>The 429 error body is built by hand instead of through an injected JSON mapper: it's a tiny,
 * fixed-shape payload, and this filter shouldn't have to care whether the app is wired for
 * Jackson 2's {@code ObjectMapper} or Jackson 3's {@code JsonMapper} (Spring Boot 4 auto-configures
 * the latter by default).
 */
@Component
@RequiredArgsConstructor
public class RateLimiterFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterFilter.class);
    private static final String KEY_PREFIX = "rl:";

    private final RateLimitProperties properties;
    private final RateLimitPolicyResolver policyResolver;
    private final ProxyManager<byte[]> proxyManager;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<ResolvedRateLimit> resolved = policyResolver.resolve(request);
        if (resolved.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        ResolvedRateLimit rateLimit = resolved.get();
        RateLimitProperties.Policy policy = rateLimit.policy();
        if (policy == null || policy.getCapacity() <= 0) {
            // Defensive: a misconfigured/blank policy should never take the API down.
            filterChain.doFilter(request, response);
            return;
        }

        byte[] bucketKey = (KEY_PREFIX + rateLimit.policyName() + ":" + rateLimit.identifier())
                .getBytes(StandardCharsets.UTF_8);

        try {
            Bucket bucket = proxyManager.builder().build(bucketKey, () -> buildConfiguration(policy));
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            response.setHeader("X-RateLimit-Limit", String.valueOf(policy.getCapacity()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(probe.getRemainingTokens(), 0)));

            if (probe.isConsumed()) {
                filterChain.doFilter(request, response);
            } else {
                long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
                writeTooManyRequests(response, retryAfterSeconds, rateLimit.policyName());
            }
        } catch (Exception ex) {
            log.warn("Rate limiter backend unavailable (policy={}, failOpen={}): {}",
                    rateLimit.policyName(), properties.isFailOpen(), ex.toString());
            if (properties.isFailOpen()) {
                filterChain.doFilter(request, response);
            } else {
                writeTooManyRequests(response, 5, rateLimit.policyName());
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Never rate-limit CORS preflight - it carries no credentials/business meaning and
        // blocking it just breaks browsers for everyone behind that IP.
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private BucketConfiguration buildConfiguration(RateLimitProperties.Policy policy) {
        // `var` on purpose: the concrete builder type differs across Bucket4j releases/back-ends,
        // and the fluent chain below only ever needs the addLimit/build contract, not the name.
        var builder = BucketConfiguration.builder()
                .addLimit(limit -> limit
                        .capacity(policy.getCapacity())
                        .refillGreedy(policy.getRefillTokens(), Duration.ofSeconds(policy.getRefillPeriodSeconds())));

        if (policy.hasSecondaryLimit()) {
            builder.addLimit(limit -> limit
                    .capacity(policy.getSecondaryCapacity())
                    .refillGreedy(policy.getSecondaryRefillTokens(),
                            Duration.ofSeconds(policy.getSecondaryRefillPeriodSeconds())));
        }

        return builder.build();
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds, String policyName)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // Hand-built JSON: the payload is small and fixed-shape, and policyName only ever comes
        // from our own enum-like set of literals ("login"/"signup"/"authenticatedApi"/"publicApi"),
        // so a full JSON library isn't needed here - just escape it defensively anyway.
        String json = "{"
                + "\"success\":false,"
                + "\"message\":\"Too many requests - please slow down and try again later.\","
                + "\"policy\":\"" + escapeJson(policyName) + "\","
                + "\"retryAfterSeconds\":" + retryAfterSeconds
                + "}";

        response.getWriter().write(json);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
