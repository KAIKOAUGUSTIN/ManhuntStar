package dev.manhuntstar.mod;

import java.text.MessageFormat;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core logs with {@code java.util.logging} (Bukkit's plugin logger); the mods send those lines to the
 * game's own log, so they show up in the console and in latest.log like the game's, with the name in
 * front like a plugin's.
 */
public final class ModLogging {

    private ModLogging() {
    }

    /**
     * A logger named {@code name} whose lines go to the game's log; {@code prefix} puts the name in front of
     * each line, for a console that doesn't show logger names (Fabric's).
     */
    public static Logger logger(String name, boolean prefix) {
        Logger logger = Logger.getLogger("dev.manhuntstar.mod." + name);
        logger.setUseParentHandlers(false);
        for (Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
        }
        logger.addHandler(new ToGameLog(LoggerFactory.getLogger(name), prefix ? "[" + name + "] " : ""));
        logger.setLevel(Level.ALL);
        return logger;
    }

    private static final class ToGameLog extends Handler {
        private final org.slf4j.Logger out;
        private final String prefix;

        ToGameLog(org.slf4j.Logger out, String prefix) {
            this.out = out;
            this.prefix = prefix;
        }

        @Override
        public void publish(LogRecord record) {
            String text = record.getMessage();
            Object[] params = record.getParameters();
            if (text != null && params != null && params.length > 0) {
                try {
                    text = MessageFormat.format(text, params);
                } catch (IllegalArgumentException ignored) {
                    // not a pattern: the text as it is
                }
            }
            // the plugin's name in front, as Bukkit's plugin logger does
            text = prefix + text;
            int level = record.getLevel().intValue();
            Throwable thrown = record.getThrown();
            if (level >= Level.SEVERE.intValue()) {
                out.error(text, thrown);
            } else if (level >= Level.WARNING.intValue()) {
                out.warn(text, thrown);
            } else if (level >= Level.INFO.intValue()) {
                out.info(text, thrown);
            } else {
                out.debug(text, thrown);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
