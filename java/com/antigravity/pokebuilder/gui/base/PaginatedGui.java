package com.antigravity.pokebuilder.gui.base;

import com.antigravity.pokebuilder.util.ItemBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public abstract class PaginatedGui<T> extends BaseGui {

    protected static final int PER_PAGE = 28;
    protected static final int PREV_SLOT = 47;
    protected static final int PAGE_SLOT = 49;
    protected static final int NEXT_SLOT = 51;
    protected static final int BACK_SLOT = 45;
    protected static final int CLOSE_SLOT = 53;

    protected int page = 0;

    protected PaginatedGui(ServerPlayerEntity player, Text title) {
        super(player, 6, title);
    }

    protected abstract List<T> items();
    protected abstract ItemStack renderItem(T item);
    protected abstract void onItemClick(T item);
    protected abstract void onBack();

    @Override
    protected void build() {
        fillBorders(7);
        List<T> all = items();
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int start = page * PER_PAGE;
        int end = Math.min(start + PER_PAGE, all.size());

        int[] slots = contentSlots();
        for (int i = start; i < end; i++) {
            int slot = slots[i - start];
            safeSet(slot, renderItem(all.get(i)));
        }

        safeSet(BACK_SLOT, ItemBuilder.of(Items.ARROW)
                .name("Back", Formatting.YELLOW).build());
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER)
                .name("Close", Formatting.RED).build());

        if (page > 0) {
            safeSet(PREV_SLOT, ItemBuilder.of(Items.SPECTRAL_ARROW)
                    .name("Previous Page", Formatting.AQUA).build());
        }
        if (page < totalPages - 1) {
            safeSet(NEXT_SLOT, ItemBuilder.of(Items.SPECTRAL_ARROW)
                    .name("Next Page", Formatting.AQUA).build());
        }
        safeSet(PAGE_SLOT, ItemBuilder.of(Items.PAPER)
                .name("Page " + (page + 1) + " / " + totalPages, Formatting.WHITE)
                .build());
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
        if (slot == BACK_SLOT) { onBack(); return; }
        if (slot == CLOSE_SLOT) { close(); return; }
        if (slot == PREV_SLOT && page > 0) { page--; refresh(); return; }
        if (slot == NEXT_SLOT) { page++; refresh(); return; }

        int[] slots = contentSlots();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                int index = page * PER_PAGE + i;
                List<T> all = items();
                if (index < all.size()) {
                    onItemClick(all.get(index));
                }
                return;
            }
        }
    }
}
