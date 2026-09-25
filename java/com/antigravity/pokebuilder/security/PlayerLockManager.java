package com.antigravity.pokebuilder.security;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerLockManager {

    private static final Map<UUID, Object> LOCKS = new ConcurrentHashMap<>();

    private PlayerLockManager() {}

    public static Object get(UUID playerId) {
        return LOCKS.computeIfAbsent(playerId, k -> new Object());
    }

    public static void release(UUID playerId) {
        LOCKS.remove(playerId);
    }
}
