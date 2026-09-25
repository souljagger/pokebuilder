package com.antigravity.pokebuilder.messages;

import com.antigravity.pokebuilder.PokeBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

// Loads the translation base layer for a given locale.
// Resolution order (first hit wins):
//   1. config/pokebuilder/lang/<locale>.json  (admin-supplied)
//   2. assets/pokebuilder/lang/<locale>.json  (bundled in the jar)
//   3. assets/pokebuilder/lang/en_us.json     (canonical fallback)
//
// JSON format is a flat object of string → string. Nested keys use dots
// in the string key itself ("command.not_player"), not JSON nesting.
// Hand-rolled parser avoids pulling Gson as a runtime dep — the format
// is simple enough that a 60-line state machine handles it.
public final class LanguageLoader {

    private static final String DEFAULT_LOCALE = "en_us";
    private static final String RESOURCE_PREFIX = "/assets/pokebuilder/lang/";

    private LanguageLoader() {}

    public static Map<String, String> load(String configuredLocale) {
        String locale = resolveLocale(configuredLocale);

        // Try config dir first (admin supplied override for the whole locale).
        Map<String, String> fromConfig = loadFromConfigDir(locale);
        if (fromConfig != null && !fromConfig.isEmpty()) {
            PokeBuilder.LOGGER.info("[PokeBuilder] Loaded locale '{}' from config/pokebuilder/lang/", locale);
            return withEnglishFallback(fromConfig, locale);
        }

        // Try bundled resource for the requested locale.
        Map<String, String> fromJar = loadFromJar(locale);
        if (fromJar != null && !fromJar.isEmpty()) {
            PokeBuilder.LOGGER.info("[PokeBuilder] Loaded locale '{}' from bundled resources", locale);
            return withEnglishFallback(fromJar, locale);
        }

        // Requested locale missing everywhere. Fall back to bundled en_us.
        Map<String, String> english = loadFromJar(DEFAULT_LOCALE);
        if (english == null) {
            PokeBuilder.LOGGER.error("[PokeBuilder] Bundled en_us.json could not be read — all strings will fall back to key names.");
            return new LinkedHashMap<>();
        }
        if (!DEFAULT_LOCALE.equals(locale)) {
            PokeBuilder.LOGGER.warn("[PokeBuilder] Locale '{}' not found. Using bundled en_us.", locale);
        }
        return english;
    }

    private static String resolveLocale(String configured) {
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("auto")) {
            Locale d = Locale.getDefault();
            String tag = (d.getLanguage() + "_" + d.getCountry()).toLowerCase(Locale.ROOT);
            return tag.isBlank() || tag.equals("_") ? DEFAULT_LOCALE : tag;
        }
        return configured.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    // Merge requested locale with English as backstop so missing keys in a
    // partial community translation fall through gracefully.
    private static Map<String, String> withEnglishFallback(Map<String, String> primary, String locale) {
        if (DEFAULT_LOCALE.equals(locale)) return primary;
        Map<String, String> fallback = loadFromJar(DEFAULT_LOCALE);
        if (fallback == null || fallback.isEmpty()) return primary;
        Map<String, String> merged = new LinkedHashMap<>(fallback);
        merged.putAll(primary);
        return merged;
    }

    private static Map<String, String> loadFromConfigDir(String locale) {
        try {
            Path path = FabricLoader.getInstance().getConfigDir()
                    .resolve("pokebuilder").resolve("lang").resolve(locale + ".json");
            if (!Files.exists(path)) return null;
            try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return parseJson(readAll(r));
            }
        } catch (IOException e) {
            PokeBuilder.LOGGER.warn("[PokeBuilder] Failed to read config-dir locale {}: {}", locale, e.toString());
            return null;
        }
    }

    private static Map<String, String> loadFromJar(String locale) {
        String resourcePath = RESOURCE_PREFIX + locale + ".json";
        try (InputStream in = LanguageLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return parseJson(readAll(r));
            }
        } catch (IOException e) {
            PokeBuilder.LOGGER.warn("[PokeBuilder] Failed to read bundled locale {}: {}", locale, e.toString());
            return null;
        }
    }

    private static String readAll(BufferedReader r) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
        return sb.toString();
    }

    // Minimal JSON parser: flat object of string to string, with the
    // standard JSON string escape set (quote, backslash, n, t, r, b, f,
    // plus 4-hex-digit unicode). Throws on any structure more complex
    // than what we ship. Lines / indentation irrelevant.
    static Map<String, String> parseJson(String src) {
        Map<String, String> out = new LinkedHashMap<>();
        int i = skipWs(src, 0);
        if (i >= src.length() || src.charAt(i) != '{') {
            throw new IllegalArgumentException("Expected '{' at position " + i);
        }
        i++;
        i = skipWs(src, i);
        if (i < src.length() && src.charAt(i) == '}') return out;

        while (i < src.length()) {
            i = skipWs(src, i);
            if (src.charAt(i) != '"') throw new IllegalArgumentException("Expected key string at position " + i);
            StringBuilder key = new StringBuilder();
            i = readString(src, i, key);

            i = skipWs(src, i);
            if (src.charAt(i) != ':') throw new IllegalArgumentException("Expected ':' at position " + i);
            i++;

            i = skipWs(src, i);
            if (src.charAt(i) != '"') throw new IllegalArgumentException("Expected value string at position " + i);
            StringBuilder value = new StringBuilder();
            i = readString(src, i, value);

            out.put(key.toString(), value.toString());

            i = skipWs(src, i);
            if (i >= src.length()) break;
            char c = src.charAt(i);
            if (c == ',') { i++; continue; }
            if (c == '}') break;
            throw new IllegalArgumentException("Expected ',' or '}' at position " + i);
        }
        return out;
    }

    private static int skipWs(String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { i++; continue; }
            break;
        }
        return i;
    }

    // Parses "…" starting at i (on the opening quote) and writes the
    // unescaped content into `out`. Returns index past the closing quote.
    private static int readString(String s, int i, StringBuilder out) {
        if (s.charAt(i) != '"') throw new IllegalArgumentException("Expected '\"' at " + i);
        i++;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"') return i + 1;
            if (c == '\\') {
                if (i + 1 >= s.length()) throw new IllegalArgumentException("Dangling escape");
                char esc = s.charAt(i + 1);
                switch (esc) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (i + 5 >= s.length()) throw new IllegalArgumentException("Short \\u escape");
                        out.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                        i += 4;
                    }
                    default -> throw new IllegalArgumentException("Unknown escape \\" + esc);
                }
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        throw new IllegalArgumentException("Unterminated string");
    }
}
