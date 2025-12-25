package io.jmix.ai.mcpdocs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private IpLimit ip = new IpLimit();
    private GlobalLimit global = new GlobalLimit();
    private Cache cache = new Cache();
    private TokenLimit tokens = new TokenLimit();
    private InputValidation input = new InputValidation();

    public IpLimit getIp() {
        return ip;
    }

    public void setIp(IpLimit ip) {
        this.ip = ip;
    }

    public GlobalLimit getGlobal() {
        return global;
    }

    public void setGlobal(GlobalLimit global) {
        this.global = global;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public TokenLimit getTokens() {
        return tokens;
    }

    public void setTokens(TokenLimit tokens) {
        this.tokens = tokens;
    }

    public InputValidation getInput() {
        return input;
    }

    public void setInput(InputValidation input) {
        this.input = input;
    }

    public static class IpLimit {
        private int capacity = 30;
        private int refillTokens = 30;
        private Duration refillDuration = Duration.ofHours(1);

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(int refillTokens) {
            this.refillTokens = refillTokens;
        }

        public Duration getRefillDuration() {
            return refillDuration;
        }

        public void setRefillDuration(Duration refillDuration) {
            this.refillDuration = refillDuration;
        }
    }

    public static class GlobalLimit {
        private int capacity = 200;
        private int refillTokens = 200;
        private Duration refillDuration = Duration.ofMinutes(1);

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(int refillTokens) {
            this.refillTokens = refillTokens;
        }

        public Duration getRefillDuration() {
            return refillDuration;
        }

        public void setRefillDuration(Duration refillDuration) {
            this.refillDuration = refillDuration;
        }
    }

    public static class Cache {
        private int maxSize = 10000;
        private Duration expireDuration = Duration.ofHours(1);

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public Duration getExpireDuration() {
            return expireDuration;
        }

        public void setExpireDuration(Duration expireDuration) {
            this.expireDuration = expireDuration;
        }
    }

    public static class TokenLimit {
        private IpTokenLimit ip = new IpTokenLimit();
        private GlobalTokenLimit global = new GlobalTokenLimit();

        public IpTokenLimit getIp() {
            return ip;
        }

        public void setIp(IpTokenLimit ip) {
            this.ip = ip;
        }

        public GlobalTokenLimit getGlobal() {
            return global;
        }

        public void setGlobal(GlobalTokenLimit global) {
            this.global = global;
        }

        public static class IpTokenLimit {
            private int perMinute = 50;
            private int perHour = 1000;
            private int perDay = 10000;

            public int getPerMinute() {
                return perMinute;
            }

            public void setPerMinute(int perMinute) {
                this.perMinute = perMinute;
            }

            public int getPerHour() {
                return perHour;
            }

            public void setPerHour(int perHour) {
                this.perHour = perHour;
            }

            public int getPerDay() {
                return perDay;
            }

            public void setPerDay(int perDay) {
                this.perDay = perDay;
            }
        }

        public static class GlobalTokenLimit {
            private int perMinute = 500;
            private int perHour = 10000;
            private int perDay = 100000;

            public int getPerMinute() {
                return perMinute;
            }

            public void setPerMinute(int perMinute) {
                this.perMinute = perMinute;
            }

            public int getPerHour() {
                return perHour;
            }

            public void setPerHour(int perHour) {
                this.perHour = perHour;
            }

            public int getPerDay() {
                return perDay;
            }

            public void setPerDay(int perDay) {
                this.perDay = perDay;
            }
        }
    }

    public static class InputValidation {
        private int maxQueryLength = 2000;
        private int maxEstimatedInputTokens = 500;

        public int getMaxQueryLength() {
            return maxQueryLength;
        }

        public void setMaxQueryLength(int maxQueryLength) {
            this.maxQueryLength = maxQueryLength;
        }

        public int getMaxEstimatedInputTokens() {
            return maxEstimatedInputTokens;
        }

        public void setMaxEstimatedInputTokens(int maxEstimatedInputTokens) {
            this.maxEstimatedInputTokens = maxEstimatedInputTokens;
        }
    }
}
