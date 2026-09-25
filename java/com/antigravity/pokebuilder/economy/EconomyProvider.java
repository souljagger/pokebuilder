package com.antigravity.pokebuilder.economy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Narrow abstraction over an economy backend. The only bundled
// implementation today is ImpactorEconomyProvider, but the interface is
// deliberately kept simple so future backends (Vault, CMI, custom) can
// ship without touching the transaction layer or the GUIs.
//
// Any verification logic beyond the simple contract below (e.g. Impactor's
// balance-delta and result-type sanity checks) belongs inside the concrete
// implementation — those concepts don't exist on all economies.
public interface EconomyProvider {

    boolean isReady();

    // Short human label used in GUI tooltips. Falls through to the
    // configured display-name when set, else the backend's own label.
    String currencyDisplay();

    BigDecimal getBalance(UUID playerId);

    boolean withdraw(UUID playerId, BigDecimal amount);

    boolean deposit(UUID playerId, BigDecimal amount);

    // Multi-line diagnostic output for /pokebuilder debug. The
    // requester UUID may be null (console invocation) — implementations
    // should skip per-player sections in that case.
    List<String> dumpDebug(UUID requesterId);
}
