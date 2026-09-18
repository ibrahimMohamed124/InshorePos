package com.inshore.shared.security.ratelimit;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds every {@code app.rate-limit.*} property (see application.properties for the full,
 * documented list). Each field below is initialized with a working default, so overriding a
 * single nested property (e.g. {@code app.rate-limit.login.capacity=10}) leaves every sibling
 * property - and every other policy - untouched.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** Master on/off switch for the whole limiter. */
    private boolean enabled = true;

    /**
     * true  -> if Redis is unreachable, let the request through and just log a warning
     *          (availability over strictness - recommended for a POS system).
     * false -> if Redis is unreachable, reject the request with 429 instead.
     */
    private boolean failOpen = true;

    /**
     * Only honour X-Forwarded-For / X-Real-IP when this app is actually behind a trusted
     * reverse proxy that sets them. Otherwise a client can spoof the header and dodge
     * per-IP limits entirely.
     */
    private boolean trustForwardedHeader = false;

    /** How long an idle bucket key is kept in Redis after fully refilling. */
    private long bucketTtlMinutes = 10;

    /** Ant-style path patterns the limiter never applies to. */
    private List<String> excludedPaths = List.of("/actuator/**", "/error", "/favicon.ico");

    private Policy login = new Policy(5, 5, 60, 20, 20, 3600);
    private Policy signup = new Policy(3, 3, 60, 10, 10, 3600);
    private Policy authenticatedApi = new Policy(120, 120, 60, 3000, 3000, 3600);
    private Policy publicApi = new Policy(60, 60, 60, 0, 0, 0);

    private Redis redis = new Redis();

    @Getter
    @Setter
    public static class Policy {

        /** Max tokens the primary (burst) bandwidth can hold. */
        private long capacity;
        /** Tokens restored every refillPeriodSeconds for the primary bandwidth. */
        private long refillTokens;
        private long refillPeriodSeconds;

        /**
         * Optional second, slower bandwidth layered on top of the primary one (e.g. a per-minute
         * burst limit plus a per-hour sustained limit). Set secondaryCapacity to 0 to disable it.
         */
        private long secondaryCapacity;
        private long secondaryRefillTokens;
        private long secondaryRefillPeriodSeconds;

        public Policy() {
        }

        public Policy(long capacity, long refillTokens, long refillPeriodSeconds,
                      long secondaryCapacity, long secondaryRefillTokens, long secondaryRefillPeriodSeconds) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriodSeconds = refillPeriodSeconds;
            this.secondaryCapacity = secondaryCapacity;
            this.secondaryRefillTokens = secondaryRefillTokens;
            this.secondaryRefillPeriodSeconds = secondaryRefillPeriodSeconds;
        }

        public boolean hasSecondaryLimit() {
            return secondaryCapacity > 0 && secondaryRefillTokens > 0 && secondaryRefillPeriodSeconds > 0;
        }
    }

    /** Connection details for the dedicated Redis instance used by the rate limiter. */
    @Getter
    @Setter
    public static class Redis {
        private String host = "localhost";
        private int port = 6379;
        private String password = "";
        private int database = 0;
        private boolean ssl = false;
        private long connectTimeoutMs = 2000;
        private long commandTimeoutMs = 2000;
    }
}
