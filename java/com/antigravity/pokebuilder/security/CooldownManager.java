package com.antigravity.pokebuilder.security;

import com.antigravity.pokebuilder.PokeBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownManager {

    private static final Map<UUID, Long> LAST_ACTION = new ConcurrentHashMap<>();

    private CooldownManager() {}

    public static boolean isOnCooldown(UUID playerId) {
        long now = System.currentTimeMillis();
        long cooldownMs = PokeBuilder.get().config().security.clickCooldownMs;
        Long last = LAST_ACTION.get(playerId);
        if (last != null && (now - last) < cooldownMs) {
            return true;
        }
        LAST_ACTION.put(playerId, now);
        return false;
    }

    public static void clear(UUID playerId) {
        LAST_ACTION.remove(playerId);
    }
}
