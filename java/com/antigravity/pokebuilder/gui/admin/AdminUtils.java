package com.antigravity.pokebuilder.gui.admin;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.PokeBuilderConstants;
import com.antigravity.pokebuilder.logging.AuditLogger;
import com.antigravity.pokebuilder.util.PermissionHelper;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;

public final class AdminUtils {

    private AdminUtils() {}

    public static boolean isAdmin(ServerPlayerEntity player) {
        return PermissionHelper.has(player, PokeBuilderConstants.PERM_ADMIN_GUI, 4);
    }

    public static boolean isAdminReload(ServerPlayerEntity player) {
        return PermissionHelper.has(player, PokeBuilderConstants.PERM_ADMIN_RELOAD, 4);
    }

    public static int stepFor(int button, boolean shift, int small, int medium, int big) {
        if (button == 1) return -stepAmount(shift, small, medium, big);
        return stepAmount(shift, small, medium, big);
    }

    private static int stepAmount(boolean shift, int small, int medium, int big) {
        return shift ? big : medium;
    }

    public static BigDecimal clampPrice(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        if (value.signum() < 0) return BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.valueOf(1_000_000_000L)) > 0) return BigDecimal.valueOf(1_000_000_000L);
        return value;
    }

    public static long clampLong(long v, long lo, long hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static float clampFloat(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static void logAdmin(ServerPlayerEntity admin, String field, String oldVal, String newVal) {
        String msg = String.format("ADMIN_CHANGE player=%s field=%s old=%s new=%s",
                admin.getName().getString(), field, oldVal, newVal);
        AuditLogger.warn(msg);
        PokeBuilder.get().markDirty();
    }
}
