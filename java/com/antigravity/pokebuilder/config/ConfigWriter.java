package com.antigravity.pokebuilder.config;

import com.antigravity.pokebuilder.PokeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public final class ConfigWriter {

    private static final String FILE_NAME = "config.conf";

    private ConfigWriter() {}

    public static boolean save(PokeBuilderConfig cfg) {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("pokebuilder");
        Path target = dir.resolve(FILE_NAME);
        Path tmp = dir.resolve(FILE_NAME + ".tmp");

        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);

            HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                    .path(tmp)
                    .build();

            CommentedConfigurationNode root = loader.createNode();
            CommentedConfigurationNode n = root.node("pokebuilder");

            n.node("economy", "currency-id").set(cfg.economy.currencyId);
            n.node("economy", "display-name").set(cfg.economy.displayName);
            n.node("economy", "format").set(cfg.economy.format);

            n.node("prices", "iv", "set-single-31").set(cfg.prices.iv.setSingle31.toPlainString());
            n.node("prices", "iv", "set-single-0").set(cfg.prices.iv.setSingle0.toPlainString());
            n.node("prices", "iv", "max-all").set(cfg.prices.iv.maxAll.toPlainString());
            n.node("prices", "ev", "set-single-252").set(cfg.prices.ev.setSingle252.toPlainString());
            n.node("prices", "ev", "set-single-0").set(cfg.prices.ev.setSingle0.toPlainString());
            n.node("prices", "ev", "reset-all").set(cfg.prices.ev.resetAll.toPlainString());
            n.node("prices", "ev", "add-10").set(cfg.prices.ev.add10.toPlainString());
            n.node("prices", "nature").set(cfg.prices.nature.toPlainString());
            n.node("prices", "ability", "normal").set(cfg.prices.ability.normal.toPlainString());
            n.node("prices", "ability", "hidden").set(cfg.prices.ability.hidden.toPlainString());
            n.node("prices", "shiny-toggle").set(cfg.prices.shinyToggle.toPlainString());
            n.node("prices", "gender-swap").set(cfg.prices.genderSwap.toPlainString());
            n.node("prices", "ball-change").set(cfg.prices.ballChange.toPlainString());
            n.node("prices", "dynamax-level-per-step").set(cfg.prices.dynamaxLevelPerStep.toPlainString());
            n.node("prices", "gmax-factor").set(cfg.prices.gmaxFactor.toPlainString());
            n.node("prices", "tera-type").set(cfg.prices.teraType.toPlainString());
            n.node("prices", "nickname").set(cfg.prices.nickname.toPlainString());
            n.node("prices", "friendship-per-step").set(cfg.prices.friendshipPerStep.toPlainString());
            n.node("prices", "status-cure").set(cfg.prices.statusCure.toPlainString());

            n.node("tiers", "default").set(cfg.tiers.defaultMultiplier.toPlainString());
            for (Map.Entry<String, java.math.BigDecimal> tier : cfg.tiers.tiers.entrySet()) {
                n.node("tiers", tier.getKey()).set(tier.getValue().toPlainString());
            }

            n.node("confirm-threshold").set(cfg.confirmThreshold.toPlainString());

            n.node("features", "iv-editor").set(cfg.features.ivEditor);
            n.node("features", "iv-max-all").set(cfg.features.ivMaxAll);
            n.node("features", "ev-editor").set(cfg.features.evEditor);
            n.node("features", "ev-reset-all").set(cfg.features.evResetAll);
            n.node("features", "nature").set(cfg.features.nature);
            n.node("features", "ability").set(cfg.features.ability);
            n.node("features", "ability-hidden").set(cfg.features.abilityHidden);
            n.node("features", "shiny").set(cfg.features.shiny);
            n.node("features", "gender").set(cfg.features.gender);
            n.node("features", "ball").set(cfg.features.ball);
            n.node("features", "dynamax-level").set(cfg.features.dynamaxLevel);
            n.node("features", "gmax-factor").set(cfg.features.gmaxFactor);
            n.node("features", "tera-type").set(cfg.features.teraType);
            n.node("features", "nickname").set(cfg.features.nickname);
            n.node("features", "friendship").set(cfg.features.friendship);
            n.node("features", "status-cure").set(cfg.features.statusCure);
            n.node("features", "pc-access").set(cfg.features.pcAccess);

            n.node("security", "click-cooldown-ms").set(cfg.security.clickCooldownMs);
            n.node("security", "max-modifications-per-minute").set(cfg.security.maxModificationsPerMinute);
            n.node("security", "allow-creative-mode").set(cfg.security.allowCreativeMode);
            n.node("security", "allow-fainted-pokemon").set(cfg.security.allowFaintedPokemon);
            n.node("security", "allow-bypass-permissions").set(cfg.security.allowBypassPermissions);

            n.node("restrictions", "block-legendaries").set(cfg.restrictions.blockLegendaries);
            n.node("restrictions", "block-mythicals").set(cfg.restrictions.blockMythicals);
            n.node("restrictions", "block-ultra-beasts").set(cfg.restrictions.blockUltraBeasts);
            n.node("restrictions", "block-paradox").set(cfg.restrictions.blockParadox);
            n.node("restrictions", "blocked-species").setList(String.class, cfg.restrictions.blockedSpecies);
            n.node("restrictions", "blocked-modifications").setList(String.class, cfg.restrictions.blockedModifications);

            n.node("sounds", "enabled").set(cfg.sounds.enabled);
            n.node("sounds", "volume").set((double) cfg.sounds.volume);
            n.node("sounds", "pitch").set((double) cfg.sounds.pitch);

            n.node("logging", "log-to-file").set(cfg.logging.logToFile);
            n.node("logging", "log-file").set(cfg.logging.logFile);
            n.node("logging", "log-to-console").set(cfg.logging.logToConsole);

            n.node("looks", "border-color").set(cfg.looks.borderColor);
            n.node("looks", "confirm-button-color").set(cfg.looks.confirmButtonColor);
            n.node("looks", "cancel-button-color").set(cfg.looks.cancelButtonColor);

            n.node("message-locale").set(cfg.messageLocale);

            // Only persist admin deltas. The full default set lives in
            // assets/pokebuilder/lang/<locale>.json — re-serializing all
            // ~60 strings would bloat config.conf and make every fresh
            // install look like it had been heavily customized.
            for (Map.Entry<String, String> e : cfg.messages.overrides().entrySet()) {
                n.node("messages", e.getKey()).set(e.getValue());
            }

            loader.save(root);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            PokeBuilder.LOGGER.info("[PokeBuilder] Config saved to {}", target);
            return true;
        } catch (IOException | RuntimeException e) {
            PokeBuilder.LOGGER.error("[PokeBuilder] Failed to save config", e);
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            return false;
        }
    }

    public static BigDecimal clampNonNegative(BigDecimal input) {
        return input.signum() < 0 ? BigDecimal.ZERO : input;
    }
}
