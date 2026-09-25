package com.antigravity.pokebuilder.service;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.UUID;

// Contract for a registered buy-flow. The interface is deliberately
// narrow — `invoke()` does whatever the flow needs (one-click transaction,
// chat prompt, paginated picker, confirmation dialog), rather than
// forcing every feature into an `apply(Pokemon)` shape. That lets
// picker-style and chat-style flows share the registry without a
// straitjacket.
//
// New in 1.3.0. Legacy features (IV, EV, nature, ability, shiny, gender,
// ball, dynamax, gmax, tera, max-IV, reset-EV) still live in bespoke GUI
// code — they'll be migrated behind this interface in a later release.
// For 1.3.0 only the three new features (nickname, friendship, status
// cure) register here.
public interface Purchasable {

    // Stable machine id, also used as the audit "action" tag and the
    // `blocked-modifications` vocabulary entry. Lowercase, underscore-
    // separated. Example: "nickname", "friendship", "status_cure".
    String id();

    // Feature gate this purchasable is bound to. A disabled feature
    // renders as a greyed placeholder and the transaction is refused.
    FeatureGate.Feature feature();

    // Fabric Permissions API node. Default OP level used for fallback
    // is already encapsulated at the caller site — implementations only
    // supply the node name.
    String permission();

    // Hint for EditorGui's layout. Registry renders Purchasables sorted
    // by this slot index; slots may be 10-16, 19-25, 28-34, 37-43 (the
    // four content rows of a 6-row GUI).
    int editorSlot();

    // Icon for the editor button. Callers pass null for `who` when
    // rendering in a context where no viewer exists (currently unused).
    ItemStack icon(Pokemon pokemon, ServerPlayerEntity who);

    // Sticker price before tier multipliers. Return BigDecimal.ZERO for
    // free actions (e.g. nickname reset).
    BigDecimal basePrice(Pokemon pokemon, ServerPlayerEntity who);

    // Runtime eligibility — e.g. status cure is ineligible on a healthy
    // Pokémon, gender swap is ineligible on genderless species.
    default Eligibility eligibility(Pokemon pokemon, ServerPlayerEntity who) {
        return Eligibility.OK;
    }

    // Run the flow. Opens a picker, runs a transaction directly, or
    // hands off to the chat input manager — implementation's choice.
    // The caller has already verified feature + permission + eligibility
    // at the GUI layer; implementations re-check at the transaction
    // boundary through TransactionService (defense in depth).
    void invoke(ServerPlayerEntity player, UUID pokemonId);

    record Eligibility(boolean ok, String messageKey) {
        public static final Eligibility OK = new Eligibility(true, null);

        public static Eligibility denied(String messageKey) {
            return new Eligibility(false, messageKey);
        }
    }
}
