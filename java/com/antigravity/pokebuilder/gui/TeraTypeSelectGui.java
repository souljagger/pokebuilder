package com.antigravity.pokebuilder.gui;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.gui.base.PaginatedGui;
import com.antigravity.pokebuilder.service.CobblemonBridge;
import com.antigravity.pokebuilder.service.FeatureGate;
import com.antigravity.pokebuilder.service.PokemonModifier;
import com.antigravity.pokebuilder.service.TransactionService;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.ModItems;
import com.antigravity.pokebuilder.util.SoundHelper;
import com.antigravity.pokebuilder.util.TextHelper;
import com.cobblemon.mod.common.api.types.tera.TeraType;
import com.cobblemon.mod.common.api.types.tera.TeraTypes;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeraTypeSelectGui extends PaginatedGui<TeraType> {

    private final UUID pokemonId;
    private List<TeraType> cached;

    public TeraTypeSelectGui(ServerPlayerEntity player, UUID pokemonId) {
        super(player, Text.literal("PokeBuilder — Tera Type").formatted(Formatting.LIGHT_PURPLE));
        this.pokemonId = pokemonId;
    }

    @Override
    protected List<TeraType> items() {
        if (cached != null) return cached;
        cached = new ArrayList<>();
        try {
            for (TeraType t : TeraTypes.INSTANCE) cached.add(t);
        } catch (Throwable ignored) {}
        return cached;
    }

    @Override
    protected ItemStack renderItem(TeraType type) {
        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        boolean current = false;
        try {
            current = p != null && p.getTeraType() != null
                    && p.getTeraType().getId().equals(type.getId());
        } catch (Throwable ignored) {}
        BigDecimal cost = PokeBuilder.get().config().prices.teraType;
        String name = type.getName();

        ItemBuilder b = ItemBuilder.of(ModItems.teraShard(name))
                .name(prettyName(name), colorForType(name), Formatting.BOLD)
                .lore("Tera Shard: " + prettyName(name), Formatting.GRAY);

        if (current) {
            b.lore("[CURRENT]", Formatting.GREEN, Formatting.BOLD).glow(true);
        } else {
            b.lore("Cost: " + TextHelper.formatMoney(cost) + " " + TextHelper.currencyName(), Formatting.YELLOW);
            b.lore("Click to re-type", Formatting.GRAY);
        }
        return b.build();
    }

    private static String prettyName(String raw) {
        if (raw == null || raw.isEmpty()) return "—";
        String lower = raw.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static Formatting colorForType(String name) {
        return switch (name == null ? "" : name.toLowerCase()) {
            case "fire" -> Formatting.RED;
            case "water" -> Formatting.BLUE;
            case "grass" -> Formatting.GREEN;
            case "electric" -> Formatting.YELLOW;
            case "ice" -> Formatting.AQUA;
            case "fighting" -> Formatting.DARK_RED;
            case "poison" -> Formatting.DARK_PURPLE;
            case "ground" -> Formatting.GOLD;
            case "flying" -> Formatting.WHITE;
            case "psychic" -> Formatting.LIGHT_PURPLE;
            case "bug" -> Formatting.DARK_GREEN;
            case "rock" -> Formatting.DARK_GRAY;
            case "ghost" -> Formatting.DARK_PURPLE;
            case "dragon" -> Formatting.DARK_BLUE;
            case "dark" -> Formatting.BLACK;
            case "steel" -> Formatting.GRAY;
            case "fairy" -> Formatting.LIGHT_PURPLE;
            case "stellar" -> Formatting.GOLD;
            default -> Formatting.WHITE;
        };
    }

    @Override
    protected void onItemClick(TeraType type) {
        if (!FeatureGate.enforce(FeatureGate.Feature.TERA_TYPE, player)) { close(); return; }
        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        if (p == null) { close(); return; }
        BigDecimal cost = PokeBuilder.get().config().prices.teraType;
        // Resolve to a canonical TeraType instance by id before handing
        // the setter an object. The iterated TeraTypes.INSTANCE yields
        // the registered types, but we do one more lookup-by-id hop
        // here to defend against the historical silent-failure mode
        // where the setter received a non-canonical wrapper and stored
        // nothing (money withdrawn, tera visually unchanged).
        final TeraType resolved = resolveCanonical(type);
        if (resolved == null) {
            com.antigravity.pokebuilder.PokeBuilder.LOGGER.warn(
                    "[PokeBuilder] Tera type '{}' resolved to null — aborting transaction for {}",
                    safeName(type), player.getName().getString());
            return;
        }
        TransactionService.execute(player, pokemonId, FeatureGate.Feature.TERA_TYPE,
                "TERA_TYPE", resolved.getName(), cost,
                // Precheck: no-op if already this type. Previously lived
                // in the click handler as an unconditional return, which
                // meant a bogus/stale match silently swallowed the click
                // with no feedback. Now the player gets a proper
                // "already set" message.
                pm -> {
                    try {
                        return pm.getTeraType() == null
                                || !sameTera(pm.getTeraType(), resolved);
                    } catch (Throwable t) { return true; }
                },
                pm -> {
                    String before = safeTeraName(pm);
                    PokemonModifier.setTeraType(pm, resolved);
                    String after = safeTeraName(pm);
                    com.antigravity.pokebuilder.PokeBuilder.LOGGER.info(
                            "[PokeBuilder] TERA_TYPE {} -> {} (requested={}) for {}",
                            before, after, resolved.getName(), player.getName().getString());
                    if (!sameTera(pm.getTeraType(), resolved)) {
                        // Defensive: if the setter didn't persist, throw
                        // so TransactionService refunds. Better to fail
                        // loudly than silently consume the player's money.
                        throw new IllegalStateException(
                                "setTeraType did not persist: still " + after);
                    }
                });
        refresh();
    }

    // Canonical lookup — prefers TeraTypes.getByName(name), falls back
    // to the passed-in instance if lookup fails.
    private static TeraType resolveCanonical(TeraType t) {
        if (t == null) return null;
        try {
            TeraType byName = TeraTypes.INSTANCE.getByName(t.getName());
            if (byName != null) return byName;
        } catch (Throwable ignored) {}
        return t;
    }

    private static boolean sameTera(TeraType a, TeraType b) {
        if (a == null || b == null) return a == b;
        try { return a.getId().equals(b.getId()); }
        catch (Throwable t) { return false; }
    }

    private static String safeName(TeraType t) {
        if (t == null) return "<null>";
        try { return t.getName(); } catch (Throwable e) { return "<err>"; }
    }

    private static String safeTeraName(Pokemon p) {
        if (p == null) return "<no-pokemon>";
        try {
            TeraType t = p.getTeraType();
            return t == null ? "<none>" : t.getName();
        } catch (Throwable e) { return "<err>"; }
    }

    @Override
    protected void onBack() {
        SoundHelper.back(player);
        new EditorGui(player, pokemonId).open();
    }

    @Override
    protected void onOpen() { SoundHelper.openMenu(player); }
}
