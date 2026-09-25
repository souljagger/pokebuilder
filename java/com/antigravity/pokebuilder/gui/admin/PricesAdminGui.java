package com.antigravity.pokebuilder.gui.admin;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.config.PokeBuilderConfig;
import com.antigravity.pokebuilder.gui.base.BaseGui;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.SoundHelper;
import com.antigravity.pokebuilder.util.TextHelper;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PricesAdminGui extends BaseGui {

    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;
    private static final int PREV_SLOT = 48;
    private static final int NEXT_SLOT = 50;
    private static final int PAGE_SLOT = 49;

    private static final int ROWS_PER_PAGE = 4;

    private int page = 0;
    private final List<PriceEntry> entries = new ArrayList<>();

    public PricesAdminGui(ServerPlayerEntity player) {
        super(player, 6, PokeBuilder.get().config().messages.get("gui.title.admin.prices"));
        registerEntries();
    }

    private void registerEntries() {
        PokeBuilderConfig c = PokeBuilder.get().config();
        entries.clear();
        entries.add(new PriceEntry("IV: Set single stat to 31", () -> c.prices.iv.setSingle31, v -> c.prices.iv.setSingle31 = v));
        entries.add(new PriceEntry("IV: Set single stat to 0", () -> c.prices.iv.setSingle0, v -> c.prices.iv.setSingle0 = v));
        entries.add(new PriceEntry("IV: Max all", () -> c.prices.iv.maxAll, v -> c.prices.iv.maxAll = v));
        entries.add(new PriceEntry("EV: Set single stat to 252", () -> c.prices.ev.setSingle252, v -> c.prices.ev.setSingle252 = v));
        entries.add(new PriceEntry("EV: Set single stat to 0", () -> c.prices.ev.setSingle0, v -> c.prices.ev.setSingle0 = v));
        entries.add(new PriceEntry("EV: Reset all", () -> c.prices.ev.resetAll, v -> c.prices.ev.resetAll = v));
        entries.add(new PriceEntry("EV: Add +10", () -> c.prices.ev.add10, v -> c.prices.ev.add10 = v));
        entries.add(new PriceEntry("Nature change", () -> c.prices.nature, v -> c.prices.nature = v));
        entries.add(new PriceEntry("Ability: Normal", () -> c.prices.ability.normal, v -> c.prices.ability.normal = v));
        entries.add(new PriceEntry("Ability: Hidden", () -> c.prices.ability.hidden, v -> c.prices.ability.hidden = v));
        entries.add(new PriceEntry("Shiny toggle", () -> c.prices.shinyToggle, v -> c.prices.shinyToggle = v));
        entries.add(new PriceEntry("Gender swap", () -> c.prices.genderSwap, v -> c.prices.genderSwap = v));
        entries.add(new PriceEntry("Ball change", () -> c.prices.ballChange, v -> c.prices.ballChange = v));
        entries.add(new PriceEntry("Dynamax: per level", () -> c.prices.dynamaxLevelPerStep, v -> c.prices.dynamaxLevelPerStep = v));
        entries.add(new PriceEntry("G-Max factor", () -> c.prices.gmaxFactor, v -> c.prices.gmaxFactor = v));
        entries.add(new PriceEntry("Tera type", () -> c.prices.teraType, v -> c.prices.teraType = v));
        entries.add(new PriceEntry("Nickname", () -> c.prices.nickname, v -> c.prices.nickname = v));
        entries.add(new PriceEntry("Friendship: per +10", () -> c.prices.friendshipPerStep, v -> c.prices.friendshipPerStep = v));
        entries.add(new PriceEntry("Status cure", () -> c.prices.statusCure, v -> c.prices.statusCure = v));
        entries.add(new PriceEntry("Confirm threshold", () -> c.confirmThreshold, v -> c.confirmThreshold = v));
    }

    @Override
    protected void build() {
        fillBorders(PokeBuilder.get().config().looks.borderColor);

        int start = page * ROWS_PER_PAGE;
        int end = Math.min(start + ROWS_PER_PAGE, entries.size());

        for (int i = start; i < end; i++) {
            int rowIdx = i - start;
            int rowBase = (rowIdx + 1) * 9;
            PriceEntry entry = entries.get(i);
            BigDecimal value = entry.getter.get();

            safeSet(rowBase + 1, ItemBuilder.of(Items.PAPER)
                    .name(entry.label, Formatting.GOLD, Formatting.BOLD)
                    .lore("Current: " + TextHelper.formatMoney(value), Formatting.YELLOW)
                    .lore("Left = +, Right = -, Shift = x10", Formatting.DARK_GRAY)
                    .build());

            safeSet(rowBase + 3, stepItem("-1000", "-1000"));
            safeSet(rowBase + 4, stepItem("-100", "-100"));
            safeSet(rowBase + 5, stepItem("+100", "+100"));
            safeSet(rowBase + 6, stepItem("+1000", "+1000"));
            safeSet(rowBase + 7, ItemBuilder.of(Items.REDSTONE_BLOCK)
                    .name("RESET", Formatting.RED).build());
        }

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) ROWS_PER_PAGE));
        safeSet(BACK_SLOT, ItemBuilder.of(Items.ARROW).name("Back", Formatting.YELLOW).build());
        if (page > 0) safeSet(PREV_SLOT, ItemBuilder.of(Items.SPECTRAL_ARROW).name("Previous", Formatting.AQUA).build());
        if (page < totalPages - 1) safeSet(NEXT_SLOT, ItemBuilder.of(Items.SPECTRAL_ARROW).name("Next", Formatting.AQUA).build());
        safeSet(PAGE_SLOT, ItemBuilder.of(Items.PAPER)
                .name("Page " + (page + 1) + " / " + totalPages, Formatting.WHITE).build());
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED).build());
    }

    private net.minecraft.item.ItemStack stepItem(String label, String step) {
        boolean negative = step.startsWith("-");
        return ItemBuilder.of(negative ? Items.RED_CONCRETE : Items.LIME_CONCRETE)
                .name(label, negative ? Formatting.RED : Formatting.GREEN, Formatting.BOLD)
                .lore("Shift-click for x10", Formatting.DARK_GRAY)
                .build();
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (!AdminUtils.isAdmin(player)) { close(); return; }

        if (slot == BACK_SLOT) { SoundHelper.back(player); new AdminGui(player).open(); return; }
        if (slot == CLOSE_SLOT) { close(); return; }
        if (slot == PREV_SLOT && page > 0) { page--; refresh(); return; }
        if (slot == NEXT_SLOT) { page++; refresh(); return; }

        int row = slot / 9 - 1;
        int col = slot % 9;
        if (row < 0 || row >= ROWS_PER_PAGE) return;

        int entryIdx = page * ROWS_PER_PAGE + row;
        if (entryIdx >= entries.size()) return;

        PriceEntry entry = entries.get(entryIdx);
        BigDecimal current = entry.getter.get();
        boolean shift = action == SlotActionType.QUICK_MOVE;
        BigDecimal delta;

        switch (col) {
            case 3 -> delta = BigDecimal.valueOf(shift ? -10000 : -1000);
            case 4 -> delta = BigDecimal.valueOf(shift ? -1000 : -100);
            case 5 -> delta = BigDecimal.valueOf(shift ? 1000 : 100);
            case 6 -> delta = BigDecimal.valueOf(shift ? 10000 : 1000);
            case 7 -> {
                AdminUtils.logAdmin(player, entry.label, current.toPlainString(), "0");
                entry.setter.accept(BigDecimal.ZERO);
                SoundHelper.openMenu(player);
                refresh();
                return;
            }
            case 1 -> {
                com.antigravity.pokebuilder.admin.ChatInputManager.request(player, entry.label,
                        text -> {
                            try {
                                BigDecimal v = AdminUtils.clampPrice(new BigDecimal(text));
                                BigDecimal old = entry.getter.get();
                                entry.setter.accept(v);
                                AdminUtils.logAdmin(player, entry.label, old.toPlainString(), v.toPlainString());
                                player.sendMessage(PokeBuilder.get().config().messages.get("admin.chat_applied", v.toPlainString()), false);
                            } catch (Exception e) {
                                player.sendMessage(PokeBuilder.get().config().messages.get("admin.chat_invalid", text), false);
                            }
                            new PricesAdminGui(player).open();
                        },
                        () -> {
                            player.sendMessage(PokeBuilder.get().config().messages.get("admin.chat_cancelled"), false);
                            new PricesAdminGui(player).open();
                        });
                close();
                return;
            }
            default -> { return; }
        }

        BigDecimal next = AdminUtils.clampPrice(current.add(delta));
        AdminUtils.logAdmin(player, entry.label, current.toPlainString(), next.toPlainString());
        entry.setter.accept(next);
        SoundHelper.openMenu(player);
        refresh();
    }

    @Override
    protected void onOpen() {
        SoundHelper.openMenu(player);
    }

    private static class PriceEntry {
        final String label;
        final Supplier<BigDecimal> getter;
        final Consumer<BigDecimal> setter;

        PriceEntry(String label, Supplier<BigDecimal> getter, Consumer<BigDecimal> setter) {
            this.label = label;
            this.getter = getter;
            this.setter = setter;
        }
    }
}
