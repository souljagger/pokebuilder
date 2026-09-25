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

public class IVEditorGui extends BaseGui {

    private static final int[] HEADER_SLOTS = {10, 11, 12, 13, 14, 15};
    private static final int[] SET31_SLOTS = {19, 20, 21, 22, 23, 24};
    private static final int[] SET0_SLOTS = {28, 29, 30, 31, 32, 33};
    private static final int BACK_SLOT = 45;
    private static final int MAX_ALL_SLOT = 52;
    private static final int CLOSE_SLOT = 53;

    private final UUID pokemonId;

    public IVEditorGui(ServerPlayerEntity player, UUID pokemonId) {
        super(player, 6, Text.literal("\u2756 PokeBuilder \u00b7 IVs \u2756").formatted(Formatting.DARK_AQUA, Formatting.BOLD));
        this.pokemonId = pokemonId;
    }

    @Override
    protected void build() {
        fillBorders(PokeBuilder.get().config().looks.borderColor);
        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        if (p == null) { close(); return; }
        PokeBuilderConfig cfg = PokeBuilder.get().config();

        Stats[] stats = CobblemonBridge.allStats();
        for (int i = 0; i < 6; i++) {
            int current = p.getIvs().get(stats[i]);
            Item icon = ModItems.hyperCandyFor(i);
            safeSet(HEADER_SLOTS[i], ItemBuilder.of(icon)
                    .name(CobblemonBridge.statLabel(stats[i]) + ": " + current + " / 31",
                            colorForStat(current, 31), Formatting.BOLD)
                    .lore("Individual value", Formatting.DARK_GRAY)
                    .glow(current == 31)
                    .build());

            boolean maxed = current >= 31;
            safeSet(SET31_SLOTS[i], ItemBuilder.of(maxed ? Items.LIME_STAINED_GLASS_PANE : Items.LIME_DYE)
                    .name("Set to 31", maxed ? Formatting.DARK_GRAY : Formatting.GREEN, Formatting.BOLD)
                    .lore(maxed ? "Already maxed!" : "Cost: " + TextHelper.formatMoney(cfg.prices.iv.setSingle31) + " " + TextHelper.currencyName(),
                            maxed ? Formatting.GREEN : Formatting.YELLOW)
                    .glow(maxed).build());

            boolean zeroed = current <= 0;
            safeSet(SET0_SLOTS[i], ItemBuilder.of(zeroed ? Items.RED_STAINED_GLASS_PANE : Items.RED_DYE)
                    .name("Set to 0", zeroed ? Formatting.DARK_GRAY : Formatting.RED, Formatting.BOLD)
                    .lore(zeroed ? "Already zero" : "Cost: " + TextHelper.formatMoney(cfg.prices.iv.setSingle0) + " " + TextHelper.currencyName(),
                            zeroed ? Formatting.GREEN : Formatting.YELLOW)
                    .build());
        }

        safeSet(BACK_SLOT, ItemBuilder.of(Items.ARROW).name("Back", Formatting.YELLOW).build());
        if (PokeBuilder.get().config().features.ivMaxAll) {
            safeSet(MAX_ALL_SLOT, ItemBuilder.of(ModItems.rareCandy())
                    .name("Max All IVs", Formatting.AQUA, Formatting.BOLD)
                    .lore("Cost: " + TextHelper.formatMoney(cfg.prices.iv.maxAll) + " " + TextHelper.currencyName(), Formatting.YELLOW)
                    .build());
        }
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED).build());
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (slot == BACK_SLOT) { SoundHelper.back(player); new EditorGui(player, pokemonId).open(); return; }
        if (slot == CLOSE_SLOT) { close(); return; }
        if (!FeatureGate.enforce(FeatureGate.Feature.IV_EDITOR, player)) { close(); return; }
        if (slot == MAX_ALL_SLOT) {
            if (!FeatureGate.enforce(FeatureGate.Feature.IV_MAX_ALL, player)) return;
            BigDecimal cost = PokeBuilder.get().config().prices.iv.maxAll;
            TransactionService.execute(player, pokemonId, FeatureGate.Feature.IV_MAX_ALL,
                    "IV_MAX_ALL", "all31", cost,
                    // Precheck: skip the withdraw if every IV is already 31.
                    // Prior to v1.4 clicking "Max All" on a perfect mon would
                    // burn the price every click with no change.
                    p -> {
                        for (Stats s : CobblemonBridge.allStats()) {
                            if (p.getIvs().get(s) < 31) return true;
                        }
                        return false;
                    },
                    PokemonModifier::maxAllIvs);
            refresh();
            return;
        }

        Stats[] stats = CobblemonBridge.allStats();
        for (int i = 0; i < 6; i++) {
            if (SET31_SLOTS[i] == slot) {
                final Stats s = stats[i];
                BigDecimal cost = PokeBuilder.get().config().prices.iv.setSingle31;
                TransactionService.execute(player, pokemonId, FeatureGate.Feature.IV_EDITOR,
                        "IV_SET_31", CobblemonBridge.statLabel(s), cost,
                        p -> p.getIvs().get(s) < 31,
                        p -> PokemonModifier.setIv(p, s, 31));
                refresh();
                return;
            }
            if (SET0_SLOTS[i] == slot) {
                final Stats s = stats[i];
                BigDecimal cost = PokeBuilder.get().config().prices.iv.setSingle0;
                TransactionService.execute(player, pokemonId, FeatureGate.Feature.IV_EDITOR,
                        "IV_SET_0", CobblemonBridge.statLabel(s), cost,
                        p -> p.getIvs().get(s) > 0,
                        p -> PokemonModifier.setIv(p, s, 0));
                refresh();
                return;
            }
        }
    }

    private static Formatting colorForStat(int value, int max) {
        if (value >= max) return Formatting.GREEN;
        if (value >= max * 3 / 4) return Formatting.AQUA;
        if (value >= max / 2) return Formatting.YELLOW;
        if (value > 0) return Formatting.GOLD;
        return Formatting.RED;
    }

    @Override
    protected void onOpen() { SoundHelper.openMenu(player); }
}
