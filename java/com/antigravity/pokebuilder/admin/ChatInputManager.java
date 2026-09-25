package com.antigravity.pokebuilder.admin;

import com.antigravity.pokebuilder.PokeBuilder;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

// Intercepts the next chat line from a given player and routes it to
// onConfirm / onCancel. Used for admin prices, messages, nicknames, etc.
//
// 1.3.0 improvements over the 1.2.x version:
//   - Optional `validator` runs before onConfirm; a rejection keeps the
//     pending state alive so the player can try again without opening
//     a fresh prompt. Replies to them with admin.chat_rejected.
//   - Optional `category` lets callers disambiguate stale pending
//     entries on GUI close (cancelIfPending uses it).
public final class ChatInputManager {

    public static final long TIMEOUT_MS = 30_000L;

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private ChatInputManager() {}

    public record Pending(String label, String category,
                          Predicate<String> validator,
                          Consumer<String> onConfirm, Runnable onCancel,
                          long expiresAt) {}

    public static void request(ServerPlayerEntity player, String label,
                               Consumer<String> onConfirm, Runnable onCancel) {
        request(player, label, null, null, onConfirm, onCancel);
    }

    // Full-fledged overload. `category` (nullable) enables
    // cancelIfPending() to target only the matching request. `validator`
    // (nullable) returns false to reject an input and keep the pending
    // alive; the caller is responsible for whatever side-effect message
    // the player should see — ChatInputManager just re-sends the
    // standard admin.chat_rejected notice.
    public static void request(ServerPlayerEntity player, String label,
                               String category, Predicate<String> validator,
                               Consumer<String> onConfirm, Runnable onCancel) {
        UUID id = player.getUuid();
        PENDING.put(id, new Pending(label, category, validator,
                onConfirm, onCancel,
                System.currentTimeMillis() + TIMEOUT_MS));
        player.sendMessage(PokeBuilder.get().config().messages.get("admin.chat_prompt", label), false);
    }

    public static boolean hasPending(UUID id) {
        Pending p = PENDING.get(id);
        if (p == null) return false;
        if (System.currentTimeMillis() > p.expiresAt) {
            PENDING.remove(id);
            return false;
        }
        return true;
    }

    // Consume the pending input. Returns true if the pending was
    // consumed (input accepted, or rejected-and-retry still consumes
    // the chat line so it doesn't broadcast). Returns false only if
    // there was no pending to match.
    public static boolean consume(UUID id, String rawMessage) {
        Pending p = PENDING.get(id);
        if (p == null) return false;
        if (System.currentTimeMillis() > p.expiresAt) {
            PENDING.remove(id);
            return false;
        }
        if (rawMessage == null) { PENDING.remove(id); return true; }
        String trimmed = rawMessage.trim();
        if (trimmed.equalsIgnoreCase("cancel")) {
            PENDING.remove(id);
            if (p.onCancel != null) {
                try { p.onCancel.run(); } catch (Throwable t) {
                    PokeBuilder.LOGGER.error("[PokeBuilder] ChatInput cancel failed", t);
                }
            }
            return true;
        }
        // Run the validator while the pending is still in place so a
        // rejection leaves the player able to retry with a fresh line.
        if (p.validator != null) {
            try {
                if (!p.validator.test(trimmed)) {
                    ServerPlayerEntity player = PokeBuilder.get().server().getPlayerManager().getPlayer(id);
                    if (player != null) {
                        player.sendMessage(PokeBuilder.get().config().messages.get("admin.chat_rejected"), false);
                    }
                    return true; // still consume the chat line, don't broadcast
                }
            } catch (Throwable t) {
                PokeBuilder.LOGGER.error("[PokeBuilder] ChatInput validator threw; accepting input", t);
            }
        }
        PENDING.remove(id);
        try {
            p.onConfirm.accept(trimmed);
        } catch (Throwable t) {
            PokeBuilder.LOGGER.error("[PokeBuilder] ChatInput callback failed", t);
        }
        return true;
    }

    public static void cancel(UUID id) {
        Pending p = PENDING.remove(id);
        if (p != null && p.onCancel != null) {
            try { p.onCancel.run(); } catch (Throwable ignored) {}
        }
    }

    // Cancel only if the pending entry matches a given category. Lets
    // a GUI's onClose clear stale prompts it opened without stepping
    // on an unrelated admin request that happens to be in flight.
    public static void cancelIfPending(UUID id, String category) {
        Pending p = PENDING.get(id);
        if (p == null) return;
        if (Objects.equals(p.category, category)) {
            PENDING.remove(id);
            if (p.onCancel != null) {
                try { p.onCancel.run(); } catch (Throwable ignored) {}
            }
        }
    }

    public static void pruneExpired() {
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(e -> now > e.getValue().expiresAt);
    }
}
