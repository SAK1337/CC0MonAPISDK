package com.cc0mon.sdk;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Configures a per-action logger that appends to
 * {@code ./cc0mon-api-<action>.log} in the current working directory.
 *
 * <p>Within a single JVM, one {@link FileHandler} is shared per log path
 * (keyed by absolute path) so repeat calls with the same action do not
 * stack duplicate handlers. The SDK's shared logger ({@code com.cc0mon.sdk})
 * also routes through the same handler for the current action's file.
 */
public final class LoggingSetup {

    private LoggingSetup() {}

    /** Shared file handlers keyed by absolute log path so we don't stack duplicates. */
    private static final Map<String, FileHandler> HANDLERS = new HashMap<>();

    /** Returns a logger writing to {@code ./cc0mon-api-<action>.log}. Safe to call multiple times. */
    public static Logger configure(String action) {
        Level level = parseLevel(System.getenv("CC0MON_LOG_LEVEL"));

        Path logPath = Paths.get(System.getProperty("user.dir"), "cc0mon-api-" + action + ".log");
        String pathKey = logPath.toAbsolutePath().toString();

        FileHandler handler = getOrCreateHandler(pathKey, action, level);

        Logger logger = Logger.getLogger("com.cc0mon.sdk." + action);
        logger.setLevel(level);
        logger.setUseParentHandlers(false);
        attachOnce(logger, handler, pathKey);

        // SDK-internal logs route to the same file while this action is active.
        Logger sdkLogger = Logger.getLogger("com.cc0mon.sdk");
        sdkLogger.setLevel(level);
        sdkLogger.setUseParentHandlers(false);
        attachOnce(sdkLogger, handler, pathKey);

        return logger;
    }

    private static synchronized FileHandler getOrCreateHandler(String pathKey, String action, Level level) {
        FileHandler handler = HANDLERS.get(pathKey);
        if (handler == null) {
            try {
                handler = new FileHandler(pathKey, true);
            } catch (IOException e) {
                throw new RuntimeException("failed to open log file " + pathKey, e);
            }
            handler.setLevel(level);
            handler.setFormatter(new LineFormatter(action));
            HANDLERS.put(pathKey, handler);
        }
        return handler;
    }

    private static void attachOnce(Logger logger, Handler handler, String pathKey) {
        for (Handler existing : logger.getHandlers()) {
            if (existing == handler) return;
        }
        logger.addHandler(handler);
    }

    private static Level parseLevel(String value) {
        if (value == null) return Level.INFO;
        return switch (value.toUpperCase()) {
            case "DEBUG" -> Level.FINE;
            case "INFO" -> Level.INFO;
            case "WARNING", "WARN" -> Level.WARNING;
            case "ERROR" -> Level.SEVERE;
            default -> Level.INFO;
        };
    }

    private static final class LineFormatter extends Formatter {
        private final String action;
        LineFormatter(String action) { this.action = action; }

        @Override
        public String format(LogRecord record) {
            String levelName = switch (record.getLevel().getName()) {
                case "FINE", "FINER", "FINEST" -> "DEBUG";
                case "SEVERE" -> "ERROR";
                default -> record.getLevel().getName();
            };
            String ts = Instant.ofEpochMilli(record.getMillis()).toString();
            String thrown = Optional.ofNullable(record.getThrown())
                .map(Throwable::toString).map(s -> " | exc=" + s).orElse("");
            return ts + " | " + levelName + " | " + action + " | " + formatMessage(record) + thrown + System.lineSeparator();
        }
    }
}
