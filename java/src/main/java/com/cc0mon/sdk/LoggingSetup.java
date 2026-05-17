package com.cc0mon.sdk;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
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
 */
public final class LoggingSetup {

    private LoggingSetup() {}

    /** Returns a logger writing to {@code ./cc0mon-api-<action>.log}. Safe to call multiple times. */
    public static Logger configure(String action) {
        Level level = parseLevel(System.getenv("CC0MON_LOG_LEVEL"));

        Path logPath = Paths.get(System.getProperty("user.dir"), "cc0mon-api-" + action + ".log");

        Logger logger = Logger.getLogger("com.cc0mon.sdk." + action);
        logger.setLevel(level);
        logger.setUseParentHandlers(false);

        boolean alreadyAttached = false;
        for (Handler h : logger.getHandlers()) {
            if (h instanceof FileHandler && action.equals(h.getFilter() instanceof ActionFilter f ? f.action : null)) {
                alreadyAttached = true;
                break;
            }
        }
        if (!alreadyAttached) {
            try {
                FileHandler handler = new FileHandler(logPath.toString(), true);
                handler.setLevel(level);
                handler.setFormatter(new LineFormatter(action));
                handler.setFilter(new ActionFilter(action));
                logger.addHandler(handler);
            } catch (IOException e) {
                throw new RuntimeException("failed to open log file " + logPath, e);
            }
        }

        // The SDK uses its own logger "com.cc0mon.sdk" — wire that to the same file too.
        Logger sdkLogger = Logger.getLogger("com.cc0mon.sdk");
        sdkLogger.setLevel(level);
        sdkLogger.setUseParentHandlers(false);
        boolean sdkAttached = false;
        for (Handler h : sdkLogger.getHandlers()) {
            if (h instanceof FileHandler && action.equals(h.getFilter() instanceof ActionFilter f ? f.action : null)) {
                sdkAttached = true;
                break;
            }
        }
        if (!sdkAttached) {
            try {
                FileHandler sdkHandler = new FileHandler(logPath.toString(), true);
                sdkHandler.setLevel(level);
                sdkHandler.setFormatter(new LineFormatter(action));
                sdkHandler.setFilter(new ActionFilter(action));
                sdkLogger.addHandler(sdkHandler);
            } catch (IOException e) {
                throw new RuntimeException("failed to open SDK log file " + logPath, e);
            }
        }

        return logger;
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

    private static final class ActionFilter implements java.util.logging.Filter {
        final String action;
        ActionFilter(String action) { this.action = action; }
        @Override public boolean isLoggable(LogRecord record) { return true; }
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
