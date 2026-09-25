package com.antigravity.pokebuilder.gui.admin;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.config.PokeBuilderConfig;
import com.antigravity.pokebuilder.gui.base.BaseGui;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.ModItems;
import com.antigravity.pokebuilder.util.SoundHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class FeaturesAdminGui extends BaseGui {

    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    private final List<Toggle> toggles = new ArrayList<>();

    public FeaturesAdminGui(ServerPlayerEntity player) {
        super(player, 6, Text.literal("\u2756 Admin \u00b7 Features \u2756")
                .formatted(Formatting.DARK_RED, Formatting.BOLD));
        registerToggles();
    }

    private void registerToggles() {
        PokeBuilderConfig.Features f = PokeBuilder.get().config().features;
        toggles.clear();
        toggles.add(new Toggle("IV editor",       ModItems.experienceCandyL(), () -> f.ivEditor,
                v -> f.ivEditor = v));
        toggles.add(new Toggle("IV: Max All",     Items.DIAMOND_BLOCK,         () -> f.ivMaxAll,
                v -> f.ivMaxAll = v));
        toggles.add(new Toggle("EV editor",       ModItems.vitaminFor(0),      () -> f.evEditor,
                v -> f.evEditor = v));
        toggles.add(new Toggle("EV: Reset All",   Items.EMERALD_BLOCK,         () -> f.evResetAll,
                v -> f.evResetAll = v));
        toggles.add(new Toggle("Nature change",   ModItems.natureMint("adamant"), () -> f.nature,
                v -> f.nature = v));
        toggles.add(new Toggle("Ability: Normal", ModItems.abilityCapsule(),   () -> f.ability,
                v -> f.ability = v));
        toggles.add(new Toggle("Ability: Hidden", ModItems.abilityPatch(),     () -> f.abilityHidden,
                v -> f.abilityHidden = v));
        toggles.add(new Toggle("Shiny toggle",    ModItems.shinyStone(),       () -> f.shiny,
                v -> f.shiny = v));
        toggles.add(new Toggle("Gender swap",     Items.POPPY,                 () -> f.gender,
                v -> f.gender = v));
        toggles.add(new Toggle("Caught ball",     ModItems.pokeBallItemByName("poke_ball", Items.SNOWBALL),
                () -> f.ball, v -> f.ball = v));
        toggles.add(new Toggle("Dynamax level",   ModItems.dynamaxCandy(),     () -> f.dynamaxLevel,
                v -> f.dynamaxLevel = v));
        toggles.add(new Toggle("G-Max factor",    ModItems.dynamaxBand(),      () -> f.gmaxFactor,
                v -> f.gmaxFactor = v));
        toggles.add(new Toggle("Tera type",       ModItems.teraShard("normal"), () -> f.teraType,
                v -> f.teraType = v));
        // 1.3.0 features — were missing from this panel; admins had to
        // edit config.conf to flip them. Now in-GUI.
        toggles.add(new Toggle("Nickname",        Items.NAME_TAG,              () -> f.nickname,
                v -> f.nickname = v));
        toggles.add(new Toggle("Friendship",      Items.POPPY,                 () -> f.friendship,
                v -> f.friendship = v));
        toggles.add(new Toggle("Status cure",     Items.HONEY_BOTTLE,          () -> f.statusCure,
                v -> f.statusCure = v));
        toggles.add(new Toggle("PC access",       Items.ENDER_CHEST,           () -> f.pcAccess,
                v -> f.pcAccess = v));
    }

    @Override
    protected void build() {
        fillBorders(PokeBuilder.get().config().looks.borderColor);

        safeSet(4, ItemBuilder.of(ModItems.linkCable())
                .name("Feature Switches", Formatting.GOLD, Formatting.BOLD)
                .lore("Toggle individual purchases on or off.", Formatting.GRAY)
                .lore("Disabled features are hidden from /pb", Formatting.DARK_GRAY)
                .lore("and refused server-side.", Formatting.DARK_GRAY)
                .glow(true).build());

        // Render up to 21 toggles in rows 1-3 interior columns
        // (slots 10-16, 19-25, 28-34). 17 toggles ship today; the extra
        // capacity leaves room to add ~4 more in 1.4.x without
        // re-laying out.
        int[] slots = new int[] {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };
        for (int i = 0; i < toggles.size() && i < slots.length; i++) {
            Toggle t = toggles.get(i);
            boolean on = t.getter.getAsBoolean();
            ItemStack icon = ItemBuilder.of(t.icon)
                    .name(t.label, on ? Formatting.GREEN : Formatting.RED, Formatting.BOLD)
                    .lore("State: " + (on ? "ENABLED" : "DISABLED"),
                            on ? Formatting.GREEN : Formatting.RED)
                    .lore("Click to toggle", Formatting.GRAY)
                    .glow(on)
                    .build();
            safeSet(slots[i], icon);
        }

        safeSet(BACK_SLOT, ItemBuilder.of(Items.ARROW).name("Back", Formatting.YELLOW).build());
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED, Formatting.BOLD).build());
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (!AdminUtils.isAdmin(player)) { close(); return; }
        if (slot == BACK_SLOT) { SoundHelper.back(player); new AdminGui(player).open(); return; }
        if (slot == CLOSE_SLOT) { close(); return; }

        int[] slots = new int[] {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };
        for (int i = 0; i < toggles.size() && i < slots.length; i++) {
            if (slots[i] == slot) {
                Toggle t = toggles.get(i);
                boolean before = t.getter.getAsBoolean();
                t.setter.accept(!before);
                AdminUtils.logAdmin(player, "features." + t.label, String.valueOf(before), String.valueOf(!before));
                SoundHelper.openMenu(player);
                refresh();
                return;
            }
        }
    }

    @Override
    protected void onOpen() { SoundHelper.openMenu(player); }

    private static final class Toggle {
        final String label;
        final Item icon;
        final BooleanSupplier getter;
        final Consumer<Boolean> setter;

        Toggle(String label, Item icon, BooleanSupplier getter, Consumer<Boolean> setter) {
            this.label = label;
            this.icon = icon;
            this.getter = getter;
            this.setter = setter;
        }
    }
}
