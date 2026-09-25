package com.antigravity.pokebuilder.logging;

import com.antigravity.pokebuilder.PokeBuilder;
import com.antigravity.pokebuilder.config.PokeBuilderConfig;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class AuditLogger {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static Path logFile;
    private static boolean toFile;
    private static boolean toConsole;

    private AuditLogger() {}

    public static void init(PokeBuilderConfig cfg) {
        toFile = cfg.logging.logToFile;
        toConsole = cfg.logging.logToConsole;
        if (toFile) {
            logFile = Path.of(cfg.logging.logFile);
            try {
                if (logFile.getParent() != null) Files.createDirectories(logFile.getParent());
                if (!Files.exists(logFile)) Files.createFile(logFile);
            } catch (IOException e) {
                PokeBuilder.LOGGER.error("[PokeBuilder] Failed to init audit log file", e);
                toFile = false;
            }
        }
    }

    public static void shutdown() {}

    private static final char[] BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    // 8-char base36 transaction id. ~2.8 * 10^12 possibilities — unique
    // enough within a reasonable audit log window, short enough to
    // display in chat and remember long enough to paste into a support
    // ticket.
    public static String newTxId() {
        char[] c = new char[8];
        for (int i = 0; i < 8; i++) c[i] = BASE36[RNG.nextInt(36)];
        return new String(c);
    }

    public static String transaction(UUID playerId, String playerName, UUID pokemonId, String species,
                                     String action, String detail, BigDecimal cost,
                                     BigDecimal before, BigDecimal after, boolean success) {
        String txId = newTxId();
        String line = String.format(
                "[%s] [TRANSACTION] TxId=%s Player=%s(%s) Pokemon=%s(%s) Action=%s Detail=%s Cost=%s Balance_Before=%s Balance_After=%s Result=%s",
                LocalDateTime.now().format(TS), txId, playerId, playerName, species, pokemonId,
                action, detail, cost.toPlainString(), before.toPlainString(),
                after.toPlainString(), success ? "SUCCESS" : "FAILURE");
        write(line, success);
        return txId;
    }

    public static void warn(String msg) {
        String line = String.format("[%s] [WARN] %s", LocalDateTime.now().format(TS), msg);
        write(line, false);
    }

    public static void error(String msg) {
        String line = String.format("[%s] [ERROR] %s", LocalDateTime.now().format(TS), msg);
        write(line, false);
    }

    private static synchronized void write(String line, boolean info) {
        if (toConsole) {
            if (info) PokeBuilder.LOGGER.info(line);
            else PokeBuilder.LOGGER.warn(line);
        }
        if (toFile && logFile != null) {
            try (BufferedWriter w = Files.newBufferedWriter(logFile,
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
                w.write(line);
                w.newLine();
            } catch (IOException e) {
                PokeBuilder.LOGGER.error("[PokeBuilder] Failed to write audit log", e);
            }
        }
    }
}
