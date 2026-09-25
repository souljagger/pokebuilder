package com.antigravity.pokebuilder.gui;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.PokeBuilderConstants;
import com.antigravity.pokebuilder.gui.base.BaseGui;
// Diagnostic logging in 1.3.2 to chase the "PC toggle does nothing" report.
import com.antigravity.pokebuilder.service.CobblemonBridge;
import com.antigravity.pokebuilder.service.FeatureGate;
import com.antigravity.pokebuilder.service.PokemonValidator;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.ModItems;
import com.antigravity.pokebuilder.util.PermissionHelper;
import com.antigravity.pokebuilder.util.SoundHelper;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class PartySelectGui extends BaseGui {

    private static final int[] POKE_SLOTS = {10, 11, 12, 13, 14, 15};
    private static final int PC_TOGGLE_SLOT = 22;
    private static final int CLOSE_SLOT = 26;

    public PartySelectGui(ServerPlayerEntity player) {
        super(player, 3, Text.literal("\u2756 PokeBuilder \u00b7 Party \u2756").formatted(Formatting.DARK_AQUA, Formatting.BOLD));
    }

    @Override
    protected void build() {
        fillBorders(PokeBuilder.get().config().looks.borderColor);

        List<Pokemon> party = CobblemonBridge.partyAsList(player);
        for (int i = 0; i < 6; i++) {
            Pokemon p = i < party.size() ? party.get(i) : null;
            safeSet(POKE_SLOTS[i], renderPokemon(p, i));
        }

        // PC toggle visibility — gated by the server-side feature flag
        // (features.pc-access defaults OFF) AND a per-player permission
        // (pokebuilder.use.pc defaults TRUE once the feature is on).
        // The permission gate uses `has` with fallback OP level 0 — i.e.
        // "everyone unless a permissions plugin explicitly denies them".
        // This matches `pokebuilder.use` (the main mod permission) and
        // means servers without LuckPerms still let players use PC
        // access once the admin opts the feature in.
        if (FeatureGate.enabled(FeatureGate.Feature.PC_ACCESS)
                && PermissionHelper.has(player, PokeBuilderConstants.PERM_USE_PC, 0)) {
            safeSet(PC_TOGGLE_SLOT, ItemBuilder.of(Items.ENDER_CHEST)
                    .name(PokeBuilder.get().config().messages.get("gui.label.switch_to_pc").getString(),
                            Formatting.AQUA, Formatting.BOLD)
                    .lore("Browse and edit stored Pokémon.", Formatting.GRAY)
                    .build());
        }

        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER)
                .name("Close", Formatting.RED, Formatting.BOLD).build());
    }

    private ItemStack renderPokemon(Pokemon p, int index) {
        if (p == null) {
            return ItemBuilder.of(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .name("Party Slot " + (index + 1), Formatting.DARK_GRAY)
                    .lore("Empty", Formatting.DARK_GRAY)
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

        // Use Cobblemon's PokemonItem to render the actual species model in
        // the slot. If the lookup fails, fall back to the Pokémon's caught
        // ball so the icon is still thematic.
        ItemStack stack = blocked
                ? new ItemStack(Items.BARRIER)
                : ModItems.pokemonIcon(p);

        Stats[] stats = CobblemonBridge.allStats();
        String ivs = String.format("%d/%d/%d/%d/%d/%d",
                p.getIvs().get(stats[0]), p.getIvs().get(stats[1]),
                p.getIvs().get(stats[2]), p.getIvs().get(stats[3]),
                p.getIvs().get(stats[4]), p.getIvs().get(stats[5]));
        String evs = String.format("%d/%d/%d/%d/%d/%d",
                p.getEvs().get(stats[0]), p.getEvs().get(stats[1]),
                p.getEvs().get(stats[2]), p.getEvs().get(stats[3]),
                p.getEvs().get(stats[4]), p.getEvs().get(stats[5]));

        Formatting nameColor = blocked ? Formatting.RED : (shiny ? Formatting.AQUA : Formatting.GREEN);
        ItemBuilder b = ItemBuilder.of(stack)
                .name((shiny ? "\u2728 " : "") + capitalize(p.getSpecies().getName()) + "  Lv." + p.getLevel(),
                        nameColor, Formatting.BOLD)
                .lore("Nature: " + capitalize(p.getNature().getName().getPath()), Formatting.GRAY)
                .lore("Ability: " + (p.getAbility() != null ? p.getAbility().getTemplate().getName() : "—"), Formatting.GRAY)
                .lore("IVs: " + ivs, Formatting.GRAY)
                .lore("EVs: " + evs, Formatting.GRAY)
                .lore("Shiny: " + (shiny ? "Yes" : "No"), Formatting.GRAY)
                .lore("Gender: " + p.getGender().name(), Formatting.GRAY);

        try {
            if (p.getTeraType() != null) {
                b.lore("Tera: " + capitalize(p.getTeraType().getName()), Formatting.LIGHT_PURPLE);
            }
        } catch (Throwable ignored) {}
        try {
            int d = p.getDmaxLevel();
            if (d > 0) b.lore("Dynamax Lv.: " + d, Formatting.LIGHT_PURPLE);
        } catch (Throwable ignored) {}
        try {
            if (p.getGmaxFactor()) b.lore("G-Max Factor", Formatting.LIGHT_PURPLE);
        } catch (Throwable ignored) {}

        if (blocked) {
            b.lore("\u2716 Blocked by server rules", Formatting.RED, Formatting.BOLD);
        } else {
            b.lore("Click to edit", Formatting.YELLOW);
        }
        if (shiny) b.glow(true);
        return b.build();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "—";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (slot == CLOSE_SLOT) {
            SoundHelper.back(player);
            close();
            return;
        }
        if (slot == PC_TOGGLE_SLOT) {
            // Re-check feature + permission at click time — the toggle
            // button only renders when both are on, but gate it here
            // too in case config changed between render and click.
            if (!FeatureGate.enabled(FeatureGate.Feature.PC_ACCESS)) {
                PokeBuilder.LOGGER.debug("[PokeBuilder] PC toggle click ignored: feature disabled");
                return;
            }
            if (!PermissionHelper.has(player, PokeBuilderConstants.PERM_USE_PC, 0)) {
                PokeBuilder.LOGGER.debug("[PokeBuilder] PC toggle click ignored: player {} lacks {}",
                        player.getName().getString(), PokeBuilderConstants.PERM_USE_PC);
                return;
            }
            try {
                SoundHelper.selectPokemon(player);
                PokeBuilder.LOGGER.info("[PokeBuilder] Opening PC for {}", player.getName().getString());
                new PCSelectGui(player).open();
            } catch (Throwable t) {
                PokeBuilder.LOGGER.error("[PokeBuilder] PC toggle failed for {}: {}",
                        player.getName().getString(), t.toString(), t);
                player.sendMessage(net.minecraft.text.Text.literal(
                        "Failed to open PC: " + t.getClass().getSimpleName()
                                + " — see server log").formatted(net.minecraft.util.Formatting.RED), false);
            }
            return;
        }
        for (int i = 0; i < POKE_SLOTS.length; i++) {
            if (POKE_SLOTS[i] == slot) {
                List<Pokemon> party = CobblemonBridge.partyAsList(player);
                if (i >= party.size()) return;
                Pokemon p = party.get(i);
                if (p == null || PokemonValidator.isEgg(p)) return;
                if (PokemonValidator.isBlocked(p)) {
                    player.sendMessage(Text.literal("This Pokémon cannot be modified.")
                            .formatted(Formatting.RED), false);
                    return;
                }
                SoundHelper.selectPokemon(player);
                new EditorGui(player, p.getUuid()).open();
                return;
            }
        }
    }

    @Override
    protected void onOpen() {
        SoundHelper.openMenu(player);
    }
}
