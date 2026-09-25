package com.antigravity.pokebuilder.service;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.config.PokeBuilderConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

// Centralizes per-feature on/off enforcement so a single switch in the admin
// panel actually closes off every code path: GUI rendering, GUI navigation,
// and the transaction service itself. Defense in depth — if any layer is
// bypassed (spoofed click, scripted click, plugin) the next one still refuses.
public final class FeatureGate {

    public enum Feature {
        IV_EDITOR,
        IV_MAX_ALL,
        EV_EDITOR,
        EV_RESET_ALL,
        NATURE,
        ABILITY,
        ABILITY_HIDDEN,
        SHINY,
        GENDER,
        BALL,
        DYNAMAX_LEVEL,
        GMAX_FACTOR,
        TERA_TYPE,
        NICKNAME,
        FRIENDSHIP,
        STATUS_CURE,
        PC_ACCESS
    }

    private FeatureGate() {}

    public static boolean enabled(Feature f) {
        if (f == null) return true;
        try {
            PokeBuilderConfig.Features feat = PokeBuilder.get().config().features;
            return switch (f) {
                case IV_EDITOR -> feat.ivEditor;
                case IV_MAX_ALL -> feat.ivMaxAll;
                case EV_EDITOR -> feat.evEditor;
                case EV_RESET_ALL -> feat.evResetAll;
                case NATURE -> feat.nature;
                case ABILITY -> feat.ability;
                case ABILITY_HIDDEN -> feat.abilityHidden;
                case SHINY -> feat.shiny;
                case GENDER -> feat.gender;
                case BALL -> feat.ball;
                case DYNAMAX_LEVEL -> feat.dynamaxLevel;
                case GMAX_FACTOR -> feat.gmaxFactor;
                case TERA_TYPE -> feat.teraType;
                case NICKNAME -> feat.nickname;
                case FRIENDSHIP -> feat.friendship;
                case STATUS_CURE -> feat.statusCure;
                case PC_ACCESS -> feat.pcAccess;
            };
        } catch (Throwable t) {
            return true;
        }
    }

    // Returns true if the feature is enabled. Otherwise sends the player a
    // standard "feature disabled" message and returns false. Use at any GUI
    // entry point or action handler that should refuse the disabled action.
    public static boolean enforce(Feature f, ServerPlayerEntity player) {
        if (enabled(f)) return true;
        if (player != null) {
            player.sendMessage(Text.literal("That feature is currently disabled by the server.")
                    .formatted(Formatting.RED), false);
        }
        return false;
    }
}
