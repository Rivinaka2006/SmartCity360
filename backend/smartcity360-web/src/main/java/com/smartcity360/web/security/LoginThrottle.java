package com.smartcity360.web.security;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@ApplicationScoped
public class LoginThrottle {
    private static final int LIMIT = 5, MAX_KEYS = 10_000;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> attempts = new ConcurrentHashMap<>();

    public boolean allowed(String key) {
        cleanup();
        ArrayDeque<Instant> q = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            expire(q);
            return q.size() < LIMIT;
        }
    }

    public void failed(String key) {
        if (attempts.size() >= MAX_KEYS && !attempts.containsKey(key)) cleanupOldest();
        ArrayDeque<Instant> q = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            expire(q);
            if (q.size() < LIMIT) q.addLast(Instant.now());
        }
    }

    public void succeeded(String key) {
        attempts.remove(key);
    }

    private void expire(ArrayDeque<Instant> q) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.removeFirst();
    }

    private void cleanup() {
        attempts.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                expire(e.getValue());
                return e.getValue().isEmpty();
            }
        });
    }

    private void cleanupOldest() {
        attempts.keySet().stream().findAny().ifPresent(attempts::remove);
    }
}
