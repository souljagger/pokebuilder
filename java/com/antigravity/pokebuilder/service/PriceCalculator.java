package com.antigravity.pokebuilder.service;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.config.PokeBuilderConfig;
import com.antigravity.pokebuilder.util.PermissionHelper;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

// Resolves a player's effective price for a given base (sticker) cost.
//
// Pricing model:
//   resolvedMultiplier = first tier t in config.tiers.tiers (declaration
//                         order) where player holds
//                         `pokebuilder.tier.<t>`, or `tiers.default`
//   effectivePrice    = basePrice * resolvedMultiplier
//
// The resolution is done per-transaction, not cached per-session, so a
// LuckPerms grant/revoke takes effect on the very next purchase. The
// tier map lookup is O(n) over configured tiers which is effectively a
// handful of entries.
//
// Price is rounded HALF_UP to 2 decimal places before return — whole-
// currency servers end up with clean integers; fractional currencies
// get at most 2 decimals of precision.
public final class PriceCalculator {

    private PriceCalculator() {}

    public record Resolution(BigDecimal sticker,
                             BigDecimal effective,
                             String tierId,
                             BigDecimal multiplier) {}

    public static Resolution resolve(ServerPlayerEntity player, BigDecimal sticker) {
        if (sticker == null || sticker.signum() <= 0) {
            return new Resolution(
                    sticker == null ? BigDecimal.ZERO : sticker,
                    sticker == null ? BigDecimal.ZERO : sticker,
                    "default",
                    BigDecimal.ONE);
        }
        PokeBuilderConfig.Tiers cfg = PokeBuilder.get().config().tiers;
        BigDecimal multiplier = cfg.defaultMultiplier;
        String tierId = "default";

        if (player != null) {
            for (Map.Entry<String, BigDecimal> entry : cfg.tiers.entrySet()) {
                String id = entry.getKey();
                if (PermissionHelper.has(player, "pokebuilder.tier." + id, Integer.MAX_VALUE)) {
                    multiplier = entry.getValue();
                    tierId = id;
                    break;
                }
            }
        }

        BigDecimal effective = sticker.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        // Strip trailing zeros so whole numbers display cleanly in GUI lore.
        effective = effective.stripTrailingZeros();
        if (effective.scale() < 0) effective = effective.setScale(0, RoundingMode.UNNECESSARY);
        return new Resolution(sticker, effective, tierId, multiplier);
    }

    // Convenience short form — just the resolved price. Use
    // `resolve()` when the tier id / multiplier need to be audit-logged.
    public static BigDecimal price(ServerPlayerEntity player, BigDecimal sticker) {
        return resolve(player, sticker).effective();
    }
}
