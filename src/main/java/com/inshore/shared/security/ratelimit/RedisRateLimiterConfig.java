package com.inshore.shared.security.ratelimit;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;

/**
 * Wires a Redis connection dedicated to the rate limiter, talking to it through the raw Lettuce
 * client rather than spring-data-redis. Bucket4j's Redis integration needs to read/write the
 * exact byte[] it wrote (its own CAS + Lua-script protocol); spring-data-redis's
 * RedisConnectionFactory adds its own (de)serialization layer on top, which silently corrupts
 * that state - see bucket4j/bucket4j#322. Keeping a separate, tiny RedisClient here avoids that
 * class of bug entirely, at the cost of one extra connection pool.
 */
@Configuration
public class RedisRateLimiterConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient rateLimiterRedisClient(RateLimitProperties properties) {
        RateLimitProperties.Redis redisProps = properties.getRedis();

        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(redisProps.getHost())
                .withPort(redisProps.getPort())
                .withDatabase(redisProps.getDatabase())
                .withTimeout(Duration.ofMillis(redisProps.getCommandTimeoutMs()))
                .withSsl(redisProps.isSsl());

        if (StringUtils.hasText(redisProps.getPassword())) {
            uriBuilder.withPassword(redisProps.getPassword().toCharArray());
        }

        ClientOptions clientOptions = ClientOptions.builder()
                .autoReconnect(true)
                .pingBeforeActivateConnection(true)
                // Fail fast instead of queuing commands indefinitely when Redis is down - the
                // filter's own try/catch + fail-open/fail-closed setting decides what happens next.
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(Duration.ofMillis(redisProps.getConnectTimeoutMs()))
                        .keepAlive(true)
                        .build())
                .build();

        RedisClient redisClient = RedisClient.create(uriBuilder.build());
        redisClient.setOptions(clientOptions);
        return redisClient;
    }

    @Bean
    public ProxyManager<byte[]> rateLimiterProxyManager(RedisClient redisClient, RateLimitProperties properties) {
        return LettuceBasedProxyManager.builderFor(redisClient)
                // Once a bucket has fully refilled (i.e. nobody has touched it in a while), let
                // Redis reclaim the key instead of holding onto it forever.
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                        Duration.ofMinutes(properties.getBucketTtlMinutes())))
                .build();
    }
}
