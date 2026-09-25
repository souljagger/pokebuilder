package com.antigravity.pokebuilder.gui.base;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiManager {

    private static final Map<UUID, BaseGui> OPEN = new ConcurrentHashMap<>();

    private GuiManager() {}

    public static boolean isOpen(UUID playerId) {
        return OPEN.containsKey(playerId);
    }

    public static BaseGui current(UUID playerId) {
        return OPEN.get(playerId);
    }

    public static void register(ServerPlayerEntity player, BaseGui gui) {
        OPEN.put(player.getUuid(), gui);
    }

    public static void unregister(UUID playerId) {
        OPEN.remove(playerId);
    }

    public static void closeAll() {
        OPEN.values().forEach(BaseGui::forceClose);
        OPEN.clear();
    }
}
