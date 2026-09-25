package com.antigravity.pokebuilder.gui;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.gui.base.BaseGui;
import com.antigravity.pokebuilder.service.CobblemonBridge;
import com.antigravity.pokebuilder.service.FeatureGate;
import com.antigravity.pokebuilder.service.MegaShowdownBridge;
import com.antigravity.pokebuilder.service.PokemonModifier;
import com.antigravity.pokebuilder.service.TransactionService;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.ModItems;
import com.antigravity.pokebuilder.util.SoundHelper;
import com.antigravity.pokebuilder.util.TextHelper;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.UUID;

public class DynamaxLevelGui extends BaseGui {

    private static final int INFO_SLOT = 4;
    private static final int BACK_SLOT = 45;
    private static final int BALANCE_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private final UUID pokemonId;

    public DynamaxLevelGui(ServerPlayerEntity player, UUID pokemonId) {
        super(player, 6, Text.literal("PokeBuilder — Dynamax Level").formatted(Formatting.LIGHT_PURPLE));
        this.pokemonId = pokemonId;
    }

    @Override
    protected void build() {
        fillBorders(10);
        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        if (p == null) { close(); return; }

        int max = MegaShowdownBridge.maxDynamaxLevel();
        int current = safeCurrentLevel(p);
        BigDecimal step = PokeBuilder.get().config().prices.dynamaxLevelPerStep;

        safeSet(INFO_SLOT, ItemBuilder.of(ModItems.dynamaxBand())
                .name("Dynamax Level " + current + " / " + max,
                        Formatting.LIGHT_PURPLE, Formatting.BOLD)
                .lore("Price per level: " + TextHelper.formatMoney(step) + " " + TextHelper.currencyName(), Formatting.YELLOW)
                .lore("Click a level below to set it.", Formatting.GRAY)
                .glow(current == max)
                .build());

        // Ten (or N) level buttons on rows 2-3, plus a "0" reset and a "Max" shortcut.
        int[] rowSlots = new int[] {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int lvl = 0; lvl <= max && lvl < rowSlots.length; lvl++) {
            boolean isCurrent = (lvl == current);
            BigDecimal cost = costFromCurrent(current, lvl, step);
            // Each level button is a stack of dynamax candies sized to the level.
            ItemBuilder b = ItemBuilder.of(lvl == 0 ? Items.GRAY_STAINED_GLASS_PANE : ModItems.dynamaxCandy())
                    .name("Level " + lvl, isCurrent ? Formatting.GREEN : Formatting.LIGHT_PURPLE, Formatting.BOLD)
                    .count(Math.max(1, Math.min(64, lvl + 1)));
            if (isCurrent) {
                b.lore("[CURRENT]", Formatting.GREEN, Formatting.BOLD).glow(true);
            } else {
                if (cost.signum() == 0) {
                    b.lore("Free (decrease)", Formatting.GRAY);
                } else {
                    b.lore("Cost: " + TextHelper.formatMoney(cost) + " " + TextHelper.currencyName(), Formatting.YELLOW);
                }
                b.lore("Click to set", Formatting.GRAY);
            }
            safeSet(rowSlots[lvl], b.build());
        }

        safeSet(BACK_SLOT, ItemBuilder.of(Items.ARROW).name("Back", Formatting.YELLOW).build());
        safeSet(BALANCE_SLOT, balanceItem());
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED).build());
    }

    private BigDecimal costFromCurrent(int current, int target, BigDecimal step) {
        if (target <= current) return BigDecimal.ZERO;
        return step.multiply(BigDecimal.valueOf(target - current));
    }

    private ItemStack balanceItem() {
        return ItemBuilder.of(Items.GOLD_INGOT)
                .name("Balance: " + TextHelper.formatMoney(
                        com.antigravity.pokebuilder.economy.EconomyHandler.isReady()
                                ? com.antigravity.pokebuilder.economy.EconomyHandler.getBalance(player.getUuid())
                                : BigDecimal.ZERO) + " " + TextHelper.currencyName(),
                        Formatting.GOLD, Formatting.BOLD)
                .build();
    }

    private int safeCurrentLevel(Pokemon p) {
        try { return p.getDmaxLevel(); } catch (Throwable t) { return 0; }
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (slot == BACK_SLOT) { SoundHelper.back(player); new EditorGui(player, pokemonId).open(); return; }
        if (slot == CLOSE_SLOT) { close(); return; }

        int max = MegaShowdownBridge.maxDynamaxLevel();
        int[] rowSlots = new int[] {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int lvl = 0; lvl <= max && lvl < rowSlots.length; lvl++) {
            if (rowSlots[lvl] == slot) {
                applyLevel(lvl);
                return;
            }
        }
    }

    private void applyLevel(int target) {
        if (!FeatureGate.enforce(FeatureGate.Feature.DYNAMAX_LEVEL, player)) { close(); return; }
        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        if (p == null) { close(); return; }
        int current = safeCurrentLevel(p);
        if (target == current) return;

        BigDecimal step = PokeBuilder.get().config().prices.dynamaxLevelPerStep;
        BigDecimal cost = costFromCurrent(current, target, step);

        TransactionService.execute(player, pokemonId, FeatureGate.Feature.DYNAMAX_LEVEL,
                "DYNAMAX_LEVEL", String.valueOf(target), cost,
                pm -> PokemonModifier.setDynamaxLevel(pm, target));
        refresh();
    }

    @Override
    protected void onOpen() { SoundHelper.openMenu(player); }
}
