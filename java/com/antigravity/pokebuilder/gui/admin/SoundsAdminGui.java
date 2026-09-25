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

public class SoundsAdminGui extends BaseGui {

    private static final int ENABLED_SLOT = 11;
    private static final int VOLUME_SLOT = 20;
    private static final int VOLUME_MINUS = 21;
    private static final int VOLUME_PLUS = 22;
    private static final int PITCH_SLOT = 29;
    private static final int PITCH_MINUS = 30;
    private static final int PITCH_PLUS = 31;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    public SoundsAdminGui(ServerPlayerEntity player) {
        super(player, 6, PokeBuilder.get().config().messages.get("gui.title.admin.sounds"));
    }

    @Override
    protected void build() {
        fillBorders(PokeBuilder.get().config().looks.borderColor);
        PokeBuilderConfig.Sounds s = PokeBuilder.get().config().sounds;

        safeSet(ENABLED_SLOT, ItemBuilder.of(s.enabled ? Items.NOTE_BLOCK : Items.BARRIER)
                .name("Sounds: " + (s.enabled ? "ON" : "OFF"),
                        s.enabled ? Formatting.GREEN : Formatting.RED, Formatting.BOLD)
                .lore("Click to toggle", Formatting.GRAY).build());

        safeSet(VOLUME_SLOT, ItemBuilder.of(Items.JUKEBOX)
                .name(String.format("Volume: %.2f", s.volume), Formatting.GOLD, Formatting.BOLD)
                .lore("Range 0.00 - 2.00", Formatting.GRAY).build());
        safeSet(VOLUME_MINUS, ItemBuilder.of(Items.RED_CONCRETE).name("-0.10 (shift -0.50)", Formatting.RED).build());
        safeSet(VOLUME_PLUS, ItemBuilder.of(Items.LIME_CONCRETE).name("+0.10 (shift +0.50)", Formatting.GREEN).build());

        safeSet(PITCH_SLOT, ItemBuilder.of(Items.BELL)
                .name(String.format("Pitch: %.2f", s.pitch), Formatting.GOLD, Formatting.BOLD)
                .lore("Range 0.50 - 2.00", Formatting.GRAY).build());
        safeSet(PITCH_MINUS, ItemBuilder.of(Items.RED_CONCRETE).name("-0.10 (shift -0.50)", Formatting.RED).build());
        safeSet(PITCH_PLUS, ItemBuilder.of(Items.LIME_CONCRETE).name("+0.10 (shift +0.50)", Formatting.GREEN).build());

        safeSet(BACK_SLOT, ItemBuilder.of(Items.ARROW).name("Back", Formatting.YELLOW).build());
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED).build());
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (!AdminUtils.isAdmin(player)) { close(); return; }
        PokeBuilderConfig.Sounds s = PokeBuilder.get().config().sounds;
        boolean shift = action == SlotActionType.QUICK_MOVE;
        float step = shift ? 0.50f : 0.10f;

        switch (slot) {
            case ENABLED_SLOT -> {
                s.enabled = !s.enabled;
                AdminUtils.logAdmin(player, "sounds.enabled", String.valueOf(!s.enabled), String.valueOf(s.enabled));
            }
            case VOLUME_MINUS -> {
                float old = s.volume;
                s.volume = AdminUtils.clampFloat(s.volume - step, 0f, 2f);
                AdminUtils.logAdmin(player, "sounds.volume", String.valueOf(old), String.valueOf(s.volume));
            }
            case VOLUME_PLUS -> {
                float old = s.volume;
                s.volume = AdminUtils.clampFloat(s.volume + step, 0f, 2f);
                AdminUtils.logAdmin(player, "sounds.volume", String.valueOf(old), String.valueOf(s.volume));
            }
            case PITCH_MINUS -> {
                float old = s.pitch;
                s.pitch = AdminUtils.clampFloat(s.pitch - step, 0.5f, 2f);
                AdminUtils.logAdmin(player, "sounds.pitch", String.valueOf(old), String.valueOf(s.pitch));
            }
            case PITCH_PLUS -> {
                float old = s.pitch;
                s.pitch = AdminUtils.clampFloat(s.pitch + step, 0.5f, 2f);
                AdminUtils.logAdmin(player, "sounds.pitch", String.valueOf(old), String.valueOf(s.pitch));
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
