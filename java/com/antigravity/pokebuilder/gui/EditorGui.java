package com.antigravity.pokebuilder.gui;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.config.PokeBuilderConfig;
import com.antigravity.pokebuilder.economy.EconomyHandler;
import com.antigravity.pokebuilder.gui.base.BaseGui;
import com.antigravity.pokebuilder.service.CobblemonBridge;
import com.antigravity.pokebuilder.service.FeatureGate;
import com.antigravity.pokebuilder.service.MegaShowdownBridge;
import com.antigravity.pokebuilder.service.PokemonModifier;
import com.antigravity.pokebuilder.service.PokemonValidator;
import com.antigravity.pokebuilder.service.TransactionService;
import com.antigravity.pokebuilder.util.ItemBuilder;
import com.antigravity.pokebuilder.util.ModItems;
import com.antigravity.pokebuilder.util.SoundHelper;
import com.antigravity.pokebuilder.util.TextHelper;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.UUID;

public class EditorGui extends BaseGui {

    private static final int INFO_SLOT = 4;

    // Row 1 (slots 10-16): core attribute editors
    private static final int IV_SLOT = 10;
    private static final int EV_SLOT = 11;
    private static final int NATURE_SLOT = 12;
    private static final int ABILITY_SLOT = 13;
    private static final int SHINY_SLOT = 14;
    private static final int GENDER_SLOT = 15;
    private static final int BALL_SLOT = 16;

    // Row 3 (slots 28-34): quick actions + Mega Showdown features
    private static final int MAX_IV_SLOT = 28;
    private static final int RESET_EV_SLOT = 29;
    private static final int DYNAMAX_SLOT = 31;
    private static final int GMAX_SLOT = 32;
    private static final int TERA_SLOT = 33;

    // Bottom bar
    private static final int BACK_SLOT = 45;
    private static final int BALANCE_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private final UUID pokemonId;

    public EditorGui(ServerPlayerEntity player, UUID pokemonId) {
        super(player, 6, Text.literal("\u2756 PokeBuilder \u00b7 Edit \u2756").formatted(Formatting.DARK_AQUA, Formatting.BOLD));
        this.pokemonId = pokemonId;
    }

