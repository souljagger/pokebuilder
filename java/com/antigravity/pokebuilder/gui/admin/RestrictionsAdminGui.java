package com.antigravity.pokebuilder.gui.admin;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.admin.ChatInputManager;
import com.antigravity.pokebuilder.config.PokeBuilderConfig;
import com.antigravity.pokebuilder.gui.base.BaseGui;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.SoundHelper;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

public class RestrictionsAdminGui extends BaseGui {

    private static final int LEGENDARY_SLOT = 10;
    private static final int MYTHICAL_SLOT = 12;
    private static final int ULTRA_SLOT = 14;
    private static final int PARADOX_SLOT = 16;
    private static final int BLOCKED_LIST_SLOT = 22;
    private static final int ADD_SPECIES_SLOT = 29;
    private static final int REMOVE_SPECIES_SLOT = 31;
    private static final int CLEAR_SPECIES_SLOT = 33;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    public RestrictionsAdminGui(ServerPlayerEntity player) {
        super(player, 6, PokeBuilder.get().config().messages.get("gui.title.admin.restrictions"));
    }

    @Override
    protected void build() {
        fillBorders(PokeBuilder.get().config().looks.borderColor);
        PokeBuilderConfig.Restrictions r = PokeBuilder.get().config().restrictions;

        safeSet(LEGENDARY_SLOT, toggle("Block legendaries", r.blockLegendaries));
        safeSet(MYTHICAL_SLOT, toggle("Block mythicals", r.blockMythicals));
        safeSet(ULTRA_SLOT, toggle("Block ultra beasts", r.blockUltraBeasts));
        safeSet(PARADOX_SLOT, toggle("Block paradox", r.blockParadox));

        ItemBuilder list = ItemBuilder.of(Items.BOOK)
                .name("Blocked species (" + r.blockedSpecies.size() + ")", Formatting.GOLD, Formatting.BOLD);
        if (r.blockedSpecies.isEmpty()) {
            list.lore("(none)", Formatting.DARK_GRAY);
        } else {
            int shown = 0;
            for (String sp : r.blockedSpecies) {
                if (shown++ >= 8) { list.lore("... and " + (r.blockedSpecies.size() - 8) + " more", Formatting.DARK_GRAY); break; }
                list.lore("- " + sp, Formatting.GRAY);
            }
        }
        safeSet(BLOCKED_LIST_SLOT, list.build());

        safeSet(ADD_SPECIES_SLOT, ItemBuilder.of(Items.LIME_CONCRETE)
                .name("Add species", Formatting.GREEN, Formatting.BOLD)
                .lore("Click → type name in chat", Formatting.GRAY).build());
        safeSet(REMOVE_SPECIES_SLOT, ItemBuilder.of(Items.RED_CONCRETE)
                .name("Remove species", Formatting.RED, Formatting.BOLD)
                .lore("Click → type name in chat", Formatting.GRAY).build());
        safeSet(CLEAR_SPECIES_SLOT, ItemBuilder.of(Items.TNT)
                .name("Clear ALL", Formatting.DARK_RED, Formatting.BOLD)
                .lore("Shift-click to confirm", Formatting.GRAY).build());

        safeSet(BACK_SLOT, ItemBuilder.of(Items.ARROW).name("Back", Formatting.YELLOW).build());
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED).build());
    }

    private net.minecraft.item.ItemStack toggle(String label, boolean on) {
        return ItemBuilder.of(on ? Items.LIME_WOOL : Items.RED_WOOL)
                .name(label + ": " + (on ? "YES" : "NO"), on ? Formatting.GREEN : Formatting.RED, Formatting.BOLD)
                .lore("Click to toggle", Formatting.GRAY).build();
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (!AdminUtils.isAdmin(player)) { close(); return; }
        PokeBuilderConfig.Restrictions r = PokeBuilder.get().config().restrictions;
        boolean shift = action == SlotActionType.QUICK_MOVE;

        switch (slot) {
            case LEGENDARY_SLOT -> {
                r.blockLegendaries = !r.blockLegendaries;
                AdminUtils.logAdmin(player, "restrictions.blockLegendaries", String.valueOf(!r.blockLegendaries), String.valueOf(r.blockLegendaries));
            }
            case MYTHICAL_SLOT -> {
                r.blockMythicals = !r.blockMythicals;
                AdminUtils.logAdmin(player, "restrictions.blockMythicals", String.valueOf(!r.blockMythicals), String.valueOf(r.blockMythicals));
            }
            case ULTRA_SLOT -> {
                r.blockUltraBeasts = !r.blockUltraBeasts;
                AdminUtils.logAdmin(player, "restrictions.blockUltraBeasts", String.valueOf(!r.blockUltraBeasts), String.valueOf(r.blockUltraBeasts));
            }
            case PARADOX_SLOT -> {
                r.blockParadox = !r.blockParadox;
                AdminUtils.logAdmin(player, "restrictions.blockParadox", String.valueOf(!r.blockParadox), String.valueOf(r.blockParadox));
            }
            case ADD_SPECIES_SLOT -> {
                ChatInputManager.request(player, "Species to BLOCK",
                        text -> {
                            String name = text.toLowerCase().trim();
                            if (!name.isEmpty() && !r.blockedSpecies.contains(name)) {
                                r.blockedSpecies.add(name);
                                AdminUtils.logAdmin(player, "restrictions.blockedSpecies[+]", "-", name);
                            }
                            new RestrictionsAdminGui(player).open();
                        },
                        () -> new RestrictionsAdminGui(player).open());
                close();
                return;
            }
            case REMOVE_SPECIES_SLOT -> {
                ChatInputManager.request(player, "Species to UNBLOCK",
                        text -> {
                            String name = text.toLowerCase().trim();
                            if (r.blockedSpecies.remove(name)) {
                                AdminUtils.logAdmin(player, "restrictions.blockedSpecies[-]", name, "-");
                            }
                            new RestrictionsAdminGui(player).open();
                        },
                        () -> new RestrictionsAdminGui(player).open());
                close();
                return;
            }
            case CLEAR_SPECIES_SLOT -> {
                if (!shift) {
                    player.sendMessage(net.minecraft.text.Text.literal("Shift-click to confirm clear.").formatted(Formatting.YELLOW), false);
                    return;
                }
                int n = r.blockedSpecies.size();
                r.blockedSpecies.clear();
                AdminUtils.logAdmin(player, "restrictions.blockedSpecies[clear]", String.valueOf(n), "0");
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
