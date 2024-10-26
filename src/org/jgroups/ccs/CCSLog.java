package org.jgroups.ccs;

import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.stack.Protocol;
import org.jgroups.util.NameCache;

/**
 * Logger that adds CCS-specific functionality to JGroups logger.
 *
 * @author onoprien
 */
public class CCSLog implements Log {

    private final Log log;
    private final Protocol protocol;
    private volatile String bus;

    public CCSLog(Protocol protocol) {
        this.protocol = protocol;
        log = LogFactory.getLog(protocol.getClass());
    }

    @Override
    public boolean isFatalEnabled() {
        return log.isFatalEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return log.isErrorEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return log.isWarnEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return log.isInfoEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    @Override
    public boolean isTraceEnabled() {
        return log.isTraceEnabled();
    }

    @Override
    public void fatal(String msg) {
        log.fatal("[" + getBusName() + "] " + msg);
    }

    @Override
    public void fatal(String format, Object... args) {
        if (isFatalEnabled()) {
            Object[] aa = new Object[args.length + 1];
            System.arraycopy(args, 0, aa, 1, args.length);
            aa[0] = getBusName();
            log.fatal("[%s] " + format, aa);
        }
    }

    @Override
    public void fatal(String msg, Throwable throwable) {
        log.fatal("[" + getBusName() + "] " + msg, throwable);
    }

    @Override
    public void error(String msg) {
        log.error("[" + getBusName() + "] " + msg);
    }

    @Override
    public void error(String format, Object... args) {
        if (isErrorEnabled()) {
            Object[] aa = new Object[args.length + 1];
            System.arraycopy(args, 0, aa, 1, args.length);
            aa[0] = getBusName();
            log.error("[%s] " + format, aa);
        }
    }

    @Override
    public void error(String msg, Throwable throwable) {
        log.error("[" + getBusName() + "] " + msg, throwable);
    }

    @Override
    public void warn(String msg) {
        log.warn("[" + getBusName() + "] " + msg);
    }

    @Override
    public void warn(String format, Object... args) {
        if (isWarnEnabled()) {
            Object[] aa = new Object[args.length + 1];
            System.arraycopy(args, 0, aa, 1, args.length);
            aa[0] = getBusName();
            log.warn("[%s] " + format, aa);
        }
    }

    @Override
    public void warn(String msg, Throwable throwable) {
        log.warn("[" + getBusName() + "] " + msg, throwable);
    }

    @Override
    public void info(String msg) {
        log.info("[" + getBusName() + "] " + msg);
    }

    @Override
    public void info(String format, Object... args) {
        if (isInfoEnabled()) {
            Object[] aa = new Object[args.length + 1];
            System.arraycopy(args, 0, aa, 1, args.length);
            aa[0] = getBusName();
            log.info("[%s] " + format, aa);
        }
    }

    @Override
    public void debug(String msg) {
        log.debug("[" + getBusName() + "] " + msg);
    }

    @Override
    public void debug(String format, Object... args) {
        if (isDebugEnabled()) {
            Object[] aa = new Object[args.length + 1];
            System.arraycopy(args, 0, aa, 1, args.length);
            aa[0] = getBusName();
            log.debug("[%s] " + format, aa);
        }
    }

    @Override
    public void debug(String msg, Throwable throwable) {
        log.debug("[" + getBusName() + "] " + msg, throwable);
    }

    @Override
    public void trace(Object obj) {
        trace(obj == null ? "null" : obj.toString());
    }

    @Override
    public void trace(String msg) {
        log.trace("[" + getBusName() + "] " + msg);
    }

    @Override
    public void trace(String format, Object... args) {
        if (isTraceEnabled()) {
            Object[] aa = new Object[args.length + 1];
            System.arraycopy(args, 0, aa, 1, args.length);
            aa[0] = getBusName();
            log.trace("[%s] " + format, aa);
        }
    }

    @Override
    public void trace(String msg, Throwable throwable) {
        log.trace("[" + getBusName() + "] " + msg, throwable);
    }

    @Override
    public void setLevel(String level) {
        log.setLevel(level);
    }

    @Override
    public String getLevel() {
        return log.getLevel();
    }
    
    private String getBusName() {
        if (bus == null) {
            try {
                String s = protocol.getProtocolStack().getChannel().clusterName();
                if (s != null) bus = s;
            } catch (RuntimeException x) {
            }
            if (bus == null) {
                String channelName = null;
                try {
                    channelName = protocol.getProtocolStack().getChannel().getName();
                } catch (RuntimeException x) {
                }
                if (channelName == null) {
                    try {
                        channelName = NameCache.get(protocol.getAddress());
                    } catch (RuntimeException x) {
                    }
                }
                try {
                    if (channelName.charAt(1) == '-') {
                        switch (channelName.charAt(0)) {
                            case 's':
                                bus = "STATUS"; break;
                            case 'c':
                                bus = "COMMAND"; break;
                            case 'l':
                                bus = "LOG"; break;
                        }
                    }
                } catch (RuntimeException x) {
                }
            }
            if (bus == null) return "CCS";
        }
        return bus;
    }

}