    @Override
    protected void build() {
        fillBorders(PokeBuilder.get().config().looks.borderColor);
        Pokemon p = CobblemonBridge.findByUuid(player, pokemonId);
        if (p == null) {
            close();
            return;
        }
        PokeBuilderConfig cfg = PokeBuilder.get().config();

        safeSet(INFO_SLOT, infoItem(p));

        // --- Row 1: core attribute editors ---
        renderFeature(IV_SLOT, FeatureGate.Feature.IV_EDITOR,
                ModItems.experienceCandyL(),
                "Individual Values",
                Formatting.AQUA,
                "Open the IV editor",
                "Set each stat individually (0-31)",
                null);

        renderFeature(EV_SLOT, FeatureGate.Feature.EV_EDITOR,
                ModItems.vitaminFor(0),
                "Effort Values",
                Formatting.GREEN,
                "Open the EV editor",
                "Distribute training points (0-252)",
                null);

        renderFeature(NATURE_SLOT, FeatureGate.Feature.NATURE,
                ModItems.natureMint(p.getNature().getName().getPath()),
                "Nature",
                Formatting.GOLD,
                "Current: " + capitalize(p.getNature().getName().getPath()),
                null,
                cfg.prices.nature);

        renderFeature(ABILITY_SLOT, FeatureGate.Feature.ABILITY,
                ModItems.abilityCapsule(),
                "Ability",
                Formatting.DARK_PURPLE,
                "Current: " + (p.getAbility() != null ? p.getAbility().getTemplate().getName() : "—"),
                null,
                cfg.prices.ability.normal);

        boolean shiny = p.getShiny();
        renderFeatureCustom(SHINY_SLOT, FeatureGate.Feature.SHINY,
                ItemBuilder.of(ModItems.shinyStone())
                        .name("Shiny: " + (shiny ? "ON" : "OFF"), shiny ? Formatting.AQUA : Formatting.GOLD, Formatting.BOLD)
                        .lore("Cost: " + TextHelper.formatMoney(cfg.prices.shinyToggle) + " " + TextHelper.currencyName(), Formatting.YELLOW)
                        .lore("Click to toggle", Formatting.GRAY)
                        .glow(shiny));

        boolean genderAllowed = PokemonValidator.allowGenderSwap(p);
        if (FeatureGate.enabled(FeatureGate.Feature.GENDER)) {
            safeSet(GENDER_SLOT, ItemBuilder.of(genderAllowed ? Items.POPPY : Items.BARRIER)
                    .name("Gender: " + p.getGender().name(), Formatting.GOLD, Formatting.BOLD)
                    .lore(genderAllowed ? "Click to swap" : "Cannot change", genderAllowed ? Formatting.GRAY : Formatting.RED)
                    .lore(genderAllowed ? "Cost: " + TextHelper.formatMoney(cfg.prices.genderSwap) + " " + TextHelper.currencyName() : "", Formatting.YELLOW)
                    .build());
        } else {
            safeSet(GENDER_SLOT, disabledItem("Gender swap"));
        }

        renderFeatureCustom(BALL_SLOT, FeatureGate.Feature.BALL,
                ItemBuilder.of(ModItems.caughtBallItem(p, Items.SNOWBALL))
                        .name("Caught Ball", Formatting.RED, Formatting.BOLD)
                        .lore("Cost: " + TextHelper.formatMoney(cfg.prices.ballChange) + " " + TextHelper.currencyName(), Formatting.YELLOW)
                        .lore("Click to choose", Formatting.GRAY));

        // --- Row 3: quick actions + Mega Showdown features ---
        renderFeature(MAX_IV_SLOT, FeatureGate.Feature.IV_MAX_ALL,
                ModItems.rareCandy(),
                "Max All IVs",
                Formatting.AQUA,
                "Sets every IV to 31",
                null,
                cfg.prices.iv.maxAll);

        renderFeature(RESET_EV_SLOT, FeatureGate.Feature.EV_RESET_ALL,
                Items.EMERALD_BLOCK,
                "Reset All EVs",
                Formatting.GREEN,
                "Sets every EV to 0",
                null,
                cfg.prices.ev.resetAll);

        // Decorative separator block between the "quick" group and the Mega Showdown group.
        safeSet(30, ItemBuilder.glass(10).build());

        int dmaxLevel = safeDmaxLevel(p);
        int dmaxMax = MegaShowdownBridge.maxDynamaxLevel();
        renderFeatureCustom(DYNAMAX_SLOT, FeatureGate.Feature.DYNAMAX_LEVEL,
                ItemBuilder.of(ModItems.dynamaxCandy())
                        .name("Dynamax Level", Formatting.LIGHT_PURPLE, Formatting.BOLD)
                        .lore("Current: " + dmaxLevel + " / " + dmaxMax, Formatting.WHITE)
                        .lore("Cost per level: " + TextHelper.formatMoney(cfg.prices.dynamaxLevelPerStep) + " " + TextHelper.currencyName(), Formatting.YELLOW)
                        .lore("Click to open the level picker", Formatting.GRAY)
                        .count(Math.max(1, Math.min(64, dmaxLevel + 1)))
                        .glow(dmaxLevel == dmaxMax && dmaxMax > 0));

        boolean gmax = safeGmax(p);
        renderFeatureCustom(GMAX_SLOT, FeatureGate.Feature.GMAX_FACTOR,
                ItemBuilder.of(ModItems.dynamaxBand())
                        .name("G-Max Factor: " + (gmax ? "YES" : "NO"),
                                Formatting.LIGHT_PURPLE, Formatting.BOLD)
                        .lore("Cost: " + TextHelper.formatMoney(cfg.prices.gmaxFactor) + " " + TextHelper.currencyName(), Formatting.YELLOW)
                        .lore("Click to toggle", Formatting.GRAY)
                        .glow(gmax));

        String teraName = safeTeraName(p);
        renderFeatureCustom(TERA_SLOT, FeatureGate.Feature.TERA_TYPE,
                ItemBuilder.of(ModItems.teraShard(teraName))
                        .name("Tera Type: " + capitalize(teraName), Formatting.LIGHT_PURPLE, Formatting.BOLD)
                        .lore("Cost: " + TextHelper.formatMoney(cfg.prices.teraType) + " " + TextHelper.currencyName(), Formatting.YELLOW)
                        .lore("Click to re-type", Formatting.GRAY)
                        .glow(true));

        safeSet(34, ItemBuilder.glass(10).build());

        // --- Registry-driven row (1.3.0+ purchasables) ---
        // Rendered AFTER the legacy rows so slot hints (20/22/24 today)
        // land in row 2 without colliding with the existing layout.
        renderPurchasables(p);

        // --- Bottom bar ---
        safeSet(BACK_SLOT, ItemBuilder.of(Items.ARROW).name("Back", Formatting.YELLOW).build());
        safeSet(BALANCE_SLOT, balanceItem());
        safeSet(CLOSE_SLOT, ItemBuilder.of(Items.BARRIER).name("Close", Formatting.RED).build());
    }

