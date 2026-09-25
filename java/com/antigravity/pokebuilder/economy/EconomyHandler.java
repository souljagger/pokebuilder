package com.antigravity.pokebuilder.economy;

import net.impactdev.impactor.api.economy.currency.Currency;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// Thin facade over the active EconomyProvider. This class preserves the
// 1.0.0 → 1.2.2 call surface (static methods used across TransactionService,
// GUIs, and commands) while the actual backend work happens inside the
// provider implementation.
//
// init() replaces the provider on /pokebuilder reload so a currency-id
// change is picked up live without needing a full server restart.
public final class EconomyHandler {

    private static volatile EconomyProvider provider;

    private EconomyHandler() {}

    public static boolean init() {
        provider = ImpactorEconomyProvider.create();
        return provider.isReady();
    }

    public static EconomyProvider provider() {
        return provider;
    }

    public static boolean isReady() {
        return provider != null && provider.isReady();
    }

    public static BigDecimal getBalance(UUID playerId) {
        return provider == null ? BigDecimal.ZERO : provider.getBalance(playerId);
    }

    public static boolean withdraw(UUID playerId, BigDecimal amount) {
        return provider != null && provider.withdraw(playerId, amount);
    }

    public static boolean deposit(UUID playerId, BigDecimal amount) {
        return provider != null && provider.deposit(playerId, amount);
    }

    public static List<String> dumpDebug(UUID playerId) {
        return provider == null ? Collections.singletonList("provider = <null>")
                : provider.dumpDebug(playerId);
    }

    // Back-compat shim. The Impactor-specific currency() accessor used to
    // live here and is still referenced by PokeBuilderCommand to echo the
    // current currency key after reload. Returns null if the active
    // provider isn't Impactor-backed.
    public static Currency currency() {
        if (provider instanceof ImpactorEconomyProvider imp) return imp.currency();
        return null;
    }
}
