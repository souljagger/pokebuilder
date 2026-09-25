package com.antigravity.pokebuilder.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack stack;
    private final List<Text> lore = new ArrayList<>();

    private ItemBuilder(ItemStack stack) {
        this.stack = stack;
    }

    public static ItemBuilder of(net.minecraft.item.Item item) {
        return new ItemBuilder(new ItemStack(item));
    }

    public static ItemBuilder of(ItemStack stack) {
        return new ItemBuilder(stack.copy());
    }

    public static ItemBuilder glass(int dyeColor) {
        net.minecraft.item.Item pane = switch (dyeColor) {
            case 3 -> Items.LIGHT_BLUE_STAINED_GLASS_PANE;
            case 5 -> Items.LIME_STAINED_GLASS_PANE;
            case 7 -> Items.GRAY_STAINED_GLASS_PANE;
            case 10 -> Items.PURPLE_STAINED_GLASS_PANE;
            case 14 -> Items.RED_STAINED_GLASS_PANE;
            case 15 -> Items.BLACK_STAINED_GLASS_PANE;
            default -> Items.GRAY_STAINED_GLASS_PANE;
        };
        return of(pane).name(Text.literal(" "));
    }

    public ItemBuilder name(Text text) {
        MutableText unitalic = Text.empty().append(text).styled(s -> s.withItalic(false));
        stack.set(DataComponentTypes.CUSTOM_NAME, unitalic);
        return this;
    }

    public ItemBuilder name(String text, Formatting... fmt) {
        // Text.literal(null) NPEs on Minecraft 1.21+ — coerce to empty
        // string so a stray null upstream doesn't crash the whole GUI.
        MutableText t = Text.literal(text == null ? "" : text);
        for (Formatting f : fmt) t = t.formatted(f);
        return name(t);
    }

    public ItemBuilder lore(Text line) {
        if (line == null) return this;
        MutableText unitalic = Text.empty().append(line).styled(s -> s.withItalic(false));
        lore.add(unitalic);
        return this;
    }

    public ItemBuilder lore(String line, Formatting... fmt) {
        // Same null-coercion as name(): a null lore line means "skip
        // this lore entry", not "crash the GUI".
        if (line == null) return this;
        MutableText t = Text.literal(line);
        for (Formatting f : fmt) t = t.formatted(f);
        return lore(t);
    }

    public ItemBuilder lore(Collection<Text> lines) {
        for (Text l : lines) lore(l);
        return this;
    }

    public ItemBuilder loreLines(String... lines) {
        for (String line : Arrays.asList(lines)) lore(line, Formatting.GRAY);
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (glow) {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return this;
    }

    public ItemBuilder count(int count) {
        stack.setCount(Math.max(1, Math.min(64, count)));
        return this;
    }

    public ItemStack build() {
        if (!lore.isEmpty()) {
            stack.set(DataComponentTypes.LORE, new LoreComponent(new ArrayList<>(lore)));
        }
        return stack;
    }
}