    // Render every Purchasable at its declared editorSlot(), gated by
    // feature + permission + eligibility. Disabled / ineligible slots
    // show a themed grey placeholder instead of the button itself.
    private void renderPurchasables(Pokemon pokemon) {
        for (var purchasable : com.antigravity.pokebuilder.service.PurchasableRegistry.ordered()) {
            int slot = purchasable.editorSlot();
            if (!FeatureGate.enabled(purchasable.feature())) {
                safeSet(slot, disabledItem(purchasable.id()));
                continue;
            }
            try {
                var stack = purchasable.icon(pokemon, player);
                if (stack != null) safeSet(slot, stack);
            } catch (Throwable t) {
                PokeBuilder.LOGGER.warn("[PokeBuilder] Purchasable '{}' icon failed: {}",
                        purchasable.id(), t.toString());
            }
        }
    }

    // Renders a feature button. If the feature is disabled, swaps the button
    // for a clearly-marked grey placeholder so admins and players can both see
    // the slot exists but can't interact with it.
    private void renderFeature(int slot, FeatureGate.Feature feature, Item icon, String label,
                               Formatting nameColor, String line1, String line2, BigDecimal cost) {
        if (!FeatureGate.enabled(feature)) {
            safeSet(slot, disabledItem(label));
            return;
        }
        ItemBuilder b = ItemBuilder.of(icon)
                .name(label, nameColor, Formatting.BOLD);
        if (line1 != null) b.lore(line1, Formatting.WHITE);
        if (line2 != null) b.lore(line2, Formatting.DARK_GRAY);
        if (cost != null) {
            b.lore("Cost: " + TextHelper.formatMoney(cost) + " " + TextHelper.currencyName(), Formatting.YELLOW);
        }
        safeSet(slot, b.build());
    }

    private void renderFeatureCustom(int slot, FeatureGate.Feature feature, ItemBuilder builder) {
        if (!FeatureGate.enabled(feature)) {
            // Look up label from the builder's stack (best-effort)
            safeSet(slot, disabledItem(""));
            return;
        }
        safeSet(slot, builder.build());
    }

    private static ItemStack disabledItem(String label) {
        return ItemBuilder.of(Items.GRAY_STAINED_GLASS_PANE)
                .name((label.isEmpty() ? "Disabled" : label),
                        Formatting.DARK_GRAY, Formatting.BOLD, Formatting.STRIKETHROUGH)
                .lore("Disabled by the server admin.", Formatting.RED)
                .lore("Ask an operator to re-enable it.", Formatting.DARK_GRAY)
                .build();
    }

