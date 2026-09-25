package com.antigravity.pokebuilder.gui;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.gui.base.BaseGui;
import com.antigravity.pokebuilder.service.CobblemonBridge;
import com.antigravity.pokebuilder.service.FeatureGate;
import com.antigravity.pokebuilder.service.PokemonValidator;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.ModItems;
import com.antigravity.pokebuilder.util.SoundHelper;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

// Paginated read-only browser of the player's PC. Each click opens the
// EditorGui on the selected Pokémon, which then goes through the normal
// transaction flow — the PC access is a *lookup* on top of the existing
// per-player locked pipeline, not a parallel one.
//
// One box at a time (30 slots in a 5×6 grid). Box name + number on the
// banner in the bottom bar. Prev / Next / Back-to-Party buttons live
// in the bottom row alongside the banner.
//
// Security boundaries enforced here AND in CobblemonBridge.findByUuid:
//   - Constructor only accepts the requesting player — no target arg,
//     so there's no API surface to open someone else's PC.
//   - PC_ACCESS feature gate refuses to render if disabled server-wide.
//   - Concurrent mutation (Pokémon trade / move / release) is caught at
//     transaction time when findByUuid returns null and the editor
//     aborts with transaction.pokemon_gone.
public class PCSelectGui extends BaseGui {

    // Layout (6-row chest, 54 slots total):
    //   row 0     ─ box slots 0..5  in cols 1..6 (overwrites top border)
    //   row 1..4  ─ box slots 6..29 in cols 1..6
    //   row 5     ─ entire bottom bar (PREV / INFO / PARTY / NEXT / CLOSE)
    // Cols 0 and 7..8 of every row stay as decorative glass walls.
    // 5 rows × 6 cols = 30 = exact PC box size. No collisions, no waste.
    private static final int BOX_SIZE = 30;

    private static final int PREV_SLOT = 45;
    private static final int BOX_INFO_SLOT = 47;
    private static final int PARTY_TOGGLE_SLOT = 49;
    private static final int NEXT_SLOT = 51;
    private static final int CLOSE_SLOT = 53;

    private int boxIndex = 0;

    public PCSelectGui(ServerPlayerEntity player) {
        this(player, 0);
    }

    public PCSelectGui(ServerPlayerEntity player, int boxIndex) {
        super(player, 6, PokeBuilder.get().config().messages.get("gui.title.pc"));
        this.boxIndex = Math.max(0, boxIndex);
    }

