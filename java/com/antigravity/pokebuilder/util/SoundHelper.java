package com.antigravity.pokebuilder.util;

import com.antigravity.pokebuilder.PokeBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

public final class SoundHelper {

    private SoundHelper() {}

    public static void play(ServerPlayerEntity player, SoundEvent sound) {
        var cfg = PokeBuilder.get().config().sounds;
        if (!cfg.enabled) return;
        player.playSoundToPlayer(sound, SoundCategory.MASTER, cfg.volume, cfg.pitch);
    }

    public static void openMenu(ServerPlayerEntity p) { play(p, SoundEvents.UI_BUTTON_CLICK.value()); }
    public static void selectPokemon(ServerPlayerEntity p) { play(p, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP); }
    public static void purchaseSuccess(ServerPlayerEntity p) { play(p, SoundEvents.ENTITY_PLAYER_LEVELUP); }
    public static void purchaseFailed(ServerPlayerEntity p) { play(p, SoundEvents.ENTITY_VILLAGER_NO); }
    public static void shinyOn(ServerPlayerEntity p) { play(p, SoundEvents.ENTITY_PLAYER_LEVELUP); }
    public static void shinyOff(ServerPlayerEntity p) { play(p, SoundEvents.BLOCK_FIRE_EXTINGUISH); }
    public static void back(ServerPlayerEntity p) { play(p, SoundEvents.UI_BUTTON_CLICK.value()); }
}
