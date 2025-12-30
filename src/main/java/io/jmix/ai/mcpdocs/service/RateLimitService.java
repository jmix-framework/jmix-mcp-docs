package io.jmix.ai.mcpdocs.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.jmix.ai.mcpdocs.config.RateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Request-based rate limiting service using token bucket algorithm.
 *
 * <p>Implements two-level rate limiting:
 * <ul>
 *   <li>Global rate limit - shared across all clients</li>
 *   <li>Per-IP rate limit - individual limit for each IP address</li>
 * </ul>
 *
 * <p>Uses Bucket4j for token bucket implementation and Caffeine for
 * caching per-IP buckets with automatic eviction.
 *
 * <p>Configuration via {@link RateLimitProperties}:
 * <pre>
 * rate-limit.global.capacity=100          # Max requests
 * rate-limit.global.refill-tokens=100     # Tokens to add
 * rate-limit.global.refill-duration=1m    # Refill period
 *
 * rate-limit.ip.capacity=3
 * rate-limit.ip.refill-tokens=3
 * rate-limit.ip.refill-duration=5s
 *
 * rate-limit.cache.max-size=1000
 * rate-limit.cache.expire-duration=5m
 * </pre>
 */
@Service
public class RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

    private final RateLimitProperties properties;

    /** Cache of per-IP token buckets with automatic eviction */
    private final Cache<String, Bucket> ipBucketCache;

    /** Shared global token bucket */
    private final Bucket globalBucket;

    public RateLimitService(RateLimitProperties properties) {
        this.properties = properties;

        // Initialize cache with eviction policy
        this.ipBucketCache = Caffeine.newBuilder()
                .maximumSize(properties.getCache().getMaxSize())
                .expireAfterAccess(properties.getCache().getExpireDuration())
                .build();

        // Create global bucket
        this.globalBucket = createBucket(
                properties.getGlobal().getCapacity(),
                properties.getGlobal().getRefillTokens(),
                properties.getGlobal().getRefillDuration()
        );

        logger.info("RateLimitService initialized with global limit: {} requests per {}, cache size: {}",
                properties.getGlobal().getCapacity(),
                properties.getGlobal().getRefillDuration(),
                properties.getCache().getMaxSize());
    }

    /**
     * Attempts to consume one token from global bucket.
     *
     * @return true if request allowed, false if rate limit exceeded
     */
    public boolean tryConsumeGlobal() {
        boolean allowed = globalBucket.tryConsume(1);
        if (!allowed) {
            logger.warn("Global rate limit exceeded");
        }
        return allowed;
    }

    /**
     * Attempts to consume one token from per-IP bucket.
     * Creates new bucket if not cached.
     *
     * @param ipAddress client IP address
     * @return true if request allowed, false if rate limit exceeded
     */
    public boolean tryConsumeForIp(String ipAddress) {
        Bucket bucket = ipBucketCache.get(ipAddress, this::createIpBucket);
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            logger.warn("Rate limit exceeded for IP: {}", ipAddress);
        }

        return allowed;
    }

    /**
     * Creates a new bucket for IP address (called by cache on miss).
     */
    private Bucket createIpBucket(String ipAddress) {
        logger.debug("Creating new bucket for IP: {}", ipAddress);
        return createBucket(
                properties.getIp().getCapacity(),
                properties.getIp().getRefillTokens(),
                properties.getIp().getRefillDuration()
        );
    }

    /**
     * Creates token bucket with specified capacity and refill rate.
     * Uses Bucket4j's interval refill strategy.
     */
    private Bucket createBucket(int capacity, int refillTokens, Duration refillDuration) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(refillTokens, refillDuration)
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Gets remaining tokens for IP address.
     *
     * @param ipAddress client IP address
     * @return available tokens, or full capacity if not cached
     */
    public long getRemainingTokensForIp(String ipAddress) {
        Bucket bucket = ipBucketCache.getIfPresent(ipAddress);
        return bucket != null ? bucket.getAvailableTokens() : properties.getIp().getCapacity();
    }

    /**
     * Gets remaining tokens in global bucket.
     *
     * @return available global tokens
     */
    public long getGlobalRemainingTokens() {
        return globalBucket.getAvailableTokens();
    }

    /**
     * Gets per-IP refill duration in seconds.
     * Used for Retry-After header.
     */
    public long getIpRefillDurationSeconds() {
        return properties.getIp().getRefillDuration().getSeconds();
    }

    /**
     * Gets global refill duration in seconds.
     * Used for Retry-After header.
     */
    public long getGlobalRefillDurationSeconds() {
        return properties.getGlobal().getRefillDuration().getSeconds();
    }
}
