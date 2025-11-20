package io.jmix.ai.mcpdocs.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public class TimedExecutor {

    private final Logger logger;

    public TimedExecutor(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    public <T> TimedResult<T> execute(String operationName, Supplier<T> operation) {
        Instant startTime = Instant.now();

        try {
            T result = operation.get();
            Duration duration = Duration.between(startTime, Instant.now());

            logger.debug("Operation '{}' completed in {} ms",
                    operationName, duration.toMillis());

            return new TimedResult<>(result, duration, false, null);

        } catch (Exception e) {
            Duration duration = Duration.between(startTime, Instant.now());

            logger.error("Operation '{}' failed after {} ms: {}",
                    operationName, duration.toMillis(), e.getMessage(), e);

            return new TimedResult<>(null, duration, true, e);
        }
    }

    public record TimedResult<T>(
            T result,
            Duration duration,
            boolean hasError,
            Exception error) {

        @Override
        public T result() {
            if (hasError) {
                throw new RuntimeException("Operation failed", error);
            }
            return result;
        }

        public long getDurationMs() {
            return duration.toMillis();
        }
    }
}
