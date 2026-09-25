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
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class NatureSelectGui extends PaginatedGui<Nature> {

    private final UUID pokemonId;
    private List<Nature> cached;

    public NatureSelectGui(ServerPlayerEntity player, UUID pokemonId) {
        super(player, Text.literal("PokeBuilder — Nature").formatted(Formatting.DARK_AQUA));
        this.pokemonId = pokemonId;
    }

    @Override
    protected List<Nature> items() {
        if (cached != null) return cached;
        cached = new ArrayList<>();
        try {
            Collection<Nature> all = Natures.INSTANCE.all();
            cached.addAll(all);
        } catch (Throwable ignored) {}
        return cached;
    }

    @Override
    protected ItemStack renderItem(Nature nature) {
        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        boolean current = p != null && p.getNature().equals(nature);
        // Nature mints from Cobblemon — naming convention: <nature>_mint.
        Item icon = ModItems.natureMint(nature.getName().getPath());
        BigDecimal cost = PokeBuilder.get().config().prices.nature;

        ItemBuilder b = ItemBuilder.of(icon)
                .name(capitalize(nature.getName().getPath()) + " Mint", Formatting.AQUA, Formatting.BOLD);

        if (nature.getIncreasedStat() != null && nature.getDecreasedStat() != null
                && !nature.getIncreasedStat().equals(nature.getDecreasedStat())) {
            b.lore("+10% " + statName(nature.getIncreasedStat()), Formatting.GREEN);
            b.lore("-10% " + statName(nature.getDecreasedStat()), Formatting.RED);
        } else {
            b.lore("No stat changes", Formatting.GRAY);
        }
        b.lore("Cost: " + TextHelper.formatMoney(cost) + " " + TextHelper.currencyName(), Formatting.YELLOW);
        if (current) b.lore("[CURRENT]", Formatting.GREEN, Formatting.BOLD).glow(true);
        return b.build();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "—";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private static String statName(com.cobblemon.mod.common.api.pokemon.stats.Stat stat) {
        if (stat == null) return "";
        try {
            String id = stat.getShowdownId();
            if (id != null) return id.toLowerCase();
        } catch (Throwable ignored) {}
        try {
            return stat.getIdentifier().getPath().toLowerCase();
        } catch (Throwable ignored) {}
        return stat.toString().toLowerCase();
    }

    @Override
    protected void onItemClick(Nature nature) {
        if (!FeatureGate.enforce(FeatureGate.Feature.NATURE, player)) { close(); return; }
        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        if (p == null) { close(); return; }

        BigDecimal cost = PokeBuilder.get().config().prices.nature;
        String id = nature.getName().getPath();
        // Precheck runs inside TransactionService under the player lock —
        // catches the "already this nature" case and refunds before the
        // withdraw. The early-return above is redundant now but kept for
        // UX (skips the purchase sound on a harmless re-click).
        TransactionService.execute(player, pokemonId, FeatureGate.Feature.NATURE,
                "NATURE_SET", id, cost,
                pm -> !pm.getNature().equals(nature),
                pm -> PokemonModifier.setNature(pm, id));
        refresh();
    }

    @Override
    protected void onBack() {
        SoundHelper.back(player);
        new EditorGui(player, pokemonId).open();
    }

    @Override
    protected void onOpen() { SoundHelper.openMenu(player); }
}
