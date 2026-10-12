package com.stotram.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Slows password guessing: after {@value #MAX_FAILURES} wrong logins from one client, that
 * client is refused for the rest of a {@link #WINDOW} window. In-memory on purpose — there
 * is one server and one admin; a restart clearing the counters is acceptable.
 *
 * <p>Keyed by client address, not by email, so a stranger can't lock the real admin out by
 * failing on purpose with the admin's email.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int PRUNE_ABOVE = 10_000;

    private record Attempts(int failures, Instant windowStart) {
        boolean expired(Instant now) {
            return windowStart.plus(WINDOW).isBefore(now);
        }
    }

    private final Map<String, Attempts> byClient = new ConcurrentHashMap<>();

    public boolean isBlocked(String client) {
        Attempts attempts = byClient.get(client);
        return attempts != null && attempts.failures() >= MAX_FAILURES && !attempts.expired(Instant.now());
    }

    public void recordFailure(String client) {
        Instant now = Instant.now();
        byClient.compute(client, (key, attempts) -> attempts == null || attempts.expired(now)
                ? new Attempts(1, now)
                : new Attempts(attempts.failures() + 1, attempts.windowStart()));
        if (byClient.size() > PRUNE_ABOVE) {
            byClient.values().removeIf(attempts -> attempts.expired(now));
        }
    }

    public void recordSuccess(String client) {
        byClient.remove(client);
    }
}
