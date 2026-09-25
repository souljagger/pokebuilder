package com.antigravity.pokebuilder.logging;

import com.antigravity.pokebuilder.PokeBuilder;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Tail reader over the audit log that yields the last N matching
// transactions without loading the whole file. Used by /pb history and
// /pokebuilder history <player>.
//
// Implementation reads the file backwards a chunk at a time, splitting
// on newline and filtering for [TRANSACTION] lines, stopping once the
// caller has collected `limit` matches. A 100 k-line log is perfectly
// happy being scanned this way — we never read more than needed.
public final class AuditLogReader {

    // Matches the canonical transaction line written by AuditLogger.
    // Groups: 1 timestamp, 2 txId, 3 playerId, 4 playerName, 5 species,
    // 6 pokemonId, 7 action, 8 detail, 9 cost, 10 result
    private static final Pattern TX_LINE = Pattern.compile(
            "^\\[(.*?)\\] \\[TRANSACTION\\] TxId=([a-z0-9]+) "
                    + "Player=([0-9a-f-]+)\\(([^)]+)\\) "
                    + "Pokemon=([^(]+)\\(([0-9a-f-]+)\\) "
                    + "Action=(\\S+) Detail=(.*?) "
                    + "Cost=(\\S+) Balance_Before=\\S+ Balance_After=\\S+ Result=(\\S+)$");

    public record Entry(String timestamp, String txId, UUID playerId, String playerName,
                        String species, UUID pokemonId, String action, String detail,
                        String cost, String result) {}

    private AuditLogReader() {}

    // Last `limit` transactions matching the filter, newest first.
    // `filterPlayer` null returns across all players.
    public static List<Entry> tail(Path logFile, UUID filterPlayer, int limit) {
        List<Entry> out = new ArrayList<>();
        if (limit <= 0 || logFile == null || !Files.exists(logFile)) return out;

        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            long length = raf.length();
            if (length == 0) return out;

            // Chunked reverse read. Bigger chunks amortize seeks; smaller
            // ones bound memory. 64 KiB is fine for the typical audit log.
            final int chunkSize = 65_536;
            long position = length;
            StringBuilder overflow = new StringBuilder();

            while (position > 0 && out.size() < limit) {
                int read = (int) Math.min(chunkSize, position);
                position -= read;
                byte[] buf = new byte[read];
                raf.seek(position);
                raf.readFully(buf);

                // Prepend this chunk to anything we already held from the
                // previous (more recent) iteration, since a line may span
                // the chunk boundary.
                String chunk = new String(buf, StandardCharsets.UTF_8) + overflow;

                // Split on LF. The first fragment may be a partial line
                // whose prefix is in the previous (older) chunk — hold it
                // back as overflow for the next iteration.
                int newlineIdx = chunk.indexOf('\n');
                String remainder = (position == 0) ? "" : (newlineIdx < 0 ? chunk : chunk.substring(0, newlineIdx));
                String scanRegion = (newlineIdx < 0 && position != 0) ? "" : chunk.substring(newlineIdx + 1);
                if (position == 0) { // we're at the start — include everything
                    scanRegion = chunk;
                    remainder = "";
                }

                String[] lines = scanRegion.split("\n", -1);
                for (int i = lines.length - 1; i >= 0 && out.size() < limit; i--) {
                    String line = lines[i].stripTrailing();
                    if (line.isEmpty()) continue;
                    Entry e = parse(line);
                    if (e == null) continue;
                    if (filterPlayer != null && !filterPlayer.equals(e.playerId)) continue;
                    out.add(e);
                }
                overflow = new StringBuilder(remainder);
            }
        } catch (IOException e) {
            PokeBuilder.LOGGER.error("[PokeBuilder] Failed to read audit log for tail", e);
            return Collections.emptyList();
        }
        return out;
    }

    private static Entry parse(String line) {
        try {
            Matcher m = TX_LINE.matcher(line);
            if (!m.matches()) return null;
            return new Entry(
                    m.group(1),
                    m.group(2),
                    UUID.fromString(m.group(3)),
                    m.group(4),
                    m.group(5).trim(),
                    UUID.fromString(m.group(6)),
                    m.group(7),
                    m.group(8),
                    m.group(9),
                    m.group(10));
        } catch (Throwable t) {
            return null;
        }
    }
}
