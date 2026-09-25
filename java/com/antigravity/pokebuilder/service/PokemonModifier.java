package com.antigravity.pokebuilder.service;

import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.tera.TeraType;
import com.cobblemon.mod.common.api.types.tera.TeraTypes;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PokemonModifier {

    private PokemonModifier() {}

    public static void setIv(Pokemon pokemon, Stats stat, int value) {
        pokemon.getIvs().set(stat, clamp(value, 0, 31));
    }

    public static void maxAllIvs(Pokemon pokemon) {
        for (Stats s : CobblemonBridge.allStats()) pokemon.getIvs().set(s, 31);
    }

    public static void setEv(Pokemon pokemon, Stats stat, int value) {
        pokemon.getEvs().set(stat, clamp(value, 0, 252));
    }

    /**
     * Adds {@code amount} EVs to a single stat, respecting both the per-stat
     * cap (252) and the total EV cap (510). The actual increment may be less
     * than {@code amount} if either cap would be exceeded.
     *
     * @throws IllegalStateException if no EVs can be added (already at cap).
     */
    public static void addEv(Pokemon pokemon, Stats stat, int amount) {
        int current = pokemon.getEvs().get(stat);
        int total = totalEvs(pokemon);
        int headroom = Math.min(252 - current, 510 - total);
        int toAdd = Math.min(amount, Math.max(0, headroom));
        if (toAdd <= 0) {
            throw new IllegalStateException("Cannot add EVs — already at cap");
        }
        pokemon.getEvs().set(stat, current + toAdd);
    }

    public static int totalEvs(Pokemon pokemon) {
        int total = 0;
        for (Stats s : CobblemonBridge.allStats()) {
            total += pokemon.getEvs().get(s);
        }
        return total;
    }

    public static void resetAllEvs(Pokemon pokemon) {
        for (Stats s : CobblemonBridge.allStats()) pokemon.getEvs().set(s, 0);
    }

    public static void setNature(Pokemon pokemon, String natureId) {
        Nature nature = Natures.INSTANCE.getNature(Identifier.of("cobblemon", natureId));
        if (nature != null) pokemon.setNature(nature);
    }

    public static void setAbility(Pokemon pokemon, int abilityIndex) {
        List<AbilityEntry> abilities = flattenAbilitiesTagged(pokemon);
        if (abilityIndex < 0 || abilityIndex >= abilities.size()) return;
        AbilityTemplate target = abilities.get(abilityIndex).template();
        com.cobblemon.mod.common.api.abilities.Ability fresh =
                target.create((net.minecraft.nbt.NbtCompound) null);
        // Mark the ability as "forced" so Cobblemon's respawn/form-change
        // logic doesn't reroll to the species' default on the next tick.
        // The setForced$common method is Kotlin-internal; reachable from
        // Java only via reflection. Silent no-op if the signature drifts
        // in a future Cobblemon version — the updateAbility call below
        // still runs either way.
        try {
            var m = fresh.getClass().getMethod("setForced$common", boolean.class);
            m.invoke(fresh, true);
        } catch (Throwable ignored) {}
        pokemon.updateAbility(fresh);
    }

    // Returns the Pokémon's current ability name, or "<none>"/"<err>" when
    // the accessor misbehaves. Callers use this for fail-loud verification
    // after updateAbility — if the name didn't change to the requested
    // template, the caller throws and TransactionService refunds.
    public static String currentAbilityName(Pokemon pokemon) {
        if (pokemon == null) return "<no-pokemon>";
        try {
            var ab = pokemon.getAbility();
            if (ab == null) return "<none>";
            var tpl = ab.getTemplate();
            return tpl == null ? "<none>" : tpl.getName();
        } catch (Throwable t) { return "<err>"; }
    }

    /**
     * Returns every unique ability available on the Pokémon's current form,
     * tagged with whether each one is a hidden ability. Normal abilities are
     * listed first, hidden abilities last — so GUI slot indices stay stable
     * regardless of the order Cobblemon's mapping happens to iterate.
     */
    public static List<AbilityEntry> flattenAbilitiesTagged(Pokemon pokemon) {
        Set<String> seen = new LinkedHashSet<>();
        List<AbilityEntry> normals = new ArrayList<>();
        List<AbilityEntry> hiddens = new ArrayList<>();
        try {
            for (var entry : pokemon.getForm().getAbilities().getMapping().entrySet()) {
                List<PotentialAbility> slot = entry.getValue();
                if (slot == null) continue;
                for (PotentialAbility potential : slot) {
                    if (potential == null) continue;
                    AbilityTemplate tpl = potential.getTemplate();
                    if (tpl == null) continue;
                    if (!seen.add(tpl.getName())) continue;
                    boolean isHidden = isHiddenPotential(potential, entry.getKey());
                    AbilityEntry ae = new AbilityEntry(tpl, isHidden);
                    if (isHidden) hiddens.add(ae); else normals.add(ae);
                }
            }
        } catch (Throwable ignored) {}
        normals.addAll(hiddens);
        return normals;
    }

    /**
     * Determines whether a {@link PotentialAbility} is hidden by checking
     * its runtime class name (Cobblemon uses a {@code HiddenAbility}
     * subclass) and falling back to the mapping key's string form.
     */
    private static boolean isHiddenPotential(PotentialAbility pa, Object mappingKey) {
        // Primary: Cobblemon 1.7+ uses com.cobblemon.mod.common.pokemon.abilities.HiddenAbility
        String className = pa.getClass().getSimpleName().toLowerCase();
        if (className.contains("hidden")) return true;
        // Fallback: the mapping key might be "H", "HA", or contain "hidden"
        if (mappingKey != null) {
            String key = String.valueOf(mappingKey).toLowerCase().trim();
            if (key.equals("h") || key.equals("ha") || key.contains("hidden")) return true;
        }
        return false;
    }

    /** @deprecated Use {@link #flattenAbilitiesTagged(Pokemon)} instead. */
    @Deprecated
    public static List<AbilityTemplate> flattenAbilities(Pokemon pokemon) {
        return flattenAbilitiesTagged(pokemon).stream()
                .map(AbilityEntry::template)
                .collect(java.util.stream.Collectors.toList());
    }

    public static void toggleShiny(Pokemon pokemon) {
        pokemon.setShiny(!pokemon.getShiny());
    }

    public static void swapGender(Pokemon pokemon) {
        Gender g = pokemon.getGender();
        if (g == Gender.MALE) pokemon.setGender(Gender.FEMALE);
        else if (g == Gender.FEMALE) pokemon.setGender(Gender.MALE);
    }

    public static void setBall(Pokemon pokemon, String ballName) {
        var ball = PokeBalls.INSTANCE.getPokeBall(Identifier.of("cobblemon", ballName));
        if (ball != null) pokemon.setCaughtBall(ball);
    }

    public static void setDynamaxLevel(Pokemon pokemon, int level) {
        int max = MegaShowdownBridge.maxDynamaxLevel();
        pokemon.setDmaxLevel(clamp(level, 0, max));
        MegaShowdownBridge.syncDynamax(pokemon);
    }

    public static void setGmaxFactor(Pokemon pokemon, boolean enabled) {
        pokemon.setGmaxFactor(enabled);
    }

    /**
     * Sets the tera type using a resolved {@link TeraType} object directly.
     * Preferred over the string-based overload because it avoids the
     * name-vs-id lookup mismatch that caused silent failures (money
     * withdrawn, tera type unchanged).
     */
    public static void setTeraType(Pokemon pokemon, TeraType type) {
        if (type != null) pokemon.setTeraType(type);
    }

    /** @deprecated Use {@link #setTeraType(Pokemon, TeraType)} instead. */
    @Deprecated
    public static void setTeraType(Pokemon pokemon, String teraName) {
        try {
            TeraType t = TeraTypes.get(teraName);
            if (t != null) pokemon.setTeraType(t);
        } catch (Throwable ignored) {}
    }

    // --- 1.3.0 additions ---

    // Sets the nickname. Passing null resets to the species default
    // (Cobblemon shows the species name when nickname is null).
    public static void setNickname(Pokemon pokemon, net.minecraft.text.Text nickname) {
        try {
            pokemon.setNickname(nickname == null ? null : nickname.copy());
        } catch (Throwable ignored) {}
    }

    // Friendship is capped 0..255 by Cobblemon's config; the `true` arg
    // lets us bypass "can I increment right now" gates that prevent
    // overfeeding-via-item spam. Purchased changes are explicit so the
    // cap should still apply — we pass false to respect natural limits.
    public static void setFriendship(Pokemon pokemon, int value) {
        try {
            pokemon.setFriendship(clamp(value, 0, 255), false);
        } catch (Throwable ignored) {}
    }

    public static void incrementFriendship(Pokemon pokemon, int delta) {
        try {
            if (delta >= 0) pokemon.incrementFriendship(delta, false);
            else pokemon.decrementFriendship(-delta, false);
        } catch (Throwable ignored) {}
    }

    // Clears poison/burn/paralysis/freeze/sleep. No-op if the Pokémon
    // is already healthy (the purchasable's eligibility() hook should
    // prevent the transaction in that case).
    public static void clearStatus(Pokemon pokemon) {
        try {
            pokemon.setStatus(null);
        } catch (Throwable ignored) {}
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
