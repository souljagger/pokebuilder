package com.antigravity.pokebuilder.economy;

import com.antigravity.pokebuilder.PokeBuilder;
import net.impactdev.impactor.api.Impactor;
import net.impactdev.impactor.api.economy.EconomyService;
import net.impactdev.impactor.api.economy.accounts.Account;
import net.impactdev.impactor.api.economy.currency.Currency;
import net.impactdev.impactor.api.economy.transactions.EconomyTransaction;
import net.impactdev.impactor.api.economy.transactions.details.EconomyResultType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// Impactor-backed EconomyProvider. Keeps the 1.2.2 paranoid-withdraw
// verification (currency-identity check + balance-delta check + full
// result-type logging) — those are Impactor-specific concepts that don't
// belong on the generic EconomyProvider interface.
//
// Instances are created by EconomyHandler.init() and held by the facade.
// Recreate on /pokebuilder reload so a currency-id change is picked up
// without needing a full server restart.
public final class ImpactorEconomyProvider implements EconomyProvider {

    private final EconomyService service;
    private final Currency currency;
    private final boolean ready;

    private ImpactorEconomyProvider(EconomyService service, Currency currency) {
        this.service = service;
        this.currency = currency;
        this.ready = service != null && currency != null;
    }

    // Resolve a provider from the current PokeBuilder config. May log
    // warnings for misconfigured currency-id and fall back to the
    // server primary currency — same behavior the facade had before.
    public static ImpactorEconomyProvider create() {
        try {
            EconomyService svc = Impactor.instance().services().provide(EconomyService.class);
            String configured = PokeBuilder.get().config().economy.currencyId;

            boolean useConfigured = configured != null
                    && !configured.isBlank()
                    && configured.contains(":")
                    && !configured.equalsIgnoreCase("impactor:default");

            Currency resolved;
            if (useConfigured) {
                try {
                    String[] parts = configured.split(":", 2);
                    Key key = Key.key(parts[0], parts[1]);
                    Currency selected = svc.currencies().currency(key).orElse(null);
                    if (selected != null) {
                        resolved = selected;
                        PokeBuilder.LOGGER.info("[PokeBuilder] Economy hooked. Currency: {} (from config)",
                                resolved.key());
                    } else {
                        resolved = svc.currencies().primary();
                        PokeBuilder.LOGGER.warn("[PokeBuilder] Configured currency '{}' was not found in Impactor. "
                                        + "Falling back to the server primary currency: {}",
                                configured, resolved != null ? resolved.key() : "<none>");
                        logAvailableCurrencies(svc);
                    }
                } catch (Exception e) {
                    resolved = svc.currencies().primary();
                    PokeBuilder.LOGGER.warn("[PokeBuilder] Failed to parse currency-id '{}'. "
                                    + "Falling back to the server primary currency: {}",
                            configured, resolved != null ? resolved.key() : "<none>", e);
                }
            } else {
                resolved = svc.currencies().primary();
                PokeBuilder.LOGGER.info("[PokeBuilder] Economy hooked. Currency: {} (server primary)",
                        resolved != null ? resolved.key() : "<none>");
            }
            return new ImpactorEconomyProvider(svc, resolved);
        } catch (Throwable t) {
            PokeBuilder.LOGGER.error("[PokeBuilder] Failed to hook into Impactor Economy", t);
            return new ImpactorEconomyProvider(null, null);
        }
    }