    @Override
    protected void build() {
        fillBorders(PokeBuilder.get().config().looks.borderColor);

        PCStore pc = CobblemonBridge.getPC(player);
        if (pc == null) {
            safeSet(22, ItemBuilder.of(Items.BARRIER)
                    .name("PC unavailable", Formatting.RED, Formatting.BOLD)
                    .lore("Your PC isn't loaded yet.", Formatting.GRAY)
                    .lore("Try again in a moment.", Formatting.DARK_GRAY)
                    .build());
            safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED).build());
            return;
        }

        List<PCBox> boxes = pc.getBoxes();
        int totalBoxes = boxes.size();
        if (totalBoxes == 0) {
            safeSet(22, ItemBuilder.of(Items.BARRIER)
                    .name("Empty PC", Formatting.GRAY, Formatting.BOLD)
                    .build());
            return;
        }
        if (boxIndex >= totalBoxes) boxIndex = totalBoxes - 1;
        if (boxIndex < 0) boxIndex = 0;

        PCBox box = boxes.get(boxIndex);
        renderBox(box, boxIndex, totalBoxes);
    }

    private void renderBox(PCBox box, int index, int total) {
        // Slots 0..BOX_SIZE-1 map to grid positions 10..16, 19..25, 28..34, 37..39.
        // Stop at slot 30-ish so we don't collide with the bottom bar.
        for (int i = 0; i < BOX_SIZE; i++) {
            int slot = slotFor(i);
            Pokemon p;
            try { p = box.get(i); } catch (Throwable t) { p = null; }
            safeSet(slot, renderPokemon(p));
        }

        // box.getName() is null for never-renamed PC boxes (Cobblemon
        // only sets a name when the player explicitly renames a box).
        // Default to "Box N" so the lore line is never empty.
        String boxName = box.getName();
        if (boxName == null || boxName.isBlank()) {
            boxName = "Box " + (index + 1);
        }
        safeSet(BOX_INFO_SLOT, ItemBuilder.of(Items.ENDER_CHEST)
                .name(PokeBuilder.get().config().messages.get(
                        "gui.label.pc_box", (index + 1), total).getString(),
                        Formatting.AQUA, Formatting.BOLD)
                .lore(boxName, Formatting.GRAY)
                .build());

        if (index > 0) {
            safeSet(PREV_SLOT, ItemBuilder.of(Items.SPECTRAL_ARROW)
                    .name(PokeBuilder.get().config().messages.get("gui.label.pc_prev_box").getString(),
                            Formatting.YELLOW, Formatting.BOLD)
                    .build());
        }
        if (index < total - 1) {
            safeSet(NEXT_SLOT, ItemBuilder.of(Items.SPECTRAL_ARROW)
                    .name(PokeBuilder.get().config().messages.get("gui.label.pc_next_box").getString(),
                            Formatting.YELLOW, Formatting.BOLD)
                    .build());
        }
        safeSet(PARTY_TOGGLE_SLOT, ItemBuilder.of(Items.WRITABLE_BOOK)
                .name(PokeBuilder.get().config().messages.get("gui.label.switch_to_party").getString(),
                        Formatting.GREEN, Formatting.BOLD)
                .lore("Return to your party view.", Formatting.GRAY)
                .build());
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED, Formatting.BOLD).build());
    }

    // Map logical PC slot (0..29) to inventory slot, using a 5×6 grid
    // anchored at row 0 col 1 (so cols 0, 7, 8 stay as wall padding).
    //   box  0..5  → row 0 cols 1..6 → slots 1..6
    //   box  6..11 → row 1 cols 1..6 → slots 10..15
    //   box 12..17 → row 2 cols 1..6 → slots 19..24
    //   box 18..23 → row 3 cols 1..6 → slots 28..33
    //   box 24..29 → row 4 cols 1..6 → slots 37..42
    private static int slotFor(int slotInBox) {
        int cols = 6;
        int row = slotInBox / cols;     // 0..4
        int col = slotInBox % cols;     // 0..5
        return row * 9 + (col + 1);     // rows 0..4, cols 1..6
    }

    private ItemStack renderPokemon(Pokemon p) {
        if (p == null) {
            return ItemBuilder.of(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .name(PokeBuilder.get().config().messages.get("gui.label.empty_slot").getString(),
                            Formatting.DARK_GRAY)
                    .build();
        }
        if (PokemonValidator.isEgg(p)) {
            return ItemBuilder.of(Items.TURTLE_EGG)
                    .name("Egg", Formatting.GRAY, Formatting.ITALIC)
                    .lore("Eggs cannot be modified", Formatting.DARK_GRAY)
                    .build();
        }
        boolean blocked = PokemonValidator.isBlocked(p);
        boolean shiny = p.getShiny();
        ItemStack stack = blocked ? new ItemStack(Items.BARRIER) : ModItems.pokemonIcon(p);

        Stats[] stats = CobblemonBridge.allStats();
        String ivs = String.format("%d/%d/%d/%d/%d/%d",
                p.getIvs().get(stats[0]), p.getIvs().get(stats[1]),
                p.getIvs().get(stats[2]), p.getIvs().get(stats[3]),
                p.getIvs().get(stats[4]), p.getIvs().get(stats[5]));
        String evs = String.format("%d/%d/%d/%d/%d/%d",
                p.getEvs().get(stats[0]), p.getEvs().get(stats[1]),
                p.getEvs().get(stats[2]), p.getEvs().get(stats[3]),
                p.getEvs().get(stats[4]), p.getEvs().get(stats[5]));

        ItemBuilder b = ItemBuilder.of(stack)
                .name((shiny ? "\u2728 " : "") + capitalize(p.getSpecies().getName()) + "  Lv." + p.getLevel(),
                        blocked ? Formatting.RED : (shiny ? Formatting.AQUA : Formatting.GREEN),
                        Formatting.BOLD)
                .lore("Nature: " + capitalize(p.getNature().getName().getPath()), Formatting.GRAY)
                .lore("Ability: " + (p.getAbility() != null ? p.getAbility().getTemplate().getName() : "—"), Formatting.GRAY)
                .lore("IVs: " + ivs, Formatting.GRAY)
                .lore("EVs: " + evs, Formatting.GRAY)
                .lore(blocked ? "\u2716 Blocked by server rules" : "Click to edit",
                        blocked ? Formatting.RED : Formatting.YELLOW);
        if (shiny) b.glow(true);
        return b.build();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "—";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (slot == CLOSE_SLOT) { close(); return; }
        if (slot == PARTY_TOGGLE_SLOT) { SoundHelper.back(player); new PartySelectGui(player).open(); return; }
        if (slot == PREV_SLOT) { boxIndex = Math.max(0, boxIndex - 1); refresh(); return; }
        if (slot == NEXT_SLOT) { boxIndex = boxIndex + 1; refresh(); return; }

        // Must also re-check the feature gate here — an admin could
        // disable PC access while the player has this GUI open.
        if (!FeatureGate.enforce(FeatureGate.Feature.PC_ACCESS, player)) {
            close();
            return;
        }

        PCStore pc = CobblemonBridge.getPC(player);
        if (pc == null) return;
        List<PCBox> boxes = pc.getBoxes();
        if (boxIndex >= boxes.size()) return;
        PCBox box = boxes.get(boxIndex);

        // Reverse slotFor(): map grid slot back to box index.
        int boxSlot = boxSlotFor(slot);
        if (boxSlot < 0 || boxSlot >= BOX_SIZE) return;

        Pokemon p;
        try { p = box.get(boxSlot); } catch (Throwable t) { return; }
        if (p == null || PokemonValidator.isEgg(p)) return;
        if (PokemonValidator.isBlocked(p)) {
            player.sendMessage(Text.literal("This Pokémon cannot be modified.")
                    .formatted(Formatting.RED), false);
            return;
        }
        SoundHelper.selectPokemon(player);
        new EditorGui(player, p.getUuid()).open();
    }

    private static int boxSlotFor(int inventorySlot) {
        // Inverse of slotFor: rows 0..4, cols 1..6.
        int row = inventorySlot / 9;
        int col = inventorySlot % 9;
        if (row < 0 || row > 4 || col < 1 || col > 6) return -1;
        int boxSlot = row * 6 + (col - 1);
        return boxSlot < BOX_SIZE ? boxSlot : -1;
    }

    @Override
    protected void onOpen() {
        SoundHelper.openMenu(player);
    }
}
