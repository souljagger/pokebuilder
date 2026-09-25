package com.antigravity.pokebuilder.util;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public final class PermissionHelper {

    private PermissionHelper() {}

    public static boolean has(ServerCommandSource src, String node, int fallbackOpLevel) {
        try {
            return Permissions.check(src, node, fallbackOpLevel);
        } catch (Throwable t) {
            return src.hasPermissionLevel(fallbackOpLevel);
        }
    }

    public static boolean has(ServerPlayerEntity player, String node, int fallbackOpLevel) {
        try {
            return Permissions.check(player, node, fallbackOpLevel);
        } catch (Throwable t) {
            return player.hasPermissionLevel(fallbackOpLevel);
        }
    }

    // Bypass / opt-in nodes: default must be "nobody" unless a permission
    // provider explicitly grants it. Do NOT fall back to OP level here —
    // otherwise every /op'd player silently bypasses costs and cooldowns.
    public static boolean hasExplicit(ServerPlayerEntity player, String node) {
        try {
            return Permissions.check(player, node, false);
        } catch (Throwable t) {
            return false;
        }
    }
}
