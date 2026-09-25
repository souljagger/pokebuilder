package com.antigravity.pokebuilder.command;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.PokeBuilderConstants;
import com.antigravity.pokebuilder.gui.PartySelectGui;
import com.antigravity.pokebuilder.gui.admin.AdminGui;
import com.antigravity.pokebuilder.gui.base.GuiManager;
import com.antigravity.pokebuilder.logging.AuditLogReader;
import com.antigravity.pokebuilder.util.PermissionHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import static net.minecraft.server.command.CommandManager.literal;

public final class PokeBuilderCommand {

    private PokeBuilderCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> root = literal("pokebuilder")
                .executes(PokeBuilderCommand::openParty)
                .then(literal("reload")
                        .requires(src -> PermissionHelper.has(src, PokeBuilderConstants.PERM_ADMIN_RELOAD, 3))
                        .executes(PokeBuilderCommand::reload))
                .then(literal("save")
                        .requires(src -> PermissionHelper.has(src, PokeBuilderConstants.PERM_ADMIN_SAVE, 4))
                        .executes(PokeBuilderCommand::save))
                .then(literal("admin")
                        .requires(src -> PermissionHelper.has(src, PokeBuilderConstants.PERM_ADMIN_GUI, 4))
                        .executes(PokeBuilderCommand::openAdmin))
                .then(literal("debug")
                        .requires(src -> PermissionHelper.has(src, PokeBuilderConstants.PERM_ADMIN_DEBUG, 4))
                        .executes(PokeBuilderCommand::debug))
                .then(literal("history")
                        .executes(ctx -> history(ctx, 1))
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                .<ServerCommandSource, Integer>argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> history(ctx, IntegerArgumentType.getInteger(ctx, "page"))))
                        .then(literal("player")
                                .requires(src -> PermissionHelper.has(src,
                                        PokeBuilderConstants.PERM_ADMIN_HISTORY, 3))
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                        .<ServerCommandSource, String>argument("name", StringArgumentType.word())
                                        .executes(ctx -> historyForOther(ctx,
                                                StringArgumentType.getString(ctx, "name"), 1))
                                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                                .<ServerCommandSource, Integer>argument("page", IntegerArgumentType.integer(1))
                                                .executes(ctx -> historyForOther(ctx,
                                                        StringArgumentType.getString(ctx, "name"),
                                                        IntegerArgumentType.getInteger(ctx, "page")))))))
                .then(literal("version").executes(PokeBuilderCommand::version));

        dispatcher.register(root);
        dispatcher.register(literal("pokebuild").executes(PokeBuilderCommand::openParty));
        LiteralArgumentBuilder<ServerCommandSource> pb = literal("pb")
                .executes(PokeBuilderCommand::openParty)
                // Player-facing shortcut so folks don't have to type
                // /pokebuilder history — /pb history works too.
                .then(literal("history")
                        .executes(ctx -> history(ctx, 1))
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                .<ServerCommandSource, Integer>argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> history(ctx, IntegerArgumentType.getInteger(ctx, "page")))));
        dispatcher.register(pb);
    }

    private static int openParty(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayerOrThrow();
        } catch (Exception e) {
            src.sendError(PokeBuilder.get().config().messages.get("command.not_player"));
            return 0;
        }

        if (!PokeBuilder.get().isEnabled()) {
            player.sendMessage(PokeBuilder.get().config().messages.get("command.disabled"), false);
            return 0;
        }

        if (!PermissionHelper.has(src, PokeBuilderConstants.PERM_USE, 0)) {
            player.sendMessage(PokeBuilder.get().config().messages.get("command.no_permission"), false);
            return 0;
        }

        if (player.interactionManager.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage(PokeBuilder.get().config().messages.get("command.spectator_blocked"), false);
            return 0;
        }

        if (!PokeBuilder.get().config().security.allowCreativeMode
                && player.interactionManager.getGameMode() == GameMode.CREATIVE) {
            player.sendMessage(PokeBuilder.get().config().messages.get("command.creative_blocked"), false);
            return 0;
        }

        if (GuiManager.isOpen(player.getUuid())) {
            player.sendMessage(PokeBuilder.get().config().messages.get("command.already_open"), false);
            return 0;
        }

        new PartySelectGui(player).open();
        return 1;
    }

    private static int reload(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        PokeBuilder.get().reloadConfig();
        ctx.getSource().sendFeedback(() -> PokeBuilder.get().config().messages.get("command.reloaded"), true);
        // Echo the currently-bound currency so admins editing currency-id
        // can confirm their change was picked up (common support question).
        try {
            var cur = com.antigravity.pokebuilder.economy.EconomyHandler.currency();
            if (cur != null) {
                ctx.getSource().sendFeedback(() -> net.minecraft.text.Text.literal(
                        "Economy currency: " + cur.key()
                ).formatted(net.minecraft.util.Formatting.GRAY), false);
            }
        } catch (Throwable ignored) {}
        return 1;
    }

    private static int save(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        boolean ok = PokeBuilder.get().saveConfig();
        String key = ok ? "command.saved" : "command.save_failed";
        ctx.getSource().sendFeedback(() -> PokeBuilder.get().config().messages.get(key), true);
        return ok ? 1 : 0;
    }

    private static int openAdmin(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayerOrThrow();
        } catch (Exception e) {
            src.sendError(PokeBuilder.get().config().messages.get("command.not_player"));
            return 0;
        }
        if (!PermissionHelper.has(src, PokeBuilderConstants.PERM_ADMIN_GUI, 4)) {
            player.sendMessage(PokeBuilder.get().config().messages.get("command.no_permission"), false);
            return 0;
        }
        if (GuiManager.isOpen(player.getUuid())) {
            player.sendMessage(PokeBuilder.get().config().messages.get("command.already_open"), false);
            return 0;
        }
        new AdminGui(player).open();
        return 1;
    }

    private static int version(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendFeedback(() -> Text.literal(
                PokeBuilderConstants.MOD_NAME + " v" + PokeBuilderConstants.VERSION
        ).formatted(Formatting.GOLD), false);
        return 1;
    }

    private static int history(CommandContext<ServerCommandSource> ctx, int page) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayerOrThrow();
        } catch (Exception e) {
            src.sendError(PokeBuilder.get().config().messages.get("command.not_player"));
            return 0;
        }
        return renderHistory(src, player.getUuid(), player.getName().getString(), page);
    }

    private static int historyForOther(CommandContext<ServerCommandSource> ctx, String name, int page) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity target = PokeBuilder.get().server().getPlayerManager().getPlayer(name);
        if (target == null) {
            src.sendError(Text.literal("Player not online: " + name).formatted(Formatting.RED));
            return 0;
        }
        return renderHistory(src, target.getUuid(), target.getName().getString(), page);
    }

    private static int renderHistory(ServerCommandSource src, UUID playerId, String playerName, int page) {
        int pageSize = 20;
        Path logFile = Path.of(PokeBuilder.get().config().logging.logFile);
        // Over-fetch by one page so we know whether a "next page" exists
        // without a second disk scan.
        List<AuditLogReader.Entry> all = AuditLogReader.tail(logFile, playerId, page * pageSize + 1);
        int startIdx = (page - 1) * pageSize;
        if (startIdx >= all.size()) {
            src.sendFeedback(() -> PokeBuilder.get().config().messages.get("history.empty"), false);
            return 0;
        }
        int endIdx = Math.min(startIdx + pageSize, all.size());
        boolean hasNext = all.size() > endIdx;
        int totalPages = hasNext ? page + 1 : page; // best-effort — we don't scan the whole log

        final int shownPage = page;
        final int shownTotal = totalPages;
        src.sendFeedback(() -> PokeBuilder.get().config().messages.get(
                "history.header", shownPage, shownTotal), false);

        for (int i = startIdx; i < endIdx; i++) {
            AuditLogReader.Entry e = all.get(i);
            final String ts = e.timestamp();
            final String txId = e.txId();
            final String action = e.action().toLowerCase();
            final String cost = e.cost();
            final String curr = " " + com.antigravity.pokebuilder.util.TextHelper.currencyName();
            src.sendFeedback(() -> PokeBuilder.get().config().messages.get(
                    "history.entry", ts, txId, action, cost, curr), false);
        }
        if (hasNext) {
            final int next = page + 1;
            src.sendFeedback(() -> PokeBuilder.get().config().messages.get(
                    "history.footer", next), false);
        }
        return 1;
    }

    // Dumps the economy state PokeBuilder sees — currency, account, balance,
    // list of registered currencies. Intended for support: an admin runs
    // this and pastes the output so we can diagnose currency routing bugs
    // without needing log access.
    private static int debug(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        java.util.UUID requester = null;
        try {
            ServerPlayerEntity p = src.getPlayer();
            if (p != null) requester = p.getUuid();
        } catch (Throwable ignored) {}

        src.sendFeedback(() -> Text.literal("=== PokeBuilder debug ===").formatted(Formatting.GOLD, Formatting.BOLD), false);
        src.sendFeedback(() -> Text.literal("version = " + PokeBuilderConstants.VERSION).formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("enabled = " + PokeBuilder.get().isEnabled()).formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("dirty   = " + PokeBuilder.get().isDirty()).formatted(Formatting.GRAY), false);

        var cfg = PokeBuilder.get().config();
        src.sendFeedback(() -> Text.literal("config.currency-id   = '" + cfg.economy.currencyId + "'").formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("config.display-name  = '" + cfg.economy.displayName + "'").formatted(Formatting.GRAY), false);

        for (String line : com.antigravity.pokebuilder.economy.EconomyHandler.dumpDebug(requester)) {
            src.sendFeedback(() -> Text.literal(line).formatted(Formatting.GRAY), false);
        }

        // Show whether the requester has any of the bypass nodes —
        // single most-asked support question is "why is my balance not
        // moving" and 9 times out of 10 the answer is "you have
        // pokebuilder.bypass.cost granted via wildcard". Surface it.
        ServerPlayerEntity player = null;
        try { player = src.getPlayer(); } catch (Throwable ignored) {}
        if (player != null) {
            boolean gate = cfg.security.allowBypassPermissions;
            boolean bypassCostPerm = com.antigravity.pokebuilder.util.PermissionHelper
                    .hasExplicit(player, PokeBuilderConstants.PERM_BYPASS_COST);
            boolean bypassCoolPerm = com.antigravity.pokebuilder.util.PermissionHelper
                    .hasExplicit(player, PokeBuilderConstants.PERM_BYPASS_COOLDOWN);
            boolean bypassCostEffective = gate && bypassCostPerm;
            boolean bypassCoolEffective = gate && bypassCoolPerm;

            src.sendFeedback(() -> Text.literal("security.allow-bypass-permissions = " + gate
                    + (gate ? "   ← bypass nodes ACTIVE" : "   ← bypass nodes inert (default safe)"))
                    .formatted(gate ? Formatting.YELLOW : Formatting.GREEN), false);
            src.sendFeedback(() -> Text.literal("bypass.cost     perm=" + bypassCostPerm
                    + " effective=" + bypassCostEffective
                    + (bypassCostEffective ? "   ← every purchase is FREE" : ""))
                    .formatted(bypassCostEffective ? Formatting.RED : Formatting.GRAY), false);
            src.sendFeedback(() -> Text.literal("bypass.cooldown perm=" + bypassCoolPerm
                    + " effective=" + bypassCoolEffective)
                    .formatted(bypassCoolEffective ? Formatting.YELLOW : Formatting.GRAY), false);
            if (bypassCostEffective) {
                src.sendFeedback(() -> Text.literal(
                        "  To turn the gate off: edit security.allow-bypass-permissions = false in config"
                ).formatted(Formatting.GRAY), false);
            } else if (gate && bypassCostPerm) {
                // Shouldn't reach here logically, but the comment is for the
                // permission-only-true case.
            } else if (!gate && bypassCostPerm) {
                src.sendFeedback(() -> Text.literal(
                        "  (Permission node is granted but ignored — gate is off.)"
                ).formatted(Formatting.GRAY), false);
            }
        }

        src.sendFeedback(() -> Text.literal("=========================").formatted(Formatting.GOLD, Formatting.BOLD), false);
        return 1;
    }
}
