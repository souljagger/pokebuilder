package com.antigravity.pokebuilder.service.purchasables;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.PokeBuilderConstants;
import com.antigravity.pokebuilder.service.CobblemonBridge;
import com.antigravity.pokebuilder.service.FeatureGate;
import com.antigravity.pokebuilder.service.PokemonModifier;
import com.antigravity.pokebuilder.service.Purchasable;
import com.antigravity.pokebuilder.service.TransactionService;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.TextHelper;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.UUID;

// Instant purchasable. Eligible only when the Pokémon currently has a
// persistent status (poison, burn, paralysis, freeze, sleep). Greyed
// out via the eligibility() hook when the Pokémon is already healthy
// so players don't waste a click.
public final class StatusCurePurchasable implements Purchasable {

    @Override public String id() { return "status_cure"; }
    @Override public FeatureGate.Feature feature() { return FeatureGate.Feature.STATUS_CURE; }
    @Override public String permission() { return PokeBuilderConstants.PERM_MOD_STATUS_CURE; }
    @Override public int editorSlot() { return 24; }

    @Override
    public ItemStack icon(Pokemon pokemon, ServerPlayerEntity who) {
        BigDecimal cost = PokeBuilder.get().config().prices.statusCure;
        boolean hasStatus = hasStatus(pokemon);
        if (!hasStatus) {
            return ItemBuilder.of(Items.GRAY_STAINED_GLASS_PANE)
                    .name("Status Cure", Formatting.DARK_GRAY, Formatting.BOLD, Formatting.STRIKETHROUGH)
                    .lore("Pokémon is already healthy.", Formatting.DARK_GRAY)
                    .build();
        }
        String statusName = statusName(pokemon);
        return ItemBuilder.of(Items.HONEY_BOTTLE)
                .name("Status Cure", Formatting.GREEN, Formatting.BOLD)
                .lore("Current: " + statusName, Formatting.YELLOW)
                .lore("Cost: " + TextHelper.formatMoney(cost) + " " + TextHelper.currencyName(),
                        Formatting.YELLOW)
                .lore("Click to heal", Formatting.GRAY)
                .glow(true)
                .build();
    }

    @Override
    public BigDecimal basePrice(Pokemon pokemon, ServerPlayerEntity who) {
        return PokeBuilder.get().config().prices.statusCure;
    }

    @Override
    public Eligibility eligibility(Pokemon pokemon, ServerPlayerEntity who) {
        return hasStatus(pokemon) ? Eligibility.OK
                : Eligibility.denied("status.already_healthy");
    }

    @Override
    public void invoke(ServerPlayerEntity player, UUID pokemonId) {
        Pokemon pokemon = CobblemonBridge.findByUuid(player, pokemonId);
        if (pokemon == null) return;
        if (!hasStatus(pokemon)) {
            player.sendMessage(PokeBuilder.get().config().messages.get("status.already_healthy"), false);
            return;
        }
        BigDecimal cost = basePrice(pokemon, player);
        String prev = statusName(pokemon);
        TransactionService.Result r = TransactionService.execute(player, pokemonId, feature(),
                id().toUpperCase(), prev, cost,
                PokemonModifier::clearStatus);
        if (r == TransactionService.Result.SUCCESS) {
            player.sendMessage(PokeBuilder.get().config().messages.get("status.cured"), false);
        }
    }

    private boolean hasStatus(Pokemon pokemon) {
        try {
            return pokemon.getStatus() != null;
        } catch (Throwable t) { return false; }
    }

    private String statusName(Pokemon pokemon) {
        try {
            var container = pokemon.getStatus();
            if (container == null) return "—";
            var ps = container.getStatus();
            if (ps == null) return "—";
            return ps.getShowdownName();
        } catch (Throwable t) {
            return "status";
        }
    }
}
