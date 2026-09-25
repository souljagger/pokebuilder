package com.antigravity.pokebuilder.messages;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// Two-layer message registry:
//   base       — locale JSON loaded at startup / reload; immutable at runtime
//   overrides  — HOCON messages{} block + admin GUI edits; only contains
//                entries that differ from base, so the config file stays
//                small instead of re-serializing all ~60 strings
//
// Lookup order: overrides > base > "&f" + key fallback. resetToDefault()
// clears an override so the key falls back through to the base layer.
public final class PokeBuilderMessages {

    private final Map<String, String> base;
    private final Map<String, String> overrides = new LinkedHashMap<>();

    // Convenience constructor for tests / fallback paths that want the
    // bundled en_us base without the HOCON override layer.
    public PokeBuilderMessages() {
        this(LanguageLoader.load("en_us"));
    }

    public PokeBuilderMessages(Map<String, String> base) {
        this.base = base == null ? new LinkedHashMap<>() : new LinkedHashMap<>(base);
    }

    public String raw(String key) {
        String o = overrides.get(key);
        if (o != null) return o;
        String b = base.get(key);
        if (b != null) return b;
        return "&f" + key;
    }

    public Text get(String key, Object... args) {
        return colorize(format(raw(key), args));
    }

    public String getPlain(String key, Object... args) {
        return stripColor(format(raw(key), args));
    }

    // Store a string. If it matches the base value, it's treated as
    // "reset to default" (no override persisted). This keeps the config
    // file free of redundant entries after an admin pastes back the
    // default.
    public void put(String key, String value) {
        if (value == null) {
            overrides.remove(key);
            return;
        }
        String baseValue = base.get(key);
        if (baseValue != null && baseValue.equals(value)) {
            overrides.remove(key);
        } else {
            overrides.put(key, value);
        }
    }

    // Explicit reset — the admin GUI's right-click "reset to default" path.
    public void resetToDefault(String key) {
        overrides.remove(key);
    }

    public boolean isOverridden(String key) {
        return overrides.containsKey(key);
    }

    // All known keys (base + overrides, de-duplicated, insertion order).
    public Set<String> keys() {
        Set<String> all = new LinkedHashSet<>(base.keySet());
        all.addAll(overrides.keySet());
        return all;
    }

    // Merged view for callers that want the effective string map
    // (base first, overrides take precedence). Used by any code iterating
    // all known strings — e.g. the MessagesAdminGui paginated list.
    public Map<String, String> all() {
        Map<String, String> merged = new LinkedHashMap<>(base);
        merged.putAll(overrides);
        return merged;
    }

    // Override-only view, used by ConfigWriter so the HOCON messages{}
    // block contains just the admin's deltas, not every single string.
    public Map<String, String> overrides() {
        return new LinkedHashMap<>(overrides);
    }

    public int size() {
        return keys().size();
    }

    private static String format(String raw, Object[] args) {
        if (args == null || args.length == 0) return raw;
        String s = raw;
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }

    public static MutableText colorize(String raw) {
        MutableText out = Text.empty();
        StringBuilder buf = new StringBuilder();
        net.minecraft.util.Formatting currentColor = null;
        boolean bold = false, italic = false, underline = false, strike = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '&' || c == '\u00A7') && i + 1 < raw.length()) {
                if (buf.length() > 0) {
                    out.append(styled(buf.toString(), currentColor, bold, italic, underline, strike));
                    buf.setLength(0);
                }
                char code = Character.toLowerCase(raw.charAt(i + 1));
                net.minecraft.util.Formatting fmt = net.minecraft.util.Formatting.byCode(code);
                if (fmt != null) {
                    if (fmt.isColor()) {
                        currentColor = fmt;
                        bold = italic = underline = strike = false;
                    } else {
                        switch (fmt) {
                            case BOLD -> bold = true;
                            case ITALIC -> italic = true;
                            case UNDERLINE -> underline = true;
                            case STRIKETHROUGH -> strike = true;
                            case RESET -> { currentColor = null; bold = italic = underline = strike = false; }
                            default -> {}
                        }
                    }
                }
                i++;
                continue;
            }
            buf.append(c);
        }
        if (buf.length() > 0) {
            out.append(styled(buf.toString(), currentColor, bold, italic, underline, strike));
        }
        return out;
    }

    private static Text styled(String text, net.minecraft.util.Formatting color,
                                boolean bold, boolean italic, boolean underline, boolean strike) {
        MutableText t = Text.literal(text);
        if (color != null) t = t.formatted(color);
        if (bold) t = t.formatted(net.minecraft.util.Formatting.BOLD);
        if (italic) t = t.formatted(net.minecraft.util.Formatting.ITALIC);
        if (underline) t = t.formatted(net.minecraft.util.Formatting.UNDERLINE);
        if (strike) t = t.formatted(net.minecraft.util.Formatting.STRIKETHROUGH);
        return t;
    }

    private static String stripColor(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '&' || c == '\u00A7') && i + 1 < raw.length()) { i++; continue; }
            sb.append(c);
        }
        return sb.toString();
    }
}
