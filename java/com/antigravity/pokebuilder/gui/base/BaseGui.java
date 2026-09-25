package com.antigravity.pokebuilder.gui.base;

import com.antigravity.pokebuilder.util.ItemBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public abstract class BaseGui {

    protected final ServerPlayerEntity player;
    protected final int rows;
    protected final int size;
    protected final SimpleInventory inventory;
    protected final Text title;
    protected PokeBuilderScreenHandler handler;

    protected BaseGui(ServerPlayerEntity player, int rows, Text title) {
        this.player = player;
        this.rows = Math.max(1, Math.min(6, rows));
        this.size = this.rows * 9;
        this.inventory = new SimpleInventory(this.size);
        this.title = title;
    }

    public final void open() {
        try {
            build();
        } catch (Throwable t) {
            com.antigravity.pokebuilder.PokeBuilder.LOGGER.error(
                    "[PokeBuilder] GUI build failed for {} (player={}): {}",
                    getClass().getSimpleName(), player.getName().getString(), t.toString(), t);
            return;
        }
        // openHandledScreen closes any currently-attached screen first,
        // which fires the OLD handler's onClosed → BaseGui.onClose →
        // GuiManager.unregister(playerId). If we'd registered ourselves
        // before that point, the unregister would yank the new GUI's
        // entry too (registry is keyed by player UUID, not by gui id).
        // Open first, register after.
        player.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inv, p) -> {
                    handler = new PokeBuilderScreenHandler(
                            typeForRows(rows), syncId, inv, inventory, rows, this);
                    return handler;
                }, title));
        GuiManager.register(player, this);
        try {
            onOpen();
        } catch (Throwable t) {
            com.antigravity.pokebuilder.PokeBuilder.LOGGER.warn(
                    "[PokeBuilder] GUI onOpen hook failed for {}: {}",
                    getClass().getSimpleName(), t.toString());
        }
    }

    public final void refresh() {
        inventory.clear();
        build();
        if (handler != null) handler.sendContentUpdates();
    }

    public final void close() {
        GuiManager.unregister(player.getUuid());
        player.closeHandledScreen();
    }

    public final void forceClose() {
        GuiManager.unregister(player.getUuid());
        try { player.closeHandledScreen(); } catch (Exception ignored) {}
    }

    public void onClose() {
        GuiManager.unregister(player.getUuid());
    }

    protected void onOpen() {}

    protected abstract void build();

    public abstract void onSlotClick(int slot, int button, SlotActionType action);

    protected void fillBorders() {
        fillBorders(com.antigravity.pokebuilder.PokeBuilder.get().config().looks.borderColor);
    }

    protected void fillBorders(int color) {
        ItemStack pane = ItemBuilder.glass(color).build();
        for (int i = 0; i < 9; i++) safeSet(i, pane);
        for (int i = size - 9; i < size; i++) safeSet(i, pane);
        for (int r = 1; r < rows - 1; r++) {
            safeSet(r * 9, pane);
            safeSet(r * 9 + 8, pane);
        }
    }

    protected void fillAll(ItemStack stack) {
        for (int i = 0; i < size; i++) {
            if (inventory.getStack(i).isEmpty()) inventory.setStack(i, stack.copy());
        }
    }

    protected void safeSet(int slot, ItemStack stack) {
        if (slot >= 0 && slot < size) inventory.setStack(slot, stack);
    }

    public ServerPlayerEntity player() {
        return player;
    }

    public Inventory inventory() {
        return inventory;
    }

    private static ScreenHandlerType<GenericContainerScreenHandler> typeForRows(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }

    public static final class PokeBuilderScreenHandler extends GenericContainerScreenHandler {
        private final int guiSize;
        private final BaseGui gui;

        public PokeBuilderScreenHandler(ScreenHandlerType<GenericContainerScreenHandler> type,
                                        int syncId, PlayerInventory playerInv, Inventory inventory,
                                        int rows, BaseGui gui) {
            super(type, syncId, playerInv, inventory, rows);
            this.guiSize = rows * 9;
            this.gui = gui;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (actionType == SlotActionType.PICKUP_ALL
                    || actionType == SlotActionType.SWAP
                    || actionType == SlotActionType.CLONE
                    || actionType == SlotActionType.THROW
                    || actionType == SlotActionType.QUICK_CRAFT) {
                return;
            }

            if (slotIndex < 0 || slotIndex >= guiSize) {
                return;
            }

            if (actionType != SlotActionType.PICKUP && actionType != SlotActionType.QUICK_MOVE) {
                return;
            }

            gui.onSlotClick(slotIndex, button, actionType);
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
            return false;
        }

        @Override
        public boolean canInsertIntoSlot(Slot slot) {
            return false;
        }

        @Override
        public void onClosed(PlayerEntity player) {
            super.onClosed(player);
            gui.onClose();
        }
    }
}
