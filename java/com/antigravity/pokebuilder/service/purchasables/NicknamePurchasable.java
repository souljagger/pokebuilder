package com.antigravity.pokebuilder.service.purchasables;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.PokeBuilderConstants;
import com.antigravity.pokebuilder.admin.ChatInputManager;
import com.antigravity.pokebuilder.service.CobblemonBridge;
import com.antigravity.pokebuilder.service.FeatureGate;
import com.antigravity.pokebuilder.service.PokemonModifier;
import com.antigravity.pokebuilder.service.Purchasable;
import com.antigravity.pokebuilder.service.TransactionService;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.PermissionHelper;
import com.antigravity.pokebuilder.util.TextHelper;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.UUID;

// Chat-input purchasable. The player types a new nickname (or "cancel",
// or "reset") — server validates length ≤ 16, strips color codes for
// non-admins, and applies via PokemonModifier.setNickname.
public final class NicknamePurchasable implements Purchasable {

    private static final int MAX_LENGTH = 16;
    private static final String CHAT_CATEGORY = "pokebuilder:nickname";

    @Override public String id() { return "nickname"; }
    @Override public FeatureGate.Feature feature() { return FeatureGate.Feature.NICKNAME; }
    @Override public String permission() { return PokeBuilderConstants.PERM_MOD_NICKNAME; }
    @Override public int editorSlot() { return 20; }

    @Override
    public ItemStack icon(Pokemon pokemon, ServerPlayerEntity who) {
        BigDecimal cost = PokeBuilder.get().config().prices.nickname;
        return ItemBuilder.of(Items.NAME_TAG)
                .name("Nickname", Formatting.GOLD, Formatting.BOLD)
                .lore("Current: " + currentName(pokemon), Formatting.GRAY)
                .lore("Type in chat after clicking.", Formatting.DARK_GRAY)
                .lore("Cost: " + TextHelper.formatMoney(cost) + " " + TextHelper.currencyName(),
                        Formatting.YELLOW)
                .build();
    }

    private String currentName(Pokemon p) {
        try {
            Text nick = p.getNickname();
            if (nick != null) {
                String s = nick.getString();
                if (s != null && !s.isBlank()) return s;
            }
        } catch (Throwable ignored) {}
        try {
            return p.getSpecies().getName();
        } catch (Throwable t) { return "—"; }
    }

    @Override
    public BigDecimal basePrice(Pokemon pokemon, ServerPlayerEntity who) {
        return PokeBuilder.get().config().prices.nickname;
    }

    @Override
    public void invoke(ServerPlayerEntity player, UUID pokemonId) {
        // Open a chat prompt. The validator runs before the transaction
        // and keeps the prompt alive on rejection (short nickname, empty,
        // bad color codes).
        boolean isOp = player.hasPermissionLevel(2);
        ChatInputManager.request(player, "Nickname", CHAT_CATEGORY,
                text -> validate(text, isOp),
                text -> applyOrReset(player, pokemonId, text, isOp),
                () -> player.sendMessage(
                        PokeBuilder.get().config().messages.get("admin.chat_cancelled"), false));
    }

    private boolean validate(String input, boolean isOp) {
        if (input == null) return false;
        String t = input.trim();
        if (t.isEmpty()) return false;
        if (t.equalsIgnoreCase("reset")) return true;
        if (t.length() > MAX_LENGTH) return false;
        if (!isOp) {
            // Strip color codes — non-admins can't inject formatting.
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c == '\u00A7') return false;
                if (c == '&' && i + 1 < t.length()) {
                    char code = Character.toLowerCase(t.charAt(i + 1));
                    if ("0123456789abcdefklmnor".indexOf(code) >= 0) return false;
                }
            }
        }
        return true;
    }

    private void applyOrReset(ServerPlayerEntity player, UUID pokemonId, String text, boolean isOp) {
        String trimmed = text.trim();
        BigDecimal cost = PokeBuilder.get().config().prices.nickname;

        if (trimmed.equalsIgnoreCase("reset")) {
            // Free reset — still atomic so party/PC identity check runs.
            TransactionService.execute(player, pokemonId, feature(),
                    id().toUpperCase(), "reset", BigDecimal.ZERO,
                    pm -> PokemonModifier.setNickname(pm, null));
            player.sendMessage(PokeBuilder.get().config().messages.get("nickname.reset"), false);
            return;
        }

        // Store the final text — colorized for admins, stripped for
        // everyone else. PokemonModifier.setNickname takes a Text so
        // we preserve formatting for OPs.
        MutableText value = isOp
                ? com.antigravity.pokebuilder.messages.PokeBuilderMessages.colorize(trimmed)
                : Text.literal(trimmed);

        TransactionService.Result r = TransactionService.execute(player, pokemonId, feature(),
                id().toUpperCase(), trimmed, cost,
                pm -> PokemonModifier.setNickname(pm, value));
        if (r == TransactionService.Result.SUCCESS) {
            player.sendMessage(PokeBuilder.get().config().messages.get("nickname.set", trimmed), false);
        }
    }

    // Safety net — resolve an optional runtime permission check from
    // the helper so GUI-layer gating matches what TransactionService
    // enforces. Not currently called from the class body but kept on
    // hand for future admin-audit flows.
    @SuppressWarnings("unused")
    private boolean hasPermission(ServerPlayerEntity p) {
        return PermissionHelper.has(p, permission(), 0);
    }

    // Access the bridge only at invoke time so tests can mock the
    // Cobblemon dep without needing a live Pokemon instance.
    @SuppressWarnings("unused")
    private Pokemon find(ServerPlayerEntity p, UUID id) {
        return CobblemonBridge.findByUuid(p, id);
    }
}
