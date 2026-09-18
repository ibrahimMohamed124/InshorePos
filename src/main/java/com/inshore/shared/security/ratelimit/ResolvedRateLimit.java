package com.inshore.shared.security.ratelimit;

/**
 * @param policyName short, stable name used both in the Redis key and in the JSON error body
 *                   ("login", "signup", "authenticatedApi", "publicApi").
 * @param policy     the bandwidth configuration to enforce.
 * @param identifier who the bucket belongs to - "user:&lt;email&gt;" for authenticated calls,
 *                   otherwise the caller's IP address.
 */
public record ResolvedRateLimit(String policyName, RateLimitProperties.Policy policy, String identifier) {
}
