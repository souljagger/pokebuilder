package com.antigravity.pokebuilder.service;

import com.antigravity.pokebuilder.PokeBuilder;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

// Mega Showdown is an OPTIONAL runtime dep. Dynamax level, G-Max factor, and
// Tera type are all native Cobblemon Pokemon fields, so the buy flow works
// without Mega Showdown installed. The only thing Mega Showdown adds here is
// its species-feature display overlay that shows the level on the summary
// screen; when present, we call its GlobalFeatureManager.update(pokemon) via
// reflection so the overlay refreshes immediately. No compile-time dependency.
public final class MegaShowdownBridge {

    private static volatile boolean probed;
    private static volatile Method updateMethod;
    private static volatile boolean megaShowdownPresent;

    private MegaShowdownBridge() {}

    public static boolean isPresent() {
        probe();
        return megaShowdownPresent;
    }

    public static int maxDynamaxLevel() {
        try {
            return Cobblemon.INSTANCE.getConfig().getMaxDynamaxLevel();
        } catch (Throwable t) {
            return 10;
        }
    }

    public static void syncDynamax(Pokemon pokemon) {
        probe();
        Method m = updateMethod;
        if (m == null) return;
        try {
            m.invoke(null, pokemon);
        } catch (Throwable t) {
            PokeBuilder.LOGGER.debug("[PokeBuilder] Mega Showdown sync failed (non-fatal): {}", t.toString());
        }
    }

    private static void probe() {
        if (probed) return;
        synchronized (MegaShowdownBridge.class) {
            if (probed) return;
            probed = true;
            megaShowdownPresent = FabricLoader.getInstance().isModLoaded("mega_showdown");
            if (!megaShowdownPresent) return;
            try {
                Class<?> clazz = Class.forName(
                        "com.github.yajatkaul.mega_showdown.cobblemon.features.GlobalFeatureManager");
                updateMethod = clazz.getMethod("update", Pokemon.class);
                PokeBuilder.LOGGER.info("[PokeBuilder] Mega Showdown detected — dynamax display overlay will sync.");
            } catch (Throwable t) {
                PokeBuilder.LOGGER.warn("[PokeBuilder] Mega Showdown present but feature API not found: {}", t.toString());
            }
        }
    }
}
