package com.antigravity.pokebuilder.util;

import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokeball.PokeBall;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// Looks up Cobblemon and Mega Showdown items by registry id at runtime, with
// vanilla fallbacks so the mod still renders sensibly if either dep is
// missing or the id has changed in a future version. Results are cached so
// each lookup happens at most once per server lifetime.
public final class ModItems {

    private static final ConcurrentMap<String, Item> CACHE = new ConcurrentHashMap<>();

    private ModItems() {}

    public static Item get(String namespace, String path, Item fallback) {
        String key = namespace + ":" + path;
        Item cached = CACHE.get(key);
        if (cached != null) return cached;
        Item resolved = fallback;
        try {
            Item i = Registries.ITEM.get(Identifier.of(namespace, path));
            if (i != null && i != Items.AIR) resolved = i;
        } catch (Throwable ignored) {}
        CACHE.put(key, resolved);
        return resolved;
    }

    public static Item cobblemon(String path, Item fallback) {
        return get("cobblemon", path, fallback);
    }

    public static Item megaShowdown(String path, Item fallback) {
        return get("mega_showdown", path, fallback);
    }

    // ---- Convenience accessors -----------------------------------------

    public static Item rareCandy()      { return cobblemon("rare_candy", Items.GOLDEN_APPLE); }
    public static Item experienceCandyL() { return cobblemon("experience_candy_l", Items.GOLDEN_CARROT); }

    public static Item shinyStone()     { return cobblemon("shiny_stone", Items.NETHER_STAR); }
    public static Item linkCable()      { return cobblemon("link_cable", Items.LEAD); }

    public static Item abilityCapsule() { return cobblemon("ability_capsule", Items.ENDER_EYE); }
    public static Item abilityPatch()   { return cobblemon("ability_patch", Items.ENDER_PEARL); }
    public static Item abilityShield()  { return cobblemon("ability_shield", Items.SHIELD); }

    // EV vitamins — mapped to the six Pokémon stats in their canonical order.
    public static Item vitaminFor(int statIndex) {
        return switch (statIndex) {
            case 0 -> cobblemon("hp_up", Items.GOLDEN_APPLE);
            case 1 -> cobblemon("protein", Items.IRON_SWORD);
            case 2 -> cobblemon("iron", Items.SHIELD);
            case 3 -> cobblemon("calcium", Items.BLAZE_ROD);
            case 4 -> cobblemon("zinc", Items.TURTLE_SCUTE);
            case 5 -> cobblemon("carbos", Items.SUGAR);
            default -> Items.PAPER;
        };
    }

    // Hyper-training "candies" — same six-stat mapping, used for the IV editor.
    public static Item hyperCandyFor(int statIndex) {
        return switch (statIndex) {
            case 0 -> cobblemon("health_candy", Items.GOLDEN_APPLE);
            case 1 -> cobblemon("mighty_candy", Items.IRON_SWORD);
            case 2 -> cobblemon("tough_candy", Items.SHIELD);
            case 3 -> cobblemon("smart_candy", Items.BLAZE_ROD);
            case 4 -> cobblemon("courage_candy", Items.TURTLE_SCUTE);
            case 5 -> cobblemon("quick_candy", Items.SUGAR);
            default -> Items.PAPER;
        };
    }

    public static Item natureMint(String natureName) {
        if (natureName == null) return Items.WRITABLE_BOOK;
        String path = natureName.toLowerCase().trim() + "_mint";
        return cobblemon(path, Items.WRITABLE_BOOK);
    }

    public static Item teraShard(String typeName) {
        if (typeName == null) return Items.AMETHYST_SHARD;
        String path = typeName.toLowerCase().trim() + "_tera_shard";
        return megaShowdown(path, Items.AMETHYST_SHARD);
    }

    public static Item dynamaxCandy() { return megaShowdown("dynamax_candy", Items.DRAGON_BREATH); }
    public static Item dynamaxBand()  { return megaShowdown("dynamax_band", Items.DRAGON_HEAD); }
    public static Item maxMushroom()  { return megaShowdown("max_mushroom", Items.RED_MUSHROOM); }

    public static Item keystone()     { return megaShowdown("keystone", Items.AMETHYST_SHARD); }

    // ---- Pokemon icon helpers -----------------------------------------

    // Render the actual Pokemon model in an inventory slot via Cobblemon's
    // PokemonItem. Falls back to a poke ball if the call fails for any reason
    // (e.g. shaded/relocated PokemonItem class on a non-standard build).
    public static ItemStack pokemonIcon(Pokemon pokemon) {
        try {
            ItemStack stack = PokemonItem.from(pokemon);
            if (stack != null && !stack.isEmpty()) return stack;
        } catch (Throwable ignored) {}
        return new ItemStack(caughtBallItem(pokemon, Items.SNOWBALL));
    }

    public static Item caughtBallItem(Pokemon pokemon, Item fallback) {
        try {
            PokeBall ball = pokemon.getCaughtBall();
            if (ball == null) return fallback;
            Item i = ball.item();
            if (i != null && i != Items.AIR) return i;
            // Fallback: lookup by registry id from ball.getName()
            return get(ball.getName().getNamespace(), ball.getName().getPath(), fallback);
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static Item pokeBallItemByName(String ballPath, Item fallback) {
        return cobblemon(ballPath, fallback);
    }
}
