package com.antigravity.pokebuilder.gui;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.economy.EconomyHandler;
import com.antigravity.pokebuilder.gui.base.BaseGui;
import com.antigravity.pokebuilder.service.CobblemonBridge;
import com.antigravity.pokebuilder.service.FeatureGate;
import com.antigravity.pokebuilder.service.PokemonModifier;
import com.antigravity.pokebuilder.service.TransactionService;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.SoundHelper;
import com.antigravity.pokebuilder.util.TextHelper;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.UUID;

// Stepper GUI for friendship. Each +N click costs stepCost × N so
// "+50" costs 5x the "+10" price — keeps incentives linear and lets
// admins tune via a single price knob (prices.friendship-per-step).
// "Set to max" costs (255 − current) × stepCost / 10, i.e. the same
// per-point rate as +10. "Reset to 0" is free (parity with other
// "downgrade" flows).
public class FriendshipGui extends BaseGui {

    private static final int INFO_SLOT = 4;
    private static final int PLUS_10_SLOT = 20;
    private static final int PLUS_50_SLOT = 22;
    private static final int MAX_SLOT = 24;
    private static final int RESET_SLOT = 31;
    private static final int BACK_SLOT = 45;
    private static final int BALANCE_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private static final int MAX_FRIENDSHIP = 255;

    private final UUID pokemonId;

    public FriendshipGui(ServerPlayerEntity player, UUID pokemonId) {
        super(player, 6, Text.literal("❖ PokeBuilder · Friendship ❖")
                .formatted(Formatting.DARK_AQUA, Formatting.BOLD));
        this.pokemonId = pokemonId;
    }

    @Override
    protected void build() {
        fillBorders(PokeBuilder.get().config().looks.borderColor);
        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        if (p == null) { close(); return; }

        int current = safeFriendship(p);
        BigDecimal stepCost = PokeBuilder.get().config().prices.friendshipPerStep;

        safeSet(INFO_SLOT, ItemBuilder.of(Items.POPPY)
                .name("Friendship: " + current + " / " + MAX_FRIENDSHIP,
                        Formatting.LIGHT_PURPLE, Formatting.BOLD)
                .lore("Every +10 costs " + TextHelper.formatMoney(stepCost)
                        + " " + TextHelper.currencyName(), Formatting.YELLOW)
                .lore("Higher friendship evolves certain Pokémon", Formatting.DARK_GRAY)
                .glow(current == MAX_FRIENDSHIP)
                .build());

        int room = MAX_FRIENDSHIP - current;
        renderStep(PLUS_10_SLOT, Items.PINK_DYE, "+10", 10, stepCost, room);
        renderStep(PLUS_50_SLOT, Items.PINK_DYE, "+50", 50, stepCost, room);

        int toMax = Math.max(0, MAX_FRIENDSHIP - current);
        BigDecimal maxCost = stepCost.multiply(BigDecimal.valueOf(toMax)).divide(BigDecimal.TEN);
        boolean canMax = toMax > 0;
        safeSet(MAX_SLOT, ItemBuilder.of(canMax ? Items.RED_DYE : Items.GRAY_DYE)
                .name(canMax ? "Set to max (255)" : "Already maxed",
                        canMax ? Formatting.RED : Formatting.DARK_GRAY, Formatting.BOLD)
                .lore(canMax ? "Cost: " + TextHelper.formatMoney(maxCost) + " "
                                + TextHelper.currencyName()
                        : "", Formatting.YELLOW)
                .glow(!canMax).build());

        boolean canReset = current > 0;
        safeSet(RESET_SLOT, ItemBuilder.of(canReset ? Items.BARRIER : Items.GRAY_DYE)
                .name(canReset ? "Reset to 0 (free)" : "Already zero",
                        canReset ? Formatting.GRAY : Formatting.DARK_GRAY, Formatting.BOLD)
                .build());

        safeSet(BACK_SLOT, ItemBuilder.of(Items.ARROW).name("Back", Formatting.YELLOW).build());
        safeSet(BALANCE_SLOT, balanceItem());
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED, Formatting.BOLD).build());
    }

    private void renderStep(int slot, Item icon, String label, int amount, BigDecimal stepCost, int room) {
        boolean enabled = room > 0;
        int delta = Math.min(amount, room);
        BigDecimal cost = enabled
                ? stepCost.multiply(BigDecimal.valueOf(delta)).divide(BigDecimal.TEN)
                : BigDecimal.ZERO;
        safeSet(slot, ItemBuilder.of(enabled ? icon : Items.GRAY_DYE)
                .name(label, enabled ? Formatting.LIGHT_PURPLE : Formatting.DARK_GRAY, Formatting.BOLD)
                .lore(enabled ? "Cost: " + TextHelper.formatMoney(cost) + " " + TextHelper.currencyName()
                        : "Already at max", Formatting.YELLOW)
                .glow(!enabled).build());
    }

    private ItemStack balanceItem() {
        BigDecimal bal = EconomyHandler.isReady() ? EconomyHandler.getBalance(player.getUuid()) : BigDecimal.ZERO;
        return ItemBuilder.of(Items.GOLD_INGOT)
                .name("Balance: " + TextHelper.formatMoney(bal) + " " + TextHelper.currencyName(),
                        Formatting.GOLD, Formatting.BOLD)
                .build();
    }

    private int safeFriendship(Pokemon p) {
        try { return p.getFriendship(); } catch (Throwable t) { return 0; }
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (slot == BACK_SLOT) { SoundHelper.back(player); new EditorGui(player, pokemonId).open(); return; }
        if (slot == CLOSE_SLOT) { close(); return; }
        if (!FeatureGate.enforce(FeatureGate.Feature.FRIENDSHIP, player)) { close(); return; }

        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        if (p == null) { close(); return; }
        int current = safeFriendship(p);
        BigDecimal stepCost = PokeBuilder.get().config().prices.friendshipPerStep;

        switch (slot) {
            case PLUS_10_SLOT -> applyDelta(current, 10, stepCost);
            case PLUS_50_SLOT -> applyDelta(current, 50, stepCost);
            case MAX_SLOT -> applyDelta(current, MAX_FRIENDSHIP - current, stepCost);
            case RESET_SLOT -> applySet(0, BigDecimal.ZERO);
            default -> {}
        }
    }

    private void applyDelta(int current, int delta, BigDecimal stepCost) {
        if (delta <= 0) return;
        int room = MAX_FRIENDSHIP - current;
        int actual = Math.min(delta, room);
        if (actual <= 0) return;
        BigDecimal cost = stepCost.multiply(BigDecimal.valueOf(actual)).divide(BigDecimal.TEN);
        TransactionService.execute(player, pokemonId, FeatureGate.Feature.FRIENDSHIP,
                "FRIENDSHIP_ADD", "+" + actual, cost,
                pm -> PokemonModifier.incrementFriendship(pm, actual));
        refresh();
    }

    private void applySet(int target, BigDecimal cost) {
        TransactionService.execute(player, pokemonId, FeatureGate.Feature.FRIENDSHIP,
                "FRIENDSHIP_SET", String.valueOf(target), cost,
                pm -> PokemonModifier.setFriendship(pm, target));
        refresh();
    }

    @Override
    protected void onOpen() { SoundHelper.openMenu(player); }
}
