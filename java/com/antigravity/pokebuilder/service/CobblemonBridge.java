package com.antigravity.pokebuilder.service;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CobblemonBridge {

    private CobblemonBridge() {}

    public static PlayerPartyStore getParty(ServerPlayerEntity player) {
        try {
            return Cobblemon.INSTANCE.getStorage().getParty(player);
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<Pokemon> partyAsList(ServerPlayerEntity player) {
        List<Pokemon> list = new ArrayList<>(6);
        PlayerPartyStore store = getParty(player);
        if (store == null) return list;
        for (int i = 0; i < 6; i++) {
            try {
                Pokemon p = store.get(i);
                list.add(p);
            } catch (Throwable t) {
                list.add(null);
            }
        }
        return list;
    }

    public static Pokemon findByUuid(ServerPlayerEntity player, UUID pokemonId) {
        PlayerPartyStore store = getParty(player);
        if (store != null) {
            for (int i = 0; i < 6; i++) {
                try {
                    Pokemon p = store.get(i);
                    if (p != null && pokemonId.equals(p.getUuid())) return p;
                } catch (Throwable ignored) {}
            }
        }
        // Only search the PC when the feature is enabled server-wide.
        // Otherwise the scan is wasted work on every transaction.
        if (FeatureGate.enabled(FeatureGate.Feature.PC_ACCESS)) {
            Pokemon pcHit = findInPC(player, pokemonId);
            if (pcHit != null) return pcHit;
        }
        return null;
    }

    // Returns the PC for the player, or null if storage isn't
    // available (Cobblemon not yet up, player data not loaded).
    public static PCStore getPC(ServerPlayerEntity player) {
        try {
            PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
            if (pc == null) {
                com.antigravity.pokebuilder.PokeBuilder.LOGGER.warn(
                        "[PokeBuilder] Cobblemon.storage.getPC({}) returned null",
                        player.getName().getString());
            }
            return pc;
        } catch (Throwable t) {
            com.antigravity.pokebuilder.PokeBuilder.LOGGER.error(
                    "[PokeBuilder] Cobblemon.storage.getPC({}) threw: {}",
                    player.getName().getString(), t.toString(), t);
            return null;
        }
    }

    // Iterate boxes until we find the requested Pokémon. Short-circuits
    // on hit. 30 boxes × 30 slots = 900 max iterations — fast enough to
    // run per transaction without measurable lag.
    public static Pokemon findInPC(ServerPlayerEntity player, UUID pokemonId) {
        PCStore pc = getPC(player);
        if (pc == null || pokemonId == null) return null;
        for (PCBox box : pc.getBoxes()) {
            for (Pokemon p : box) {
                if (p != null && pokemonId.equals(p.getUuid())) return p;
            }
        }
        return null;
    }

    public static Stats[] allStats() {
        return new Stats[]{Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
    }

    public static String statLabel(Stats s) {
        return switch (s) {
            case HP -> "HP";
            case ATTACK -> "Atk";
            case DEFENCE -> "Def";
            case SPECIAL_ATTACK -> "SpA";
            case SPECIAL_DEFENCE -> "SpD";
            case SPEED -> "Spe";
            default -> s.name();
        };
    }
}
