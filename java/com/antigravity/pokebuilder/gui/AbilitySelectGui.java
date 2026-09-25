package com.antigravity.pokebuilder.gui;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.PokeBuilderConstants;
import com.antigravity.pokebuilder.gui.base.BaseGui;
import com.antigravity.pokebuilder.service.CobblemonBridge;
import com.antigravity.pokebuilder.service.FeatureGate;
import com.antigravity.pokebuilder.service.AbilityEntry;
import com.antigravity.pokebuilder.service.PokemonModifier;
import com.antigravity.pokebuilder.service.TransactionService;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.ModItems;
import com.antigravity.pokebuilder.util.PermissionHelper;
import com.antigravity.pokebuilder.util.SoundHelper;
import com.antigravity.pokebuilder.util.TextHelper;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class AbilitySelectGui extends BaseGui {

    private static final int[] ABILITY_SLOTS = {10, 12, 14};
    private static final int BACK_SLOT = 18;
    private static final int CLOSE_SLOT = 26;

    private final UUID pokemonId;
    private List<AbilityEntry> cachedAbilities;

    public AbilitySelectGui(ServerPlayerEntity player, UUID pokemonId) {
        super(player, 3, Text.literal("PokeBuilder — Ability").formatted(Formatting.DARK_AQUA));
        this.pokemonId = pokemonId;
    }

    @Override
    protected void build() {
        fillBorders(7);
        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        if (p == null) { close(); return; }

        cachedAbilities = PokemonModifier.flattenAbilitiesTagged(p);
        String currentAbilityName = p.getAbility().getTemplate().getName();
        for (int i = 0; i < ABILITY_SLOTS.length; i++) {
            int slotIdx = ABILITY_SLOTS[i];
            if (i >= cachedAbilities.size()) {
                safeSet(slotIdx, ItemBuilder.glass(15).name("Unavailable", Formatting.DARK_GRAY).build());
                continue;
            }
            AbilityEntry entry = cachedAbilities.get(i);
            AbilityTemplate ability = entry.template();
            boolean hidden = entry.hidden();
            boolean current = currentAbilityName.equals(ability.getName());
            BigDecimal cost = hidden
                    ? PokeBuilder.get().config().prices.ability.hidden
                    : PokeBuilder.get().config().prices.ability.normal;

            safeSet(slotIdx, ItemBuilder.of(hidden ? ModItems.abilityPatch() : ModItems.abilityCapsule())
                    .name(ability.getName() + (hidden ? " (Hidden)" : ""),
                            hidden ? Formatting.LIGHT_PURPLE : Formatting.AQUA, Formatting.BOLD)
                    .lore("Cost: " + TextHelper.formatMoney(cost) + " " + TextHelper.currencyName(), Formatting.YELLOW)
                    .lore(current ? "[CURRENT]" : "Click to select", current ? Formatting.GREEN : Formatting.GRAY)
                    .glow(current).build());
        }

        safeSet(BACK_SLOT, ItemBuilder.of(Items.ARROW).name("Back", Formatting.YELLOW).build());
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED).build());
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        if (slot == BACK_SLOT) { SoundHelper.back(player); new EditorGui(player, pokemonId).open(); return; }
        if (slot == CLOSE_SLOT) { close(); return; }
        if (!FeatureGate.enforce(FeatureGate.Feature.ABILITY, player)) { close(); return; }

        for (int i = 0; i < ABILITY_SLOTS.length; i++) {
            if (ABILITY_SLOTS[i] == slot) {
                if (cachedAbilities == null || i >= cachedAbilities.size()) return;
                final int index = i;
                AbilityEntry entry = cachedAbilities.get(i);
                boolean hidden = entry.hidden();
                if (hidden && !FeatureGate.enforce(FeatureGate.Feature.ABILITY_HIDDEN, player)) return;
                if (hidden && !PermissionHelper.has(player, PokeBuilderConstants.PERM_MOD_ABILITY_HIDDEN, 0)) {
                    player.sendMessage(Text.literal("No permission for hidden abilities.").formatted(Formatting.RED), false);
                    return;
                }
                BigDecimal cost = hidden
                        ? PokeBuilder.get().config().prices.ability.hidden
                        : PokeBuilder.get().config().prices.ability.normal;

                final String targetAbility = entry.template().getName();
                TransactionService.execute(player, pokemonId,
                        hidden ? FeatureGate.Feature.ABILITY_HIDDEN : FeatureGate.Feature.ABILITY,
                        "ABILITY_SET", targetAbility, cost,
                        pm -> {
                            try {
                                String cur = pm.getAbility().getTemplate().getName();
                                return !targetAbility.equals(cur);
                            } catch (Throwable t) { return true; }
                        },
                        pm -> {
                            String before = PokemonModifier.currentAbilityName(pm);
                            PokemonModifier.setAbility(pm, index);
                            String after = PokemonModifier.currentAbilityName(pm);
                            PokeBuilder.LOGGER.info(
                                    "[PokeBuilder] ABILITY_SET {} -> {} (requested={}) for {}",
                                    before, after, targetAbility, player.getName().getString());
                            if (!targetAbility.equals(after)) {
                                // Setter didn't persist — throw so the
                                // transaction refunds. Same pattern as
                                // the Tera fix in 1.3.7.
                                throw new IllegalStateException(
                                        "updateAbility did not persist: still " + after);
                            }
                        });
                refresh();
                return;
            }
        }
    }

    @Override
    protected void onOpen() { SoundHelper.openMenu(player); }
}
