package com.antigravity.pokebuilder.service;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.PokeBuilderConstants;
import com.antigravity.pokebuilder.economy.EconomyHandler;
import com.antigravity.pokebuilder.logging.AuditLogger;
import com.antigravity.pokebuilder.security.CooldownManager;
import com.antigravity.pokebuilder.security.PlayerLockManager;
import com.antigravity.pokebuilder.security.RateLimiter;
import com.antigravity.pokebuilder.util.PermissionHelper;
import com.antigravity.pokebuilder.util.SoundHelper;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class TransactionService {

    public enum Result { SUCCESS, NOT_ENOUGH_FUNDS, FAILED, COOLDOWN, RATE_LIMITED, INVALID_STATE, FEATURE_DISABLED, NO_OP }

    private TransactionService() {}

    // Backwards-compatible overload — callers without a feature gate fall
    // through to the no-feature path which only enforces permission/economy.
    public static Result execute(ServerPlayerEntity player, UUID pokemonId,
                                 String action, String detail, BigDecimal cost,
                                 Consumer<Pokemon> modification) {
        return execute(player, pokemonId, null, action, detail, cost, null, modification);
    }

    public static Result execute(ServerPlayerEntity player, UUID pokemonId,
                                 FeatureGate.Feature feature,
                                 String action, String detail, BigDecimal cost,
                                 Consumer<Pokemon> modification) {
        return execute(player, pokemonId, feature, action, detail, cost, null, modification);
    }

    // Primary path: takes an optional `precheck` that runs *before* any
    // economy call. If the precheck returns false the transaction exits
    // with Result.NO_OP — no withdraw, no audit record, no sound — and
    // the player gets a "already set" notification. This plugs the
    // max-IV-spam exploit: prior to v1.4.x, clicking "Set to 31" on a
    // stat that was already 31 silently charged the player because the
    // modification body was a no-op but the economy didn't know that.
    public static Result execute(ServerPlayerEntity player, UUID pokemonId,
                                 FeatureGate.Feature feature,
                                 String action, String detail, BigDecimal cost,
                                 Predicate<Pokemon> precheck,
                                 Consumer<Pokemon> modification) {
        UUID playerId = player.getUuid();

        if (!PokeBuilder.get().isEnabled() || !EconomyHandler.isReady()) {
            return Result.FAILED;
        }

        if (feature != null && !FeatureGate.enforce(feature, player)) {
            return Result.FEATURE_DISABLED;
        }

        // Per-feature blocked-modification check: the admin's
        // restrictions.blocked-modifications list wins over feature
        // gates. Gives server owners an escape hatch to disable a
        // specific feature without touching the features { ... } block.
        if (feature != null && !PokemonValidator.modificationAllowed(feature)) {
            player.sendMessage(PokeBuilder.get().config().messages.get("transaction.blocked_modification"), false);
            return Result.FEATURE_DISABLED;
        }

        if (!PermissionHelper.has(player, PokeBuilderConstants.PERM_USE, 0)) {
            return Result.FAILED;
        }

        // Bypass permissions are gated by a server-side master switch
        // (security.allow-bypass-permissions, default false). When the
        // gate is off, the bypass nodes are silently inert no matter
        // what the permission plugin says — defeats the wildcard-grant
        // accident that was hitting cora-on-SYLX repeatedly.
        boolean bypassEnabled = PokeBuilder.get().config().security.allowBypassPermissions;
        boolean bypassCooldown = bypassEnabled
                && PermissionHelper.hasExplicit(player, PokeBuilderConstants.PERM_BYPASS_COOLDOWN);
        if (!bypassCooldown && CooldownManager.isOnCooldown(playerId)) {
            return Result.COOLDOWN;
        }

        if (!RateLimiter.tryAcquire(playerId)) {
            AuditLogger.warn("Rate limit triggered for " + player.getName().getString());
            player.sendMessage(PokeBuilder.get().config().messages.get("transaction.rate_limited"), false);
            return Result.RATE_LIMITED;
        }

        // Same master gate as bypassCooldown above — see the comment
        // there for why this is config-driven, not just permission-driven.
        boolean bypassCost = bypassEnabled
                && PermissionHelper.hasExplicit(player, PokeBuilderConstants.PERM_BYPASS_COST);

        // Resolve the player's tier multiplier (VIP discounts etc.)
        // against the sticker price. Bypass still wins (free > discounted).
        PriceCalculator.Resolution priced = PriceCalculator.resolve(player, cost);
        BigDecimal effectiveCost = bypassCost ? BigDecimal.ZERO : priced.effective();

        synchronized (PlayerLockManager.get(playerId)) {
            Pokemon current = CobblemonBridge.findByUuid(player, pokemonId);
            if (current == null) {
                player.sendMessage(PokeBuilder.get().config().messages.get("transaction.pokemon_gone"), false);
                return Result.INVALID_STATE;
            }

            // No-op precheck: reject before withdrawing any currency if
            // the modification wouldn't actually change anything
            // (e.g. IV already 31, Tera type already Fire, nature
            // already Adamant). Runs inside the lock so the precheck
            // sees the same pokemon state the modification will.
            if (precheck != null && !precheck.test(current)) {
                SoundHelper.purchaseFailed(player);
                net.minecraft.text.Text msg = PokeBuilder.get().config().messages.get("transaction.already_set");
                if (msg != null) player.sendMessage(msg, false);
                return Result.NO_OP;
            }

            BigDecimal before = EconomyHandler.getBalance(playerId);
            if (!bypassCost && before.compareTo(effectiveCost) < 0) {
                SoundHelper.purchaseFailed(player);
                player.sendMessage(PokeBuilder.get().config().messages.get("transaction.not_enough_funds"), false);
                return Result.NOT_ENOUGH_FUNDS;
            }

            boolean withdrew = bypassCost || EconomyHandler.withdraw(playerId, effectiveCost);
            if (!withdrew) {
                SoundHelper.purchaseFailed(player);
                player.sendMessage(PokeBuilder.get().config().messages.get("transaction.failed"), false);
                AuditLogger.warn("Withdraw failed for " + player.getName().getString());
                return Result.FAILED;
            }

            try {
                modification.accept(current);
            } catch (Throwable t) {
                PokeBuilder.LOGGER.error("[PokeBuilder] Modification failed — issuing refund", t);
                if (!bypassCost) EconomyHandler.deposit(playerId, effectiveCost);
                player.sendMessage(PokeBuilder.get().config().messages.get("transaction.refunded"), false);
                AuditLogger.error("Modification failed for " + player.getName().getString()
                        + " action=" + action + " detail=" + detail);
                return Result.FAILED;
            }

            BigDecimal after = EconomyHandler.getBalance(playerId);
            // Audit logs both sticker and paid so VIP receipts are not
            // confusing (1000 sticker, 800 actually deducted, logged).
            String richDetail = buildAuditDetail(detail, priced, bypassCost);
            AuditLogger.transaction(playerId, player.getName().getString(),
                    current.getUuid(), current.getSpecies().getName(),
                    action, richDetail, effectiveCost, before, after, true);
            SoundHelper.purchaseSuccess(player);
            return Result.SUCCESS;
        }
    }

    private static String buildAuditDetail(String detail, PriceCalculator.Resolution priced, boolean bypass) {
        StringBuilder sb = new StringBuilder(detail == null ? "" : detail);
        if (bypass) {
            sb.append(" tier=bypass multiplier=0");
        } else if (!"default".equals(priced.tierId()) || priced.multiplier().compareTo(BigDecimal.ONE) != 0) {
            sb.append(" tier=").append(priced.tierId())
              .append(" multiplier=").append(priced.multiplier().toPlainString())
              .append(" sticker=").append(priced.sticker().toPlainString());
        }
        return sb.toString();
    }
}
