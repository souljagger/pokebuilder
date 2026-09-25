package com.antigravity.pokebuilder.service.purchasables;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.PokeBuilderConstants;
import com.antigravity.pokebuilder.gui.FriendshipGui;
import com.antigravity.pokebuilder.service.FeatureGate;
import com.antigravity.pokebuilder.service.Purchasable;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.TextHelper;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.UUID;

public final class FriendshipPurchasable implements Purchasable {

    @Override public String id() { return "friendship"; }
    @Override public FeatureGate.Feature feature() { return FeatureGate.Feature.FRIENDSHIP; }
    @Override public String permission() { return PokeBuilderConstants.PERM_MOD_FRIENDSHIP; }
    @Override public int editorSlot() { return 22; }

    @Override
    public ItemStack icon(Pokemon pokemon, ServerPlayerEntity who) {
        int current = safeFriendship(pokemon);
        BigDecimal step = PokeBuilder.get().config().prices.friendshipPerStep;
        return ItemBuilder.of(Items.POPPY)
                .name("Friendship: " + current + " / 255", Formatting.LIGHT_PURPLE, Formatting.BOLD)
                .lore("Cost per +10: " + TextHelper.formatMoney(step) + " "
                        + TextHelper.currencyName(), Formatting.YELLOW)
                .lore("Click to open the friendship picker.", Formatting.GRAY)
                .glow(current == 255)
                .build();
    }

    @Override
    public BigDecimal basePrice(Pokemon pokemon, ServerPlayerEntity who) {
        return PokeBuilder.get().config().prices.friendshipPerStep;
    }

    @Override
    public void invoke(ServerPlayerEntity player, UUID pokemonId) {
        new FriendshipGui(player, pokemonId).open();
    }

    private int safeFriendship(Pokemon p) {
        try { return p.getFriendship(); } catch (Throwable t) { return 0; }
    }
}
