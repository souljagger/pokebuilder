package com.antigravity.pokebuilder.util;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.economy.EconomyHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public final class TextHelper {

    private TextHelper() {}

    public static Text plain(String s) {
        return Text.literal(s);
    }

    public static Text colored(String s, Formatting... fmt) {
        MutableText t = Text.literal(s);
        for (Formatting f : fmt) t = t.formatted(f);
        return t;
    }

    public static Text gray(String s) {
        return colored(s, Formatting.GRAY);
    }

    public static Text gold(String s) {
        return colored(s, Formatting.GOLD);
    }

    public static Text red(String s) {
        return colored(s, Formatting.RED);
    }

    public static Text green(String s) {
        return colored(s, Formatting.GREEN);
    }

    public static String formatMoney(BigDecimal amount) {
        String fmt = PokeBuilder.get().config().economy.format;
        try {
            return new DecimalFormat(fmt).format(amount);
        } catch (Exception e) {
            return amount.toPlainString();
        }
    }

    public static String currencyName() {
        String override = PokeBuilder.get().config().economy.displayName;
        if (override != null && !override.isBlank()) return override;
        try {
            if (EconomyHandler.currency() != null) {
                Component plural = EconomyHandler.currency().plural();
                if (plural != null) {
                    return PlainTextComponentSerializer.plainText().serialize(plural);
                }
            }
        } catch (Throwable ignored) {}
        return "coins";
    }
}
