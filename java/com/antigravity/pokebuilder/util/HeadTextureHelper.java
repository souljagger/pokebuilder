package com.antigravity.pokebuilder.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.UUID;

public final class HeadTextureHelper {

    private HeadTextureHelper() {}

    public static ItemStack skullWithTexture(String base64Texture) {
        ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
        if (base64Texture == null || base64Texture.isBlank()) return skull;

        try {
            GameProfile profile = new GameProfile(UUID.randomUUID(), "PokeBuilderHead");
            profile.getProperties().put("textures", new Property("textures", base64Texture));
            skull.set(DataComponentTypes.PROFILE, new ProfileComponent(profile));
        } catch (Throwable ignored) {}
        return skull;
    }

    public static ItemStack fallback() {
        return new ItemStack(Items.PLAYER_HEAD);
    }
}