    private ItemStack infoItem(Pokemon p) {
        ItemBuilder b = ItemBuilder.of(ModItems.pokemonIcon(p))
                .name(capitalize(p.getSpecies().getName()) + "  Lv." + p.getLevel(),
                        Formatting.GREEN, Formatting.BOLD)
                .lore("Nature: " + capitalize(p.getNature().getName().getPath()), Formatting.GRAY)
                .lore("Ability: " + (p.getAbility() != null ? p.getAbility().getTemplate().getName() : "—"), Formatting.GRAY)
                .lore("Shiny: " + (p.getShiny() ? "Yes" : "No"), Formatting.GRAY)
                .lore("Gender: " + p.getGender().name(), Formatting.GRAY);
        try {
            if (p.getTeraType() != null) {
                b.lore("Tera: " + capitalize(p.getTeraType().getName()), Formatting.LIGHT_PURPLE);
            }
        } catch (Throwable ignored) {}
        try {
            int d = p.getDmaxLevel();
            if (d > 0) b.lore("Dynamax: " + d, Formatting.LIGHT_PURPLE);
        } catch (Throwable ignored) {}
        try {
            if (p.getGmaxFactor()) b.lore("G-Max Factor: yes", Formatting.LIGHT_PURPLE);
        } catch (Throwable ignored) {}
        return b.build();
    }

    private ItemStack balanceItem() {
        BigDecimal balance = EconomyHandler.isReady()
                ? EconomyHandler.getBalance(player.getUuid()) : BigDecimal.ZERO;
        return ItemBuilder.of(Items.GOLD_INGOT)
                .name("Balance: " + TextHelper.formatMoney(balance) + " " + TextHelper.currencyName(),
                        Formatting.GOLD, Formatting.BOLD)
                .build();
    }

    private int safeDmaxLevel(Pokemon p) {
        try { return p.getDmaxLevel(); } catch (Throwable t) { return 0; }
    }

    private boolean safeGmax(Pokemon p) {
        try { return p.getGmaxFactor(); } catch (Throwable t) { return false; }
    }

    private String safeTeraName(Pokemon p) {
        try {
            if (p.getTeraType() == null) return "normal";
            String n = p.getTeraType().getName();
            return n == null ? "normal" : n;
        } catch (Throwable t) { return "normal"; }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "—";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action) {
        switch (slot) {
            case BACK_SLOT -> { SoundHelper.back(player); new PartySelectGui(player).open(); }
            case CLOSE_SLOT -> close();
            case IV_SLOT -> {
                if (FeatureGate.enforce(FeatureGate.Feature.IV_EDITOR, player))
                    new IVEditorGui(player, pokemonId).open();
            }
            case EV_SLOT -> {
                if (FeatureGate.enforce(FeatureGate.Feature.EV_EDITOR, player))
                    new EVEditorGui(player, pokemonId).open();
            }
            case NATURE_SLOT -> {
                if (FeatureGate.enforce(FeatureGate.Feature.NATURE, player))
                    new NatureSelectGui(player, pokemonId).open();
            }
            case ABILITY_SLOT -> {
                if (FeatureGate.enforce(FeatureGate.Feature.ABILITY, player))
                    new AbilitySelectGui(player, pokemonId).open();
            }
            case BALL_SLOT -> {
                if (FeatureGate.enforce(FeatureGate.Feature.BALL, player))
                    new BallSelectGui(player, pokemonId).open();
            }
            case SHINY_SLOT -> { if (FeatureGate.enforce(FeatureGate.Feature.SHINY, player)) doShiny(); }
            case GENDER_SLOT -> { if (FeatureGate.enforce(FeatureGate.Feature.GENDER, player)) doGender(); }
            case MAX_IV_SLOT -> { if (FeatureGate.enforce(FeatureGate.Feature.IV_MAX_ALL, player)) doMaxIv(); }
            case RESET_EV_SLOT -> { if (FeatureGate.enforce(FeatureGate.Feature.EV_RESET_ALL, player)) doResetEv(); }
            case DYNAMAX_SLOT -> {
                if (FeatureGate.enforce(FeatureGate.Feature.DYNAMAX_LEVEL, player))
                    new DynamaxLevelGui(player, pokemonId).open();
            }
            case GMAX_SLOT -> {
                if (FeatureGate.enforce(FeatureGate.Feature.GMAX_FACTOR, player))
                    new GmaxToggleGui(player, pokemonId).open();
            }
            case TERA_SLOT -> {
                if (FeatureGate.enforce(FeatureGate.Feature.TERA_TYPE, player))
                    new TeraTypeSelectGui(player, pokemonId).open();
            }
            default -> dispatchRegistry(slot);
        }
    }

