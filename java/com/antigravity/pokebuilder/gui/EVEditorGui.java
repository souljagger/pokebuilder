package com.antigravity.pokebuilder.gui;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.config.PokeBuilderConfig;
import com.antigravity.pokebuilder.gui.base.BaseGui;
import com.antigravity.pokebuilder.service.CobblemonBridge;
import com.antigravity.pokebuilder.service.FeatureGate;
import com.antigravity.pokebuilder.service.PokemonModifier;
import com.antigravity.pokebuilder.service.TransactionService;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.ModItems;
import com.antigravity.pokebuilder.util.SoundHelper;
import com.antigravity.pokebuilder.util.TextHelper;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.UUID;

public class EVEditorGui extends BaseGui {

    private static final int[] HEADER_SLOTS = {10, 11, 12, 13, 14, 15};
    private static final int[] SET252_SLOTS = {19, 20, 21, 22, 23, 24};
    private static final int[] SET0_SLOTS = {28, 29, 30, 31, 32, 33};
    private static final int TOTAL_SLOT = 40;
    private static final int BACK_SLOT = 45;
    private static final int RESET_ALL_SLOT = 52;
    private static final int CLOSE_SLOT = 53;

    private final UUID pokemonId;

    public EVEditorGui(ServerPlayerEntity player, UUID pokemonId) {
        super(player, 6, Text.literal("\u2756 PokeBuilder \u00b7 EVs \u2756").formatted(Formatting.DARK_AQUA, Formatting.BOLD));
        this.pokemonId = pokemonId;
    }

