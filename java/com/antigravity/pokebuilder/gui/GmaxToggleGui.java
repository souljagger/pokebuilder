package com.antigravity.pokebuilder.gui;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.economy.EconomyHandler;
import com.antigravity.pokebuilder.gui.base.BaseGui;
import com.antigravity.pokebuilder.service.CobblemonBridge;
import com.antigravity.pokebuilder.service.FeatureGate;
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

public class GmaxToggleGui extends BaseGui {

    private static final int INFO_SLOT = 4;
    private static final int TOGGLE_SLOT = 22;
    private static final int BACK_SLOT = 45;
    private static final int BALANCE_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private final UUID pokemonId;

    public GmaxToggleGui(ServerPlayerEntity player, UUID pokemonId) {
        super(player, 6, Text.literal("PokeBuilder — G-Max Factor").formatted(Formatting.LIGHT_PURPLE));
        this.pokemonId = pokemonId;
    }

    @Override
    protected void build() {
        fillBorders(10);
        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        if (p == null) { close(); return; }

        boolean hasFactor = safeGmax(p);
        BigDecimal cost = PokeBuilder.get().config().prices.gmaxFactor;

        safeSet(INFO_SLOT, ItemBuilder.of(ModItems.dynamaxBand())
                .name("Gigantamax Factor", Formatting.LIGHT_PURPLE, Formatting.BOLD)
                .lore("Current: " + (hasFactor ? "UNLOCKED" : "Locked"),
                        hasFactor ? Formatting.GREEN : Formatting.GRAY)
                .lore("Grants access to the G-Max form", Formatting.DARK_GRAY)
                .lore("when this species supports it.", Formatting.DARK_GRAY)
                .glow(hasFactor)
                .build());

        safeSet(TOGGLE_SLOT, ItemBuilder.of(hasFactor ? Items.RED_STAINED_GLASS_PANE : ModItems.maxMushroom())
                .name(hasFactor ? "Remove G-Max Factor" : "Grant G-Max Factor",
                        Formatting.LIGHT_PURPLE, Formatting.BOLD)
                .lore(hasFactor ? "Free — removes the factor" : "Cost: " + TextHelper.formatMoney(cost) + " " + TextHelper.currencyName(),
                        hasFactor ? Formatting.GRAY : Formatting.YELLOW)
                .lore("Click to " + (hasFactor ? "remove" : "grant"), Formatting.GRAY)
                .glow(!hasFactor)
                .build());

        safeSet(BACK_SLOT, ItemBuilder.of(Items.ARROW).name("Back", Formatting.YELLOW).build());
        safeSet(BALANCE_SLOT, balanceItem());
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED).build());
    }

    private ItemStack balanceItem() {
        BigDecimal bal = EconomyHandler.isReady() ? EconomyHandler.getBalance(player.getUuid()) : BigDecimal.ZERO;
        return ItemBuilder.of(Items.GOLD_INGOT)
                .name("Balance: " + TextHelper.formatMoney(bal) + " " + TextHelper.currencyName(),
                        Formatting.GOLD, Formatting.BOLD)
                .build();
    }

    private boolean safeGmax(Pokemon p) {
        try { return p.getGmaxFactor(); } catch (Throwable t) { return false; }
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (slot == BACK_SLOT) { SoundHelper.back(player); new EditorGui(player, pokemonId).open(); return; }
        if (slot == CLOSE_SLOT) { close(); return; }
        if (slot == TOGGLE_SLOT) {
            if (!FeatureGate.enforce(FeatureGate.Feature.GMAX_FACTOR, player)) return;
            Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
            if (p == null) { close(); return; }
            boolean target = !safeGmax(p);
            BigDecimal cost = target ? PokeBuilder.get().config().prices.gmaxFactor : BigDecimal.ZERO;
            TransactionService.execute(player, pokemonId, FeatureGate.Feature.GMAX_FACTOR,
                    "GMAX_FACTOR", target ? "ON" : "OFF", cost,
                    pm -> PokemonModifier.setGmaxFactor(pm, target));
            refresh();
        }
    }

    @Override
    protected void onOpen() { SoundHelper.openMenu(player); }
}