    // Route clicks on any registered Purchasable's editorSlot() to its
    // invoke() handler. Legacy hardcoded cases above handle 1.2.x
    // features; this catches registry-driven 1.3.0+ features.
    private void dispatchRegistry(int slot) {
        for (var purchasable : com.antigravity.pokebuilder.service.PurchasableRegistry.ordered()) {
            if (purchasable.editorSlot() != slot) continue;
            if (!FeatureGate.enforce(purchasable.feature(), player)) return;
            try {
                purchasable.invoke(player, pokemonId);
            } catch (Throwable t) {
                PokeBuilder.LOGGER.error("[PokeBuilder] Purchasable '{}' invoke failed",
                        purchasable.id(), t);
            }
            return;
        }
    }

    private void doShiny() {
        BigDecimal cost = PokeBuilder.get().config().prices.shinyToggle;
        Pokemon current = CobblemonBridge.findByUuid(player, pokemonId);
        if (current == null) { close(); return; }
        boolean willBeShiny = !current.getShiny();

        TransactionService.Result r = TransactionService.execute(player, pokemonId,
                FeatureGate.Feature.SHINY,
                "SHINY_TOGGLE", willBeShiny ? "ON" : "OFF", cost,
                PokemonModifier::toggleShiny);
        if (r == TransactionService.Result.SUCCESS) {
            if (willBeShiny) SoundHelper.shinyOn(player); else SoundHelper.shinyOff(player);
        }
        refresh();
    }

    private void doGender() {
        Pokemon current = CobblemonBridge.findByUuid(player, pokemonId);
        if (current == null) { close(); return; }
        if (!PokemonValidator.allowGenderSwap(current)) {
            player.sendMessage(Text.literal("This Pokémon's gender cannot be changed.").formatted(Formatting.RED), false);
            return;
        }
        BigDecimal cost = PokeBuilder.get().config().prices.genderSwap;
        TransactionService.execute(player, pokemonId, FeatureGate.Feature.GENDER,
                "GENDER_SWAP", "toggle", cost, PokemonModifier::swapGender);
        refresh();
    }

    private void doMaxIv() {
        BigDecimal cost = PokeBuilder.get().config().prices.iv.maxAll;
        java.util.function.Predicate<Pokemon> notAllMaxed = pm -> {
            for (com.cobblemon.mod.common.api.pokemon.stats.Stats s : CobblemonBridge.allStats())
                if (pm.getIvs().get(s) < 31) return true;
            return false;
        };
        if (cost.compareTo(PokeBuilder.get().config().confirmThreshold) > 0) {
            new ConfirmationGui(player, pokemonId, "Max All IVs", cost, p -> {
                TransactionService.execute(player, pokemonId, FeatureGate.Feature.IV_MAX_ALL,
                        "IV_MAX_ALL", "all31", cost, notAllMaxed, PokemonModifier::maxAllIvs);
                new EditorGui(player, pokemonId).open();
            }).open();
            return;
        }
        TransactionService.execute(player, pokemonId, FeatureGate.Feature.IV_MAX_ALL,
                "IV_MAX_ALL", "all31", cost, notAllMaxed, PokemonModifier::maxAllIvs);
        refresh();
    }

    private void doResetEv() {
        BigDecimal cost = PokeBuilder.get().config().prices.ev.resetAll;
        TransactionService.execute(player, pokemonId, FeatureGate.Feature.EV_RESET_ALL,
                "EV_RESET_ALL", "all0", cost,
                pm -> {
                    for (com.cobblemon.mod.common.api.pokemon.stats.Stats s : CobblemonBridge.allStats())
                        if (pm.getEvs().get(s) > 0) return true;
                    return false;
                },
                PokemonModifier::resetAllEvs);
        refresh();
    }

    @Override
    protected void onOpen() {
        SoundHelper.openMenu(player);
    }

    public UUID pokemonId() { return pokemonId; }
}
