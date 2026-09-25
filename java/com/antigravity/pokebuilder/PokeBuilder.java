package com.antigravity.pokebuilder;

import com.antigravity.pokebuilder.admin.ChatInputManager;
import com.antigravity.pokebuilder.command.PokeBuilderCommand;
import com.antigravity.pokebuilder.config.ConfigLoader;
import com.antigravity.pokebuilder.config.PokeBuilderConfig;
import com.antigravity.pokebuilder.economy.EconomyHandler;
import com.antigravity.pokebuilder.gui.base.GuiManager;
import com.antigravity.pokebuilder.logging.AuditLogger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PokeBuilder implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(PokeBuilderConstants.MOD_NAME);

    private static PokeBuilder instance;
    private PokeBuilderConfig config;
    private MinecraftServer server;
    private boolean enabled = false;
    private boolean dirty = false;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("[PokeBuilder] Initializing...");

        if (!FabricLoader.getInstance().isModLoaded("cobblemon")) {
            LOGGER.error("[PokeBuilder] Cobblemon not found! Mod will be inactive.");
        } else {
            String v = FabricLoader.getInstance().getModContainer("cobblemon")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
            LOGGER.info("[PokeBuilder] Detected Cobblemon version: {}", v);
        }

        if (!FabricLoader.getInstance().isModLoaded("impactor")) {
            LOGGER.error("[PokeBuilder] Impactor not found! Mod will be inactive.");
        }

        this.config = ConfigLoader.load();
        AuditLogger.init(config);

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                PokeBuilderCommand.register(dispatcher));

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (sender == null) return true;
            if (ChatInputManager.hasPending(sender.getUuid())) {
                ChatInputManager.consume(sender.getUuid(), message.getContent().getString());
                return false;
            }
            return true;
        });

        LOGGER.info("[PokeBuilder] Ready.");
    }

    private void onServerStarted(MinecraftServer server) {
        this.server = server;
        boolean cobblemon = FabricLoader.getInstance().isModLoaded("cobblemon");
        boolean impactor = FabricLoader.getInstance().isModLoaded("impactor");
        if (!cobblemon || !impactor) {
            LOGGER.error("[PokeBuilder] Missing dependencies — disabled at runtime.");
            this.enabled = false;
            return;
        }
        if (!EconomyHandler.init()) {
            LOGGER.error("[PokeBuilder] Economy service unavailable — disabled at runtime.");
            this.enabled = false;
            return;
        }

        // Register 1.3.0 Purchasables once the server is fully up. The
        // registry is frozen afterwards so no other mod can splice in
        // mid-session. Legacy features (IV/EV/nature/etc.) still run
        // through EditorGui's bespoke handlers and will be migrated
        // behind this interface in a later release.
        if (!com.antigravity.pokebuilder.service.PurchasableRegistry.isFrozen()) {
            com.antigravity.pokebuilder.service.PurchasableRegistry.register(
                    new com.antigravity.pokebuilder.service.purchasables.NicknamePurchasable());
            com.antigravity.pokebuilder.service.PurchasableRegistry.register(
                    new com.antigravity.pokebuilder.service.purchasables.FriendshipPurchasable());
            com.antigravity.pokebuilder.service.PurchasableRegistry.register(
                    new com.antigravity.pokebuilder.service.purchasables.StatusCurePurchasable());
            com.antigravity.pokebuilder.service.PurchasableRegistry.freeze();
            LOGGER.info("[PokeBuilder] Registered {} purchasables.",
                    com.antigravity.pokebuilder.service.PurchasableRegistry.size());
        }

        this.enabled = true;
        LOGGER.info("[PokeBuilder] Enabled. Dependencies resolved.");
    }

    private void onServerStopping(MinecraftServer server) {
        GuiManager.closeAll();
        AuditLogger.shutdown();
        this.enabled = false;
    }

    public void reloadConfig() {
        this.config = ConfigLoader.load();
        AuditLogger.init(config);
        // Re-resolve the Impactor currency from the (possibly changed)
        // economy.currency-id so /pokebuilder reload actually picks up
        // currency edits without needing a full server restart. If the
        // service is unavailable (e.g. Impactor still missing), init()
        // itself logs the failure and leaves enabled=false — do not
        // override that state here.
        if (FabricLoader.getInstance().isModLoaded("impactor")) {
            EconomyHandler.init();
        }
        this.dirty = false;
        LOGGER.info("[PokeBuilder] Configuration reloaded.");
    }

    public boolean saveConfig() {
        boolean ok = com.antigravity.pokebuilder.config.ConfigWriter.save(this.config);
        if (ok) this.dirty = false;
        return ok;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public static PokeBuilder get() {
        return instance;
    }

    public PokeBuilderConfig config() {
        return config;
    }

    public MinecraftServer server() {
        return server;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
