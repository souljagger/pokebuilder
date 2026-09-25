package com.antigravity.pokebuilder.gui.admin;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.config.PokeBuilderConfig;
import com.antigravity.pokebuilder.gui.base.BaseGui;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.SoundHelper;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

public class SecurityAdminGui extends BaseGui {

    private static final int COOLDOWN_SLOT = 11;
    private static final int COOLDOWN_MINUS = 12;
    private static final int COOLDOWN_PLUS = 13;
    private static final int RATE_SLOT = 20;
    private static final int RATE_MINUS = 21;
    private static final int RATE_PLUS = 22;
    private static final int CREATIVE_SLOT = 29;
    private static final int FAINTED_SLOT = 31;
    private static final int BYPASS_GATE_SLOT = 33;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    public SecurityAdminGui(ServerPlayerEntity player) {
        super(player, 6, PokeBuilder.get().config().messages.get("gui.title.admin.security"));
    }

    @Override
    protected void build() {
        fillBorders(PokeBuilder.get().config().looks.borderColor);
        PokeBuilderConfig.Security s = PokeBuilder.get().config().security;

        safeSet(COOLDOWN_SLOT, ItemBuilder.of(Items.CLOCK)
                .name("Click cooldown: " + s.clickCooldownMs + " ms", Formatting.GOLD, Formatting.BOLD)
                .lore("Min 100, max 5000", Formatting.GRAY).build());
        safeSet(COOLDOWN_MINUS, stepper(false, "-100 ms (shift -500)"));
        safeSet(COOLDOWN_PLUS, stepper(true, "+100 ms (shift +500)"));

        safeSet(RATE_SLOT, ItemBuilder.of(Items.REPEATER)
                .name("Max mods/min: " + s.maxModificationsPerMinute, Formatting.GOLD, Formatting.BOLD)
                .lore("Min 1, max 1000", Formatting.GRAY).build());
        safeSet(RATE_MINUS, stepper(false, "-1 (shift -10)"));
        safeSet(RATE_PLUS, stepper(true, "+1 (shift +10)"));

        safeSet(CREATIVE_SLOT, ItemBuilder.of(s.allowCreativeMode ? Items.LIME_WOOL : Items.RED_WOOL)
                .name("Allow creative mode: " + (s.allowCreativeMode ? "YES" : "NO"),
                        s.allowCreativeMode ? Formatting.GREEN : Formatting.RED, Formatting.BOLD)
                .lore("Click to toggle", Formatting.GRAY).build());

        safeSet(FAINTED_SLOT, ItemBuilder.of(s.allowFaintedPokemon ? Items.LIME_WOOL : Items.RED_WOOL)
                .name("Allow fainted Pokémon: " + (s.allowFaintedPokemon ? "YES" : "NO"),
                        s.allowFaintedPokemon ? Formatting.GREEN : Formatting.RED, Formatting.BOLD)
                .lore("Click to toggle", Formatting.GRAY).build());

        // Master gate for the bypass.* permission family. Default OFF
        // — even if LuckPerms grants the bypass node (e.g. via a
        // wildcard like `pokebuilder.*` on an admin group), the
        // transaction layer treats it as not-granted unless this is on.
        safeSet(BYPASS_GATE_SLOT, ItemBuilder.of(s.allowBypassPermissions ? Items.LIME_WOOL : Items.RED_WOOL)
                .name("Allow bypass perms: " + (s.allowBypassPermissions ? "YES" : "NO"),
                        s.allowBypassPermissions ? Formatting.YELLOW : Formatting.GREEN, Formatting.BOLD)
                .lore("Master gate for pokebuilder.bypass.*", Formatting.GRAY)
                .lore(s.allowBypassPermissions
                        ? "↑ ON: anyone with the perm bypasses cost/cooldown"
                        : "↓ OFF: bypass perms are ignored (safe default)",
                        s.allowBypassPermissions ? Formatting.YELLOW : Formatting.GREEN)
                .lore("Click to toggle", Formatting.GRAY).build());

        safeSet(BACK_SLOT, ItemBuilder.of(Items.ARROW).name("Back", Formatting.YELLOW).build());
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED).build());
    }

    private net.minecraft.item.ItemStack stepper(boolean plus, String label) {
        return ItemBuilder.of(plus ? Items.LIME_CONCRETE : Items.RED_CONCRETE)
                .name(label, plus ? Formatting.GREEN : Formatting.RED, Formatting.BOLD).build();
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (!AdminUtils.isAdmin(player)) { close(); return; }
        PokeBuilderConfig.Security s = PokeBuilder.get().config().security;
        boolean shift = action == SlotActionType.QUICK_MOVE;

        switch (slot) {
            case COOLDOWN_MINUS -> {
                long old = s.clickCooldownMs;
                s.clickCooldownMs = AdminUtils.clampLong(s.clickCooldownMs - (shift ? 500 : 100), 100, 5000);
                AdminUtils.logAdmin(player, "security.clickCooldownMs", String.valueOf(old), String.valueOf(s.clickCooldownMs));
            }
            case COOLDOWN_PLUS -> {
                long old = s.clickCooldownMs;
                s.clickCooldownMs = AdminUtils.clampLong(s.clickCooldownMs + (shift ? 500 : 100), 100, 5000);
                AdminUtils.logAdmin(player, "security.clickCooldownMs", String.valueOf(old), String.valueOf(s.clickCooldownMs));
            }
            case RATE_MINUS -> {
                int old = s.maxModificationsPerMinute;
                s.maxModificationsPerMinute = AdminUtils.clampInt(s.maxModificationsPerMinute - (shift ? 10 : 1), 1, 1000);
                AdminUtils.logAdmin(player, "security.maxModificationsPerMinute", String.valueOf(old), String.valueOf(s.maxModificationsPerMinute));
            }
            case RATE_PLUS -> {
                int old = s.maxModificationsPerMinute;
                s.maxModificationsPerMinute = AdminUtils.clampInt(s.maxModificationsPerMinute + (shift ? 10 : 1), 1, 1000);
                AdminUtils.logAdmin(player, "security.maxModificationsPerMinute", String.valueOf(old), String.valueOf(s.maxModificationsPerMinute));
            }
            case CREATIVE_SLOT -> {
                s.allowCreativeMode = !s.allowCreativeMode;
                AdminUtils.logAdmin(player, "security.allowCreativeMode", String.valueOf(!s.allowCreativeMode), String.valueOf(s.allowCreativeMode));
            }
            case FAINTED_SLOT -> {
                s.allowFaintedPokemon = !s.allowFaintedPokemon;
                AdminUtils.logAdmin(player, "security.allowFaintedPokemon", String.valueOf(!s.allowFaintedPokemon), String.valueOf(s.allowFaintedPokemon));
            }
            case BYPASS_GATE_SLOT -> {
                s.allowBypassPermissions = !s.allowBypassPermissions;
                AdminUtils.logAdmin(player, "security.allowBypassPermissions",
                        String.valueOf(!s.allowBypassPermissions), String.valueOf(s.allowBypassPermissions));
            }
            case BACK_SLOT -> { SoundHelper.back(player); new AdminGui(player).open(); return; }
            case CLOSE_SLOT -> { close(); return; }
            default -> { return; }
        }
        SoundHelper.openMenu(player);
        refresh();
    }

    @Override
    protected void onOpen() { SoundHelper.openMenu(player); }
}