    private static void logAvailableCurrencies(EconomyService svc) {
        try {
            StringBuilder sb = new StringBuilder("[PokeBuilder] Available Impactor currencies:");
            for (Currency c : svc.currencies().registered()) {
                sb.append("\n  - ").append(c.key()).append(c.primary() ? " (primary)" : "");
            }
            PokeBuilder.LOGGER.warn(sb.toString());
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    // Exposed for diagnostic callers that want the raw key; not part of
    // the generic EconomyProvider interface.
    public Currency currency() {
        return currency;
    }

    @Override
    public String currencyDisplay() {
        String override = PokeBuilder.get().config().economy.displayName;
        if (override != null && !override.isBlank()) return override;
        try {
            if (currency != null) {
                var plural = currency.plural();
                if (plural != null) return PlainTextComponentSerializer.plainText().serialize(plural);
            }
        } catch (Throwable ignored) {}
        return "coins";
    }

    @Override
    public BigDecimal getBalance(UUID playerId) {
        if (!ready) return BigDecimal.ZERO;
        try {
            Account account = service.account(currency, playerId).get(5, TimeUnit.SECONDS);
            return account == null ? BigDecimal.ZERO : account.balance();
        } catch (Exception e) {
            PokeBuilder.LOGGER.error("[PokeBuilder] Balance lookup failed for {}", playerId, e);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        if (amount == null) return false;
        if (amount.signum() == 0) return true;
        if (!ready) return false;
        try {
            Account account = service.account(currency, playerId).get(5, TimeUnit.SECONDS);
            if (account == null) {
                PokeBuilder.LOGGER.warn("[PokeBuilder] Account lookup returned null for player={} currency={}",
                        playerId, currency.key());
                return false;
            }
            verifyCurrency(account);

            BigDecimal before = account.balance();
            EconomyTransaction tx = account.withdraw(amount);
            BigDecimal after = account.balance();

            return verifyTransaction(tx, before, after, amount, true, playerId);
        } catch (Exception e) {
            PokeBuilder.LOGGER.error("[PokeBuilder] Withdraw failed for player={} amount={} currency={}",
                    playerId, amount, currency != null ? currency.key() : "<none>", e);
            return false;
        }
    }

    @Override
    public boolean deposit(UUID playerId, BigDecimal amount) {
        if (amount == null) return false;
        if (amount.signum() == 0) return true;
        if (!ready) return false;
        try {
            Account account = service.account(currency, playerId).get(5, TimeUnit.SECONDS);
            if (account == null) {
                PokeBuilder.LOGGER.warn("[PokeBuilder] Account lookup returned null for player={} currency={}",
                        playerId, currency.key());
                return false;
            }
            verifyCurrency(account);

            BigDecimal before = account.balance();
            EconomyTransaction tx = account.deposit(amount);
            BigDecimal after = account.balance();

            return verifyTransaction(tx, before, after, amount, false, playerId);
        } catch (Exception e) {
            PokeBuilder.LOGGER.error("[PokeBuilder] Deposit failed for player={} amount={} currency={}",
                    playerId, amount, currency != null ? currency.key() : "<none>", e);
            return false;
        }
    }

    private void verifyCurrency(Account account) {
        try {
            Currency acctCur = account.currency();
            if (acctCur != null && currency != null && !acctCur.key().equals(currency.key())) {
                PokeBuilder.LOGGER.warn("[PokeBuilder] Account currency mismatch! expected={} got={}. "
                                + "Impactor may be routing requests to the wrong account.",
                        currency.key(), acctCur.key());
            }
        } catch (Throwable ignored) {}
    }

    private boolean verifyTransaction(EconomyTransaction tx, BigDecimal before, BigDecimal after,
                                      BigDecimal amount, boolean isWithdraw, UUID playerId) {
        if (tx == null) {
            PokeBuilder.LOGGER.warn("[PokeBuilder] {} returned null transaction for player={}",
                    isWithdraw ? "Withdraw" : "Deposit", playerId);
            return false;
        }
        EconomyResultType result = tx.result();
        boolean ok = result == EconomyResultType.SUCCESS;
        BigDecimal actualDelta = isWithdraw ? before.subtract(after) : after.subtract(before);

        if (!ok) {
            PokeBuilder.LOGGER.warn("[PokeBuilder] {} did not succeed: result={} player={} currency={} "
                            + "amount={} before={} after={} delta={}. Check Impactor events / restrictions.",
                    isWithdraw ? "Withdraw" : "Deposit", result, playerId,
                    currency != null ? currency.key() : "<none>",
                    amount, before, after, actualDelta);
            return false;
        }
        if (actualDelta.compareTo(amount) != 0) {
            PokeBuilder.LOGGER.warn("[PokeBuilder] {} reported SUCCESS but balance delta mismatches! "
                            + "player={} currency={} expected-amount={} before={} after={} actual-delta={}. "
                            + "The account may be virtual, cached, or intercepted by another mod.",
                    isWithdraw ? "Withdraw" : "Deposit", playerId,
                    currency != null ? currency.key() : "<none>",
                    amount, before, after, actualDelta);
        }
        return true;
    }

    @Override
    public List<String> dumpDebug(UUID playerId) {
        List<String> out = new ArrayList<>();
        out.add("backend = Impactor");
        out.add("ready = " + ready);
        out.add("service = " + (service != null ? service.getClass().getName() : "<null>"));
        out.add("currency = " + (currency != null ? currency.key() : "<null>"));
        if (currency != null) {
            try {
                out.add("currency.plural = " + PlainTextComponentSerializer.plainText().serialize(currency.plural()));
                out.add("currency.singular = " + PlainTextComponentSerializer.plainText().serialize(currency.singular()));
                out.add("currency.decimals = " + currency.decimals());
                out.add("currency.default-balance = " + currency.defaultAccountBalance());
                out.add("currency.primary = " + currency.primary());
            } catch (Throwable ignored) {}
        }
        if (service != null) {
            try {
                out.add("all currencies:");
                for (Currency c : service.currencies().registered()) {
                    String note = c.primary() ? " (primary)" : "";
                    out.add("  - " + c.key() + note);
                }
            } catch (Throwable t) {
                out.add("  <could not list currencies: " + t + ">");
            }
        }
        if (playerId != null && ready) {
            try {
                Account account = service.account(currency, playerId).get(5, TimeUnit.SECONDS);
                if (account != null) {
                    out.add("player = " + playerId);
                    out.add("account.currency = " + (account.currency() != null ? account.currency().key() : "<null>"));
                    out.add("account.virtual = " + account.virtual());
                    out.add("account.balance = " + account.balance());
                } else {
                    out.add("account = <null>");
                }
            } catch (Throwable t) {
                out.add("account lookup failed: " + t);
            }
        }
        return out;
    }
}
