package com.antigravity.pokebuilder.gui;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.gui.base.PaginatedGui;
import com.antigravity.pokebuilder.service.CobblemonBridge;
import com.antigravity.pokebuilder.service.FeatureGate;
import com.antigravity.pokebuilder.service.PokemonModifier;
import com.antigravity.pokebuilder.service.TransactionService;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.SoundHelper;
import com.antigravity.pokebuilder.util.TextHelper;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblemon.mod.common.pokeball.PokeBall;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BallSelectGui extends PaginatedGui<PokeBall> {

    private final UUID pokemonId;
    private List<PokeBall> cached;

    public BallSelectGui(ServerPlayerEntity player, UUID pokemonId) {
        super(player, Text.literal("PokeBuilder — Ball").formatted(Formatting.DARK_AQUA));
        this.pokemonId = pokemonId;
    }

    @Override
    protected List<PokeBall> items() {
        if (cached != null) return cached;
        cached = new ArrayList<>();
        try {
            cached.addAll(PokeBalls.INSTANCE.all());
        } catch (Throwable ignored) {}
        return cached;
    }

    @Override
    protected ItemStack renderItem(PokeBall ball) {
        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        boolean current = p != null && p.getCaughtBall().equals(ball);
        BigDecimal cost = PokeBuilder.get().config().prices.ballChange;

        // Each PokeBall has a 1:1 PokeBallItem accessible directly via .item().
        // Earlier versions tried to look up "<name>_pokeball" in the registry,
        // which didn't match the actual item ids and silently fell back to
        // snowballs.
        ItemStack base;
        try {
            PokeBallItem item = ball.item();
            base = item != null ? new ItemStack(item) : new ItemStack(Items.SNOWBALL);
        } catch (Throwable t) {
            base = new ItemStack(Items.SNOWBALL);
        }

        ItemBuilder b = ItemBuilder.of(base)
                .name(prettyBallName(ball.getName().getPath()), Formatting.AQUA, Formatting.BOLD)
                .lore("Cost: " + TextHelper.formatMoney(cost) + " " + TextHelper.currencyName(), Formatting.YELLOW);
        if (current) b.lore("[CURRENT]", Formatting.GREEN, Formatting.BOLD).glow(true);
        return b.build();
    }

    private static String prettyBallName(String raw) {
        if (raw == null || raw.isEmpty()) return "Ball";
        StringBuilder out = new StringBuilder();
        boolean upper = true;
        for (char c : raw.toCharArray()) {
            if (c == '_') { out.append(' '); upper = true; continue; }
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return out.toString();
    }

    @Override
    protected void onItemClick(PokeBall ball) {
        if (!FeatureGate.enforce(FeatureGate.Feature.BALL, player)) { close(); return; }
        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        if (p == null) { close(); return; }

        BigDecimal cost = PokeBuilder.get().config().prices.ballChange;
        String name = ball.getName().getPath();
        TransactionService.execute(player, pokemonId, FeatureGate.Feature.BALL,
                "BALL_SET", name, cost,
                pm -> {
                    try { return !pm.getCaughtBall().equals(ball); }
                    catch (Throwable t) { return true; }
                },
                pm -> PokemonModifier.setBall(pm, name));
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
