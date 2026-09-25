package com.antigravity.pokebuilder.config;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.messages.LanguageLoader;
import com.antigravity.pokebuilder.messages.PokeBuilderMessages;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {

    private static final String FILE_NAME = "config.conf";

    private ConfigLoader() {}

    public static PokeBuilderConfig load() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("pokebuilder");
        Path file = dir.resolve(FILE_NAME);

        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            if (!Files.exists(file)) {
                writeDefault(file);
            }
        } catch (IOException e) {
            PokeBuilder.LOGGER.error("[PokeBuilder] Failed to prepare config directory", e);
            return new PokeBuilderConfig();
        }

        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .path(file)
                .build();

        try {
            CommentedConfigurationNode root = loader.load();
            ConfigurationNode node = root.node("pokebuilder");
            PokeBuilderConfig cfg = readConfig(node);
            cfg.validate();
            PokeBuilder.LOGGER.info("[PokeBuilder] Config loaded from {}", file);
            return cfg;
        } catch (Exception e) {
            PokeBuilder.LOGGER.error("[PokeBuilder] Failed to load config — using defaults", e);
            return new PokeBuilderConfig();
        }
    }

    private static void writeDefault(Path file) throws IOException {
        try (InputStream in = ConfigLoader.class.getResourceAsStream(
                "/assets/pokebuilder/default_config.conf")) {
            if (in == null) {
                Files.writeString(file, "pokebuilder {}\n");
                return;
            }
            Files.copy(in, file);
        }
    }

    private static PokeBuilderConfig readConfig(ConfigurationNode n) {
        PokeBuilderConfig cfg = new PokeBuilderConfig();

        ConfigurationNode eco = n.node("economy");
        cfg.economy.currencyId = eco.node("currency-id").getString(cfg.economy.currencyId);
        cfg.economy.displayName = eco.node("display-name").getString(cfg.economy.displayName);
        cfg.economy.format = eco.node("format").getString(cfg.economy.format);

        ConfigurationNode prices = n.node("prices");
        cfg.prices.iv.setSingle31 = bd(prices.node("iv", "set-single-31"), cfg.prices.iv.setSingle31);
        cfg.prices.iv.setSingle0 = bd(prices.node("iv", "set-single-0"), cfg.prices.iv.setSingle0);
        cfg.prices.iv.maxAll = bd(prices.node("iv", "max-all"), cfg.prices.iv.maxAll);
        cfg.prices.ev.setSingle252 = bd(prices.node("ev", "set-single-252"), cfg.prices.ev.setSingle252);
        cfg.prices.ev.setSingle0 = bd(prices.node("ev", "set-single-0"), cfg.prices.ev.setSingle0);
        cfg.prices.ev.resetAll = bd(prices.node("ev", "reset-all"), cfg.prices.ev.resetAll);
        cfg.prices.ev.add10 = bd(prices.node("ev", "add-10"), cfg.prices.ev.add10);
        cfg.prices.nature = bd(prices.node("nature"), cfg.prices.nature);
        cfg.prices.ability.normal = bd(prices.node("ability", "normal"), cfg.prices.ability.normal);
        cfg.prices.ability.hidden = bd(prices.node("ability", "hidden"), cfg.prices.ability.hidden);
        cfg.prices.shinyToggle = bd(prices.node("shiny-toggle"), cfg.prices.shinyToggle);
        cfg.prices.genderSwap = bd(prices.node("gender-swap"), cfg.prices.genderSwap);
        cfg.prices.ballChange = bd(prices.node("ball-change"), cfg.prices.ballChange);
        cfg.prices.dynamaxLevelPerStep = bd(prices.node("dynamax-level-per-step"), cfg.prices.dynamaxLevelPerStep);
        cfg.prices.gmaxFactor = bd(prices.node("gmax-factor"), cfg.prices.gmaxFactor);
        cfg.prices.teraType = bd(prices.node("tera-type"), cfg.prices.teraType);
        cfg.prices.nickname = bd(prices.node("nickname"), cfg.prices.nickname);
        cfg.prices.friendshipPerStep = bd(prices.node("friendship-per-step"), cfg.prices.friendshipPerStep);
        cfg.prices.statusCure = bd(prices.node("status-cure"), cfg.prices.statusCure);

        cfg.confirmThreshold = bd(n.node("confirm-threshold"), cfg.confirmThreshold);

        ConfigurationNode feat = n.node("features");
        cfg.features.ivEditor = feat.node("iv-editor").getBoolean(cfg.features.ivEditor);
        cfg.features.ivMaxAll = feat.node("iv-max-all").getBoolean(cfg.features.ivMaxAll);
        cfg.features.evEditor = feat.node("ev-editor").getBoolean(cfg.features.evEditor);
        cfg.features.evResetAll = feat.node("ev-reset-all").getBoolean(cfg.features.evResetAll);
        cfg.features.nature = feat.node("nature").getBoolean(cfg.features.nature);
        cfg.features.ability = feat.node("ability").getBoolean(cfg.features.ability);
        cfg.features.abilityHidden = feat.node("ability-hidden").getBoolean(cfg.features.abilityHidden);
        cfg.features.shiny = feat.node("shiny").getBoolean(cfg.features.shiny);
        cfg.features.gender = feat.node("gender").getBoolean(cfg.features.gender);
        cfg.features.ball = feat.node("ball").getBoolean(cfg.features.ball);
        cfg.features.dynamaxLevel = feat.node("dynamax-level").getBoolean(cfg.features.dynamaxLevel);
        cfg.features.gmaxFactor = feat.node("gmax-factor").getBoolean(cfg.features.gmaxFactor);
        cfg.features.teraType = feat.node("tera-type").getBoolean(cfg.features.teraType);
        cfg.features.nickname = feat.node("nickname").getBoolean(cfg.features.nickname);
        cfg.features.friendship = feat.node("friendship").getBoolean(cfg.features.friendship);
        cfg.features.statusCure = feat.node("status-cure").getBoolean(cfg.features.statusCure);
        cfg.features.pcAccess = feat.node("pc-access").getBoolean(cfg.features.pcAccess);

        // Tiers: map id → multiplier. "default" is pulled out so the
        // Tiers pojo can hold it on a dedicated field; everything else
        // goes into the LinkedHashMap in declaration order.
        ConfigurationNode tiers = n.node("tiers");
        if (!tiers.virtual()) {
            cfg.tiers.tiers.clear();
            try {
                for (var entry : tiers.childrenMap().entrySet()) {
                    String id = String.valueOf(entry.getKey());
                    String raw = entry.getValue().getString();
                    if (raw == null) continue;
                    try {
                        java.math.BigDecimal mult = new java.math.BigDecimal(raw);
                        if ("default".equalsIgnoreCase(id)) {
                            cfg.tiers.defaultMultiplier = mult;
                        } else {
                            cfg.tiers.tiers.put(id.toLowerCase(java.util.Locale.ROOT), mult);
                        }
                    } catch (NumberFormatException nfe) {
                        PokeBuilder.LOGGER.warn("[PokeBuilder] Invalid tier multiplier '{}' for '{}' — ignored",
                                raw, id);
                    }
                }
            } catch (Exception ignored) {}
        }

        ConfigurationNode sec = n.node("security");
        cfg.security.clickCooldownMs = sec.node("click-cooldown-ms").getLong(cfg.security.clickCooldownMs);
        cfg.security.maxModificationsPerMinute = sec.node("max-modifications-per-minute")
                .getInt(cfg.security.maxModificationsPerMinute);
        cfg.security.allowCreativeMode = sec.node("allow-creative-mode").getBoolean(cfg.security.allowCreativeMode);
        cfg.security.allowFaintedPokemon = sec.node("allow-fainted-pokemon").getBoolean(cfg.security.allowFaintedPokemon);
        cfg.security.allowBypassPermissions = sec.node("allow-bypass-permissions").getBoolean(cfg.security.allowBypassPermissions);

        ConfigurationNode res = n.node("restrictions");
        cfg.restrictions.blockLegendaries = res.node("block-legendaries").getBoolean(cfg.restrictions.blockLegendaries);
        cfg.restrictions.blockMythicals = res.node("block-mythicals").getBoolean(cfg.restrictions.blockMythicals);
        cfg.restrictions.blockUltraBeasts = res.node("block-ultra-beasts").getBoolean(cfg.restrictions.blockUltraBeasts);
        cfg.restrictions.blockParadox = res.node("block-paradox").getBoolean(cfg.restrictions.blockParadox);
        try {
            cfg.restrictions.blockedSpecies = res.node("blocked-species").getList(String.class, cfg.restrictions.blockedSpecies);
            cfg.restrictions.blockedModifications = res.node("blocked-modifications").getList(String.class, cfg.restrictions.blockedModifications);
        } catch (Exception ignored) {}

        ConfigurationNode snd = n.node("sounds");
        cfg.sounds.enabled = snd.node("enabled").getBoolean(cfg.sounds.enabled);
        cfg.sounds.volume = (float) snd.node("volume").getDouble(cfg.sounds.volume);
        cfg.sounds.pitch = (float) snd.node("pitch").getDouble(cfg.sounds.pitch);

        ConfigurationNode log = n.node("logging");
        cfg.logging.logToFile = log.node("log-to-file").getBoolean(cfg.logging.logToFile);
        cfg.logging.logFile = log.node("log-file").getString(cfg.logging.logFile);
        cfg.logging.logToConsole = log.node("log-to-console").getBoolean(cfg.logging.logToConsole);

        ConfigurationNode looks = n.node("looks");
        cfg.looks.borderColor = looks.node("border-color").getInt(cfg.looks.borderColor);
        cfg.looks.confirmButtonColor = looks.node("confirm-button-color").getInt(cfg.looks.confirmButtonColor);
        cfg.looks.cancelButtonColor = looks.node("cancel-button-color").getInt(cfg.looks.cancelButtonColor);

        // Resolve the locale first, then rebuild the messages registry on
        // top of the locale's JSON base. HOCON overrides (the messages{}
        // block below) layer on top of that base so only actual admin
        // deltas end up in the map.
        cfg.messageLocale = n.node("message-locale").getString(cfg.messageLocale);
        cfg.messages = new PokeBuilderMessages(LanguageLoader.load(cfg.messageLocale));

        ConfigurationNode msgs = n.node("messages");
        if (!msgs.virtual()) {
            try {
                for (var entry : msgs.childrenMap().entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    String value = entry.getValue().getString();
                    if (value != null) cfg.messages.put(key, value);
                }
            } catch (Exception ignored) {}
        }

        return cfg;
    }

    private static BigDecimal bd(ConfigurationNode node, BigDecimal fallback) {
        if (node.virtual() || node.empty()) return fallback;
        String raw = node.getString();
        if (raw == null) return fallback;
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
