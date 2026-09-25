package com.antigravity.pokebuilder.gui;

import com.antigravity.pokebuilder.economy.EconomyHandler;
import com.antigravity.pokebuilder.gui.base.BaseGui;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.SoundHelper;
import com.antigravity.pokebuilder.util.TextHelper;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

public class ConfirmationGui extends BaseGui {

    private static final int INFO_SLOT = 4;
    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;

    private final UUID pokemonId;
    private final String description;
    private final BigDecimal cost;
    private final Consumer<Pokemon> onConfirm;

    public ConfirmationGui(ServerPlayerEntity player, UUID pokemonId, String description,
                           BigDecimal cost, Consumer<Pokemon> onConfirm) {
        super(player, 3, Text.literal("Confirm Purchase").formatted(Formatting.DARK_AQUA));
        this.pokemonId = pokemonId;
        this.description = description;
        this.cost = cost;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void build() {
        fillBorders(7);
        BigDecimal balance = EconomyHandler.isReady() ? EconomyHandler.getBalance(player.getUuid()) : BigDecimal.ZERO;
        BigDecimal after = balance.subtract(cost);

        safeSet(INFO_SLOT, ItemBuilder.of(Items.PAPER)
                .name("Confirm Purchase", Formatting.GOLD, Formatting.BOLD)
                .lore("Change: " + description, Formatting.GRAY)
                .lore("Cost: " + TextHelper.formatMoney(cost) + " " + TextHelper.currencyName(), Formatting.YELLOW)
                .lore("Balance after: " + TextHelper.formatMoney(after) + " " + TextHelper.currencyName(), Formatting.YELLOW)
                .build());

        safeSet(CONFIRM_SLOT, ItemBuilder.of(Items.LIME_WOOL)
                .name("CONFIRM", Formatting.GREEN, Formatting.BOLD).build());
        safeSet(CANCEL_SLOT, ItemBuilder.of(Items.RED_WOOL)
                .name("CANCEL", Formatting.RED, Formatting.BOLD).build());
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (slot == CONFIRM_SLOT) {
            Pokemon p = com.antigravity.pokebuilder.service.CobblemonBridge.findByUuid(player, pokemonId);
            if (p != null) {
                onConfirm.accept(p);
            }
        } else if (slot == CANCEL_SLOT) {
            SoundHelper.back(player);
            new EditorGui(player, pokemonId).open();
        }
    }

    @Override
    protected void onOpen() { SoundHelper.openMenu(player); }
}
