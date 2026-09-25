package com.antigravity.pokebuilder.gui.admin;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.admin.ChatInputManager;
import com.antigravity.pokebuilder.gui.base.BaseGui;
import com.antigravity.pokebuilder.messages.PokeBuilderMessages;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.SoundHelper;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class MessagesAdminGui extends BaseGui {

    private static final int PER_PAGE = 28;
    private static final int BACK_SLOT = 45;
    private static final int PREV_SLOT = 48;
    private static final int PAGE_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int CLOSE_SLOT = 53;

    private int page = 0;
    private final List<String> keys;

    public MessagesAdminGui(ServerPlayerEntity player) {
        super(player, 6, PokeBuilder.get().config().messages.get("gui.title.admin.messages"));
        this.keys = new ArrayList<>(PokeBuilder.get().config().messages.all().keySet());
    }

    @Override
    protected void build() {
        fillBorders(PokeBuilder.get().config().looks.borderColor);
        PokeBuilderMessages msgs = PokeBuilder.get().config().messages;

        int totalPages = Math.max(1, (int) Math.ceil(keys.size() / (double) PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int start = page * PER_PAGE;
        int end = Math.min(start + PER_PAGE, keys.size());

        int[] slots = contentSlots();
        for (int i = start; i < end; i++) {
            String key = keys.get(i);
            String raw = msgs.raw(key);
            String preview = raw.length() > 40 ? raw.substring(0, 37) + "..." : raw;

            safeSet(slots[i - start], ItemBuilder.of(Items.WRITABLE_BOOK)
                    .name(key, Formatting.GOLD, Formatting.BOLD)
                    .lore(preview, Formatting.GRAY)
                    .lore("Left-click → edit in chat", Formatting.YELLOW)
                    .lore("Right-click → reset to default", Formatting.RED)
                    .build());
        }

        safeSet(BACK_SLOT, ItemBuilder.of(Items.ARROW).name("Back", Formatting.YELLOW).build());
        if (page > 0) safeSet(PREV_SLOT, ItemBuilder.of(Items.SPECTRAL_ARROW).name("Previous", Formatting.AQUA).build());
        if (page < totalPages - 1) safeSet(NEXT_SLOT, ItemBuilder.of(Items.SPECTRAL_ARROW).name("Next", Formatting.AQUA).build());
        safeSet(PAGE_SLOT, ItemBuilder.of(Items.PAPER).name("Page " + (page + 1) + " / " + totalPages, Formatting.WHITE).build());
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED).build());
    }

    private int[] contentSlots() {
        int[] s = new int[PER_PAGE];
        int idx = 0;
        for (int row = 0; row < 4; row++) {
            for (int col = 1; col < 8; col++) {
                s[idx++] = (row + 1) * 9 + col;
            }
        }
        return s;
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (!AdminUtils.isAdmin(player)) { close(); return; }
        if (slot == BACK_SLOT) { SoundHelper.back(player); new AdminGui(player).open(); return; }
        if (slot == CLOSE_SLOT) { close(); return; }
        if (slot == PREV_SLOT && page > 0) { page--; refresh(); return; }
        if (slot == NEXT_SLOT) { page++; refresh(); return; }

        int[] slots = contentSlots();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                int idx = page * PER_PAGE + i;
                if (idx >= keys.size()) return;
                String key = keys.get(idx);

                if (button == 1) {
                    // Drop the override so the key falls through to the
                    // active locale's JSON base. Previously this
                    // constructed a fresh PokeBuilderMessages and copied
                    // its value back in, which hardcoded English even on
                    // a non-en_us server.
                    PokeBuilderMessages msgs = PokeBuilder.get().config().messages;
                    String old = msgs.raw(key);
                    msgs.resetToDefault(key);
                    String def = msgs.raw(key);
                    AdminUtils.logAdmin(player, "messages[" + key + "]", old, def);
                    SoundHelper.openMenu(player);
                    refresh();
                    return;
                }

                ChatInputManager.request(player, key,
                        text -> {
                            String old = PokeBuilder.get().config().messages.raw(key);
                            PokeBuilder.get().config().messages.put(key, text);
                            AdminUtils.logAdmin(player, "messages[" + key + "]", old, text);
                            player.sendMessage(Text.literal("Applied: ").formatted(Formatting.GREEN)
                                    .append(PokeBuilderMessages.colorize(text)), false);
                            new MessagesAdminGui(player).open();
                        },
                        () -> new MessagesAdminGui(player).open());
                close();
                return;
            }
        }
    }

    @Override
    protected void onOpen() { SoundHelper.openMenu(player); }
}
