package com.antigravity.pokebuilder.gui.admin;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.gui.base.BaseGui;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.ModItems;
import com.antigravity.pokebuilder.util.SoundHelper;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class AdminGui extends BaseGui {

    private static final int PRICES_SLOT = 10;
    private static final int FEATURES_SLOT = 11;
    private static final int SECURITY_SLOT = 13;
    private static final int RESTRICTIONS_SLOT = 15;
    private static final int SOUNDS_SLOT = 16;
    private static final int MESSAGES_SLOT = 22;
    private static final int SAVE_SLOT = 40;
    private static final int RELOAD_SLOT = 42;
    private static final int CLOSE_SLOT = 49;

    public AdminGui(ServerPlayerEntity player) {
        super(player, 6, PokeBuilder.get().config().messages.get("gui.title.admin"));
    }

    @Override
    protected void build() {
        fillBorders(PokeBuilder.get().config().looks.borderColor);

        // Banner header across slot 4
        safeSet(4, ItemBuilder.of(Items.NETHER_STAR)
                .name("\u2756 Admin Control Panel \u2756", Formatting.GOLD, Formatting.BOLD)
                .lore("Live-edit every PokeBuilder setting", Formatting.GRAY)
                .lore("Click SAVE TO DISK to persist", Formatting.DARK_GRAY)
                .glow(true).build());

        safeSet(PRICES_SLOT, ItemBuilder.of(Items.GOLD_INGOT)
                .name("Prices", Formatting.GOLD, Formatting.BOLD)
                .lore("Adjust every cost (+/- buttons)", Formatting.GRAY)
                .lore("Shift-click for x10 steps", Formatting.DARK_GRAY).build());

        safeSet(FEATURES_SLOT, ItemBuilder.of(ModItems.linkCable())
                .name("Features", Formatting.AQUA, Formatting.BOLD)
                .lore("Enable / disable individual purchases", Formatting.GRAY)
                .lore("Disabled features are hidden from /pb", Formatting.DARK_GRAY)
                .build());

        safeSet(SECURITY_SLOT, ItemBuilder.of(Items.IRON_BARS)
                .name("Security", Formatting.RED, Formatting.BOLD)
                .lore("Cooldown, rate limit, creative", Formatting.GRAY)
                .lore("Fainted-Pokémon allow toggle", Formatting.DARK_GRAY)
                .build());

        safeSet(RESTRICTIONS_SLOT, ItemBuilder.of(Items.CRYING_OBSIDIAN)
                .name("Restrictions", Formatting.DARK_RED, Formatting.BOLD)
                .lore("Legendary / Mythical / Ultra Beast", Formatting.GRAY)
                .lore("Paradox toggle + blocked species list", Formatting.DARK_GRAY)
                .build());

        safeSet(SOUNDS_SLOT, ItemBuilder.of(Items.NOTE_BLOCK)
                .name("Sounds", Formatting.AQUA, Formatting.BOLD)
                .lore("Enable / volume / pitch", Formatting.GRAY).build());

        safeSet(MESSAGES_SLOT, ItemBuilder.of(Items.WRITABLE_BOOK)
                .name("Messages", Formatting.LIGHT_PURPLE, Formatting.BOLD)
                .lore("Edit every user-facing string", Formatting.GRAY)
                .lore("Click, then type in chat", Formatting.DARK_GRAY).build());

        boolean dirty = PokeBuilder.get().isDirty();
        safeSet(SAVE_SLOT, ItemBuilder.of(dirty ? Items.LIME_CONCRETE : Items.LIME_STAINED_GLASS)
                .name("SAVE TO DISK", Formatting.GREEN, Formatting.BOLD)
                .lore(dirty ? "\u26a0 You have unsaved changes" : "Nothing to save",
                        dirty ? Formatting.YELLOW : Formatting.DARK_GRAY)
                .lore("Writes config.conf atomically", Formatting.DARK_GRAY)
                .glow(dirty).build());

        safeSet(RELOAD_SLOT, ItemBuilder.of(Items.BOOK)
                .name("RELOAD FROM DISK", Formatting.AQUA, Formatting.BOLD)
                .lore("Re-reads config.conf from disk", Formatting.GRAY)
                .lore("Unsaved in-memory edits will be lost", Formatting.RED)
                .build());

        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER)
                .name("Close", Formatting.RED, Formatting.BOLD).build());
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (!AdminUtils.isAdmin(player)) { close(); return; }

        switch (slot) {
            case PRICES_SLOT -> new PricesAdminGui(player).open();
            case FEATURES_SLOT -> new FeaturesAdminGui(player).open();
            case SECURITY_SLOT -> new SecurityAdminGui(player).open();
            case RESTRICTIONS_SLOT -> new RestrictionsAdminGui(player).open();
            case SOUNDS_SLOT -> new SoundsAdminGui(player).open();
            case MESSAGES_SLOT -> new MessagesAdminGui(player).open();
            case SAVE_SLOT -> {
                if (PokeBuilder.get().saveConfig()) {
                    player.sendMessage(PokeBuilder.get().config().messages.get("command.saved"), false);
                    SoundHelper.purchaseSuccess(player);
                } else {
                    player.sendMessage(PokeBuilder.get().config().messages.get("command.save_failed"), false);
                    SoundHelper.purchaseFailed(player);
                }
                refresh();
            }
            case RELOAD_SLOT -> {
                PokeBuilder.get().reloadConfig();
                player.sendMessage(PokeBuilder.get().config().messages.get("command.reloaded"), false);
                SoundHelper.openMenu(player);
                refresh();
            }
            case CLOSE_SLOT -> close();
            default -> {}
        }
    }

    @Override
    protected void onOpen() {
        SoundHelper.openMenu(player);
    }

    static Text title(String key) {
        return PokeBuilder.get().config().messages.get(key);
    }
}
