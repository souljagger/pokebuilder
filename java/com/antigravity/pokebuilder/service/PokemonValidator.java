package com.antigravity.pokebuilder.service;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.config.PokeBuilderConfig;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;

public final class PokemonValidator {

    private PokemonValidator() {}

    public static boolean isEgg(Pokemon pokemon) {
        try {
            return pokemon.getPersistentData().getBoolean("isEgg")
                    || pokemon.getSpecies().getName().equalsIgnoreCase("egg");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean allowGenderSwap(Pokemon pokemon) {
        try {
            Gender g = pokemon.getGender();
            if (g == Gender.GENDERLESS) return false;
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isBlocked(Pokemon pokemon) {
        PokeBuilderConfig cfg = PokeBuilder.get().config();
        try {
            String name = pokemon.getSpecies().getName().toLowerCase();
            for (String blocked : cfg.restrictions.blockedSpecies) {
                if (blocked.equalsIgnoreCase(name)) return true;
            }
            java.util.Set<String> labels = pokemon.getSpecies().getLabels();
            if (cfg.restrictions.blockLegendaries && containsAnyLabel(labels, "legendary")) return true;
            if (cfg.restrictions.blockMythicals && containsAnyLabel(labels, "mythical")) return true;
            if (cfg.restrictions.blockUltraBeasts && containsAnyLabel(labels, "ultra_beast", "ultrabeast")) return true;
            // Paradox pokemon wear either "paradox", "paradox_past" (ancient) or
            // "paradox_future" (iron) in Cobblemon's species label set.
            if (cfg.restrictions.blockParadox && containsAnyLabel(labels,
                    "paradox", "paradox_past", "paradox_future", "ancient_paradox", "future_paradox")) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean containsAnyLabel(java.util.Set<String> labels, String... candidates) {
        if (labels == null || labels.isEmpty()) return false;
        for (String c : candidates) {
            for (String l : labels) {
                if (l != null && l.equalsIgnoreCase(c)) return true;
            }
        }
        return false;
    }

    // True if the given FeatureGate.Feature is not in the admin's
    // blocked-modifications list. The list uses lowercase Feature
    // name()s as the canonical vocabulary — e.g. "iv_editor",
    // "shiny", "dynamax_level". Wire this at every purchase site
    // through TransactionService.
    public static boolean modificationAllowed(FeatureGate.Feature feature) {
        if (feature == null) return true;
        PokeBuilderConfig cfg = PokeBuilder.get().config();
        if (cfg.restrictions.blockedModifications == null || cfg.restrictions.blockedModifications.isEmpty()) {
            return true;
        }
        String key = feature.name().toLowerCase(java.util.Locale.ROOT);
        for (String blocked : cfg.restrictions.blockedModifications) {
            if (blocked == null) continue;
            if (blocked.equalsIgnoreCase(key)) return false;
        }
        return true;
    }

    // Legacy string-based overload kept for external callers that
    // don't yet have a Feature reference. Normalizes to lowercase.
    public static boolean modificationAllowed(String modification) {
        if (modification == null) return true;
        PokeBuilderConfig cfg = PokeBuilder.get().config();
        return !cfg.restrictions.blockedModifications.contains(modification.toLowerCase(java.util.Locale.ROOT));
    }
}