    @Override
    protected void build() {
        fillBorders(PokeBuilder.get().config().looks.borderColor);
        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        if (p == null) { close(); return; }
        PokeBuilderConfig cfg = PokeBuilder.get().config();

        Stats[] stats = CobblemonBridge.allStats();
        int total = 0;
        for (int i = 0; i < 6; i++) {
            int current = p.getEvs().get(stats[i]);
            total += current;
        }
        for (int i = 0; i < 6; i++) {
            int current = p.getEvs().get(stats[i]);
            Item icon = ModItems.vitaminFor(i);
            boolean canAdd = current < 252 && total < 510;
            ItemBuilder hdr = ItemBuilder.of(icon)
                    .name(CobblemonBridge.statLabel(stats[i]) + ": " + current + " / 252",
                            colorForEv(current), Formatting.BOLD)
                    .lore("Effort value", Formatting.DARK_GRAY);
            if (canAdd) {
                hdr.lore("Click: +10 EVs", Formatting.AQUA);
                hdr.lore("Cost: " + TextHelper.formatMoney(cfg.prices.ev.add10) + " " + TextHelper.currencyName(), Formatting.YELLOW);
            } else {
                hdr.lore("Maxed out", Formatting.GREEN);
            }
            safeSet(HEADER_SLOTS[i], hdr.glow(current == 252).build());

            boolean capped = current >= 252;
            safeSet(SET252_SLOTS[i], ItemBuilder.of(capped ? Items.LIME_STAINED_GLASS_PANE : Items.LIME_DYE)
                    .name("Set to 252", capped ? Formatting.DARK_GRAY : Formatting.GREEN, Formatting.BOLD)
                    .lore(capped ? "Already at 252" : "Cost: " + TextHelper.formatMoney(cfg.prices.ev.setSingle252) + " " + TextHelper.currencyName(),
                            capped ? Formatting.GREEN : Formatting.YELLOW)
                    .glow(capped).build());

            boolean zeroed = current <= 0;
            safeSet(SET0_SLOTS[i], ItemBuilder.of(zeroed ? Items.RED_STAINED_GLASS_PANE : Items.RED_DYE)
                    .name("Set to 0", zeroed ? Formatting.DARK_GRAY : Formatting.RED, Formatting.BOLD)
                    .lore(zeroed ? "Already zero" : "Cost: " + TextHelper.formatMoney(cfg.prices.ev.setSingle0) + " " + TextHelper.currencyName(),
                            zeroed ? Formatting.GREEN : Formatting.YELLOW)
                    .build());
        }

        safeSet(TOTAL_SLOT, ItemBuilder.of(Items.BOOK)
                .name("Total EVs: " + total + " / 510", Formatting.GOLD, Formatting.BOLD).build());

        safeSet(BACK_SLOT, ItemBuilder.of(Items.ARROW).name("Back", Formatting.YELLOW).build());
        if (PokeBuilder.get().config().features.evResetAll) {
            safeSet(RESET_ALL_SLOT, ItemBuilder.of(Items.EMERALD_BLOCK)
                    .name("Reset All EVs", Formatting.GREEN, Formatting.BOLD)
                    .lore("Cost: " + TextHelper.formatMoney(cfg.prices.ev.resetAll) + " " + TextHelper.currencyName(), Formatting.YELLOW)
                    .build());
        }
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED).build());
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (slot == BACK_SLOT) { SoundHelper.back(player); new EditorGui(player, pokemonId).open(); return; }
        if (slot == CLOSE_SLOT) { close(); return; }
        if (!FeatureGate.enforce(FeatureGate.Feature.EV_EDITOR, player)) { close(); return; }
        if (slot == RESET_ALL_SLOT) {
            if (!FeatureGate.enforce(FeatureGate.Feature.EV_RESET_ALL, player)) return;
            BigDecimal cost = PokeBuilder.get().config().prices.ev.resetAll;
            TransactionService.execute(player, pokemonId, FeatureGate.Feature.EV_RESET_ALL,
                    "EV_RESET_ALL", "all0", cost,
                    // No-op when every EV is already zero.
                    pm -> {
                        for (Stats s : CobblemonBridge.allStats())
                            if (pm.getEvs().get(s) > 0) return true;
                        return false;
                    },
                    PokemonModifier::resetAllEvs);
            refresh();
            return;
        }

        Stats[] stats = CobblemonBridge.allStats();
        for (int i = 0; i < 6; i++) {
            if (HEADER_SLOTS[i] == slot) {
                Pokemon pokemon = CobblemonBridge.findByUuid(player, pokemonId);
                if (pokemon == null) { close(); return; }
                int current = pokemon.getEvs().get(stats[i]);
                int evTotal = PokemonModifier.totalEvs(pokemon);
                if (current >= 252 || evTotal >= 510) {
                    player.sendMessage(net.minecraft.text.Text.literal("EVs already at maximum.")
                            .formatted(Formatting.RED), false);
                    SoundHelper.purchaseFailed(player);
                    return;
                }
                final Stats s = stats[i];
                BigDecimal cost = PokeBuilder.get().config().prices.ev.add10;
                TransactionService.execute(player, pokemonId, FeatureGate.Feature.EV_EDITOR,
                        "EV_ADD_10", CobblemonBridge.statLabel(s), cost,
                        pm -> PokemonModifier.addEv(pm, s, 10));
                refresh();
                return;
            }
            if (SET252_SLOTS[i] == slot) {
                final Stats s = stats[i];
                BigDecimal cost = PokeBuilder.get().config().prices.ev.setSingle252;
                TransactionService.execute(player, pokemonId, FeatureGate.Feature.EV_EDITOR,
                        "EV_SET_252", CobblemonBridge.statLabel(s), cost,
                        p -> p.getEvs().get(s) < 252,
                        p -> PokemonModifier.setEv(p, s, 252));
                refresh();
                return;
            }
            if (SET0_SLOTS[i] == slot) {
                final Stats s = stats[i];
                BigDecimal cost = PokeBuilder.get().config().prices.ev.setSingle0;
                TransactionService.execute(player, pokemonId, FeatureGate.Feature.EV_EDITOR,
                        "EV_SET_0", CobblemonBridge.statLabel(s), cost,
                        p -> p.getEvs().get(s) > 0,
                        p -> PokemonModifier.setEv(p, s, 0));
                refresh();
                return;
            }
        }
    }

    private static Formatting colorForEv(int value) {
        if (value >= 252) return Formatting.GREEN;
        if (value >= 190) return Formatting.AQUA;
        if (value >= 128) return Formatting.YELLOW;
        if (value > 0) return Formatting.GOLD;
        return Formatting.RED;
    }

    @Override
    protected void onOpen() { SoundHelper.openMenu(player); }
}
