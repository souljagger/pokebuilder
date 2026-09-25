package com.antigravity.pokebuilder.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Holds every registered Purchasable. Registration happens during
// SERVER_STARTED (so Cobblemon and the economy are up). After
// `freeze()` runs the registry rejects further registrations to prevent
// mid-session mutation by other mods.
//
// Intentionally process-wide (static). PokeBuilder is a server-side
// mod; there is exactly one registry per JVM.
public final class PurchasableRegistry {

    private static final Map<String, Purchasable> ENTRIES = new LinkedHashMap<>();
    private static volatile boolean frozen = false;

    private PurchasableRegistry() {}

    public static synchronized void register(Purchasable p) {
        if (frozen) {
            throw new IllegalStateException("PurchasableRegistry is frozen; register during SERVER_STARTED");
        }
        if (p == null || p.id() == null || p.id().isBlank()) {
            throw new IllegalArgumentException("Purchasable id must be non-blank");
        }
        if (ENTRIES.containsKey(p.id())) {
            throw new IllegalStateException("Duplicate Purchasable id: " + p.id());
        }
        ENTRIES.put(p.id(), p);
    }

    public static void freeze() {
        frozen = true;
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static Optional<Purchasable> byId(String id) {
        return Optional.ofNullable(ENTRIES.get(id));
    }

    // Ordered list sorted by editorSlot — matches the left-to-right,
    // top-to-bottom order EditorGui uses for rendering.
    public static List<Purchasable> ordered() {
        List<Purchasable> out = new ArrayList<>(ENTRIES.values());
        out.sort(Comparator.comparingInt(Purchasable::editorSlot));
        return Collections.unmodifiableList(out);
    }

    public static int size() {
        return ENTRIES.size();
    }

    // Testing / reload-only helper. Not exposed to the mod's public
    // surface.
    static synchronized void clear() {
        ENTRIES.clear();
        frozen = false;
    }
}
