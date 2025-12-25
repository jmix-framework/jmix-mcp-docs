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
 * Token-based rate limiting service.
 * Tracks AI token consumption (embeddings) to prevent abuse and protect budget.
 * Uses multiple time windows:
 * - Per minute: Fast protection against bursts
 * - Per hour: Medium-term budget control
 * - Per day: Long-term budget protection
 * Based on embedding costs: text-embedding-3-small @ $0.02/1M tokens
 * - Typical query: ~100 input tokens
 * - Real cost: ~$0.000002 per request
 * - 100k requests/day ≈ $0.20/day
 */
@Service
public class TokenBudgetService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBudgetService.class);

    private final RateLimitProperties properties;
    private final Cache<String, IpTokenBuckets> ipBucketCache;
    private final GlobalTokenBuckets globalBuckets;

    public TokenBudgetService(RateLimitProperties properties) {
        this.properties = properties;

        this.ipBucketCache = Caffeine.newBuilder()
                .maximumSize(properties.getCache().getMaxSize())
                .expireAfterAccess(properties.getCache().getExpireDuration())
                .build();

        this.globalBuckets = createGlobalBuckets();

        logger.info("TokenBudgetService initialized with global limits: {}/min, {}/hour, {}/day",
                properties.getTokens().getGlobal().getPerMinute(),
                properties.getTokens().getGlobal().getPerHour(),
                properties.getTokens().getGlobal().getPerDay());
    }

    /**
     * Try to consume tokens globally (all IPs combined).
     *
     * @param tokens number of tokens to consume
     * @return true if tokens were consumed, false if limit exceeded
     */
    public boolean tryConsumeGlobal(int tokens) {
        // First, check if ALL buckets can accommodate (probe phase)
        boolean perMinuteAvailable = globalBuckets.perMinute.getAvailableTokens() >= tokens;
        boolean perHourAvailable = globalBuckets.perHour.getAvailableTokens() >= tokens;
        boolean perDayAvailable = globalBuckets.perDay.getAvailableTokens() >= tokens;

        if (!(perMinuteAvailable && perHourAvailable && perDayAvailable)) {
            logger.warn("Global token limit exceeded. Attempted to consume {} tokens. " +
                            "Remaining: {}/min, {}/hour, {}/day",
                    tokens,
                    globalBuckets.perMinute.getAvailableTokens(),
                    globalBuckets.perHour.getAvailableTokens(),
                    globalBuckets.perDay.getAvailableTokens());
            return false;
        }

        // All buckets have capacity, now consume from all (consume phase)
        globalBuckets.perMinute.tryConsume(tokens);
        globalBuckets.perHour.tryConsume(tokens);
        globalBuckets.perDay.tryConsume(tokens);

        return true;
    }

    /**
     * Try to consume tokens for a specific IP address.
     *
     * @param ipAddress IP address
     * @param tokens    number of tokens to consume
     * @return true if tokens were consumed, false if limit exceeded
     */
    public boolean tryConsumeForIp(String ipAddress, int tokens) {
        IpTokenBuckets buckets = ipBucketCache.get(ipAddress, this::createIpBuckets);

        // First, check if ALL buckets can accommodate (probe phase)
        boolean perMinuteAvailable = buckets.perMinute.getAvailableTokens() >= tokens;
        boolean perHourAvailable = buckets.perHour.getAvailableTokens() >= tokens;
        boolean perDayAvailable = buckets.perDay.getAvailableTokens() >= tokens;

        if (!(perMinuteAvailable && perHourAvailable && perDayAvailable)) {
            logger.warn("Token limit exceeded for IP: {}. Attempted to consume {} tokens. " +
                            "Remaining: {}/min, {}/hour, {}/day",
                    ipAddress, tokens,
                    buckets.perMinute.getAvailableTokens(),
                    buckets.perHour.getAvailableTokens(),
                    buckets.perDay.getAvailableTokens());
            return false;
        }

        // All buckets have capacity, now consume from all (consume phase)
        buckets.perMinute.tryConsume(tokens);
        buckets.perHour.tryConsume(tokens);
        buckets.perDay.tryConsume(tokens);

        return true;
    }

    public TokenQuota getRemainingTokensForIp(String ipAddress) {
        IpTokenBuckets buckets = ipBucketCache.getIfPresent(ipAddress);
        if (buckets == null) {
            return new TokenQuota(
                    properties.getTokens().getIp().getPerMinute(),
                    properties.getTokens().getIp().getPerHour(),
                    properties.getTokens().getIp().getPerDay()
            );
        }

        return new TokenQuota(
                buckets.perMinute.getAvailableTokens(),
                buckets.perHour.getAvailableTokens(),
                buckets.perDay.getAvailableTokens()
        );
    }

    public TokenQuota getGlobalRemainingTokens() {
        return new TokenQuota(
                globalBuckets.perMinute.getAvailableTokens(),
                globalBuckets.perHour.getAvailableTokens(),
                globalBuckets.perDay.getAvailableTokens()
        );
    }

    private IpTokenBuckets createIpBuckets(String ipAddress) {
        logger.debug("Creating token buckets for IP: {}", ipAddress);
        return new IpTokenBuckets(
                createBucket(properties.getTokens().getIp().getPerMinute(), Duration.ofMinutes(1)),
                createBucket(properties.getTokens().getIp().getPerHour(), Duration.ofHours(1)),
                createBucket(properties.getTokens().getIp().getPerDay(), Duration.ofDays(1))
        );
    }

    private GlobalTokenBuckets createGlobalBuckets() {
        return new GlobalTokenBuckets(
                createBucket(properties.getTokens().getGlobal().getPerMinute(), Duration.ofMinutes(1)),
                createBucket(properties.getTokens().getGlobal().getPerHour(), Duration.ofHours(1)),
                createBucket(properties.getTokens().getGlobal().getPerDay(), Duration.ofDays(1))
        );
    }

    private Bucket createBucket(int capacity, Duration refillDuration) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillDuration)
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private record IpTokenBuckets(Bucket perMinute, Bucket perHour, Bucket perDay) { }
    private record GlobalTokenBuckets(Bucket perMinute, Bucket perHour, Bucket perDay) { }

    public record TokenQuota(long perMinute, long perHour, long perDay) {

        public long getMinimum() {
            return Math.min(perMinute, Math.min(perHour, perDay));
        }

        @Override
        public String toString() {
            return String.format("%d/min, %d/hour, %d/day", perMinute, perHour, perDay);
        }
    }
}
