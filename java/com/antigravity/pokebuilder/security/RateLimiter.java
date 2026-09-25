package com.antigravity.pokebuilder.security;

import com.antigravity.pokebuilder.PokeBuilder;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {

    private static final long WINDOW_MS = 60_000L;
    private static final Map<UUID, Deque<Long>> HISTORY = new ConcurrentHashMap<>();

    private RateLimiter() {}

    public static synchronized boolean tryAcquire(UUID playerId) {
        int limit = PokeBuilder.get().config().security.maxModificationsPerMinute;
        long now = System.currentTimeMillis();
        Deque<Long> deque = HISTORY.computeIfAbsent(playerId, k -> new ArrayDeque<>());
        while (!deque.isEmpty() && (now - deque.peekFirst()) > WINDOW_MS) {
            deque.pollFirst();
        }
        if (deque.size() >= limit) {
            return false;
        }
        deque.addLast(now);
        return true;
    }

    public static void clear(UUID playerId) {
        HISTORY.remove(playerId);
    }
}
