package org.jgroups.logging;

import java.util.logging.Level;

/**
 * Simple logging wrapper for log4j or JDK logging. Code originally copied from Infinispan
 *
 * @author Manik Surtani
 * @author Bela Ban
 * @since 2.8
 */
public interface Log {
    boolean isFatalEnabled();
    boolean isErrorEnabled();
    boolean isWarnEnabled();
    boolean isInfoEnabled();
    boolean isDebugEnabled();
    boolean isTraceEnabled();



    void fatal(String msg);
    void fatal(String format, Object ... args);
    void fatal(String msg, Throwable throwable);

    void error(String msg);
    void error(String format, Object ... args);
    void error(String msg, Throwable throwable);

    void warn(String msg);
    void warn(String format, Object ... args);
    void warn(String msg, Throwable throwable);

    void info(String msg);
    void info(String format, Object ... args);

    void debug(String msg);
    void debug(String format, Object ... args);
    void debug(String msg, Throwable throwable);

    void trace(Object obj);
    void trace(String msg);
    void trace(String format, Object ... args);
    void trace(String msg, Throwable throwable);
    
    // CCS begin
    default boolean isEnabled(Level level) {
        return switch (level.getName()) {
            case "OFF" -> isFatalEnabled();
            case "SEVERE" -> isErrorEnabled();
            case "WARNING" -> isWarnEnabled();
            case "INFO" -> isInfoEnabled();
            case "CONFIG", "FINE" -> isDebugEnabled();
            case "FINER", "FINEST" -> isTraceEnabled();
            case "ALL" -> false;
            default -> throw new IllegalArgumentException("Unknown level "+ level);
        };
    }
    
    default void out(Level level, String msg) {
        switch (level.getName()) {
            case "OFF" -> fatal(msg);
            case "SEVERE" -> error(msg);
            case "WARNING" -> warn(msg);
            case "INFO" -> info(msg);
            case "CONFIG", "FINE" -> debug(msg);
            case "FINER", "FINEST" -> trace(msg);
            case "ALL" -> {}
            default -> throw new IllegalArgumentException("Unknown level "+ level);
        }
    }
    
    default void out(Level level, String format, Object ... args) {
        switch (level.getName()) {
            case "OFF" -> fatal(format, args);
            case "SEVERE" -> error(format, args);
            case "WARNING" -> warn(format, args);
            case "INFO" -> info(format, args);
            case "CONFIG", "FINE" -> debug(format, args);
            case "FINER", "FINEST" -> trace(format, args);
            case "ALL" -> {}
            default -> throw new IllegalArgumentException("Unknown level "+ level);
        }
    }
    
    default void out(Level level, String msg, Throwable throwable) {
        switch (level.getName()) {
            case "OFF" -> fatal(msg, throwable);
            case "SEVERE" -> error(msg, throwable);
            case "WARNING" -> warn(msg, throwable);
            case "INFO" -> info(msg +". "+ throwable);
            case "CONFIG", "FINE" -> debug(msg, throwable);
            case "FINER", "FINEST" -> trace(msg, throwable);
            case "ALL" -> {}
            default -> throw new IllegalArgumentException("Unknown level "+ level);
        }
    }
    // CCS end



    // Advanced methods

    /**
     * Sets the level of a logger. This method is used to dynamically change the logging level of a running system,
     * e.g. via JMX. The appender of a level needs to exist.
     * @param level The new level. Valid values are "fatal", "error", "warn", "info", "debug", "trace"
     * (capitalization not relevant)
     */
    void setLevel(String level);

    String getLevel();
}
