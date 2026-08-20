package org.jgroups.ccs;

import java.util.*;
import java.util.logging.Level;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.PhysicalAddress;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.protocols.pbcast.NakAckHeader2;
import org.jgroups.stack.Protocol;
import org.jgroups.util.NameCache;
import org.jgroups.util.SeqnoList;
import org.jgroups.util.UUID;

/**
 * Logger that adds CCS-specific functionality to JGroups logger.
 *
 * @author onoprien
 */
public class CCSLog implements Log {

    // CCS begin
    private final Log log;
    private final Protocol protocol;
    private final JChannel channel;
    private volatile String bus;

    public CCSLog(Class<?> clazz, String bus) {
        log = LogFactory.getLog(clazz);
        protocol = null;
        channel = null;
        this.bus = bus;
    }

    public CCSLog(Protocol protocol) {
        log = LogFactory.getLog(protocol.getClass());
        this.protocol = protocol;
        channel = null;
    }

    public CCSLog(JChannel channel) {
        log = LogFactory.getLog(channel.getClass());
        protocol = null;
        this.channel = channel;
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
    
    public String getBusName() {
        if (bus == null) {
            try {
                bus = protocol.getProtocolStack().getChannel().clusterName();
            } catch (RuntimeException x) {
            }
            if (bus == null) {
                String channelName = null;
                try {
                    channelName = protocol.getProtocolStack().getChannel().getName();
                } catch (RuntimeException x) {
                }
                if (channelName == null && channel != null) {
                    channelName = channel.getName();
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
    
    
// -- Static utilities : -------------------------------------------------------
    
    /**
     * Get brief string representation of current stack trace.
     * 
     * @param depth Max number of elements. If negative, all withing current class. If 0, all.
     * @return Stack trace string.
     */
    static public String getStackTrace(int depth) {
        StringBuilder sb = new StringBuilder("Stack: ");
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        int n = trace.length - 2;
        if (n < 1) return "";
        ArrayList<String> mm = new ArrayList<>(n);        
        if (depth >= 0) {
            depth = (depth == 0 || depth > n) ? trace.length : depth + 2;
            for (int i = 2; i < depth; i++) {
                String clazz = trace[i].getClassName();
                int k = clazz.lastIndexOf(".");
                if (k >= 0) clazz = clazz.substring(k + 1);
                mm.add(clazz + "." + trace[i].getMethodName());
            }
        } else {
            String clazz = trace[2].getClassName();
            int k = clazz.indexOf("$");
            if (k >= 0) clazz = clazz.substring(0,k);
            k = clazz.lastIndexOf(".");
            if (k >= 0) clazz = clazz.substring(k + 1);
            for (int i = 2; i < trace.length; i++) {
                if (trace[i].getClassName().contains(clazz)) {
                    mm.add(clazz + "." + trace[i].getMethodName());
                }
            }
        }
        return sb.append(String.join("<-", mm)).append(". ").toString();
    }

    static public String toString(Address address) {
        if (address == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(address.getClass().getSimpleName());
        if (address instanceof PhysicalAddress) {
            sb.append(" ip=").append(((PhysicalAddress) address).printIpAddress());
        } else if (address instanceof UUID) {
            String name = NameCache.get(address);
            if (name != null) {
                sb.append(" ").append(name);
            }
//            sb.append(" uuid=").append(((UUID) address).toStringLong());
        }
        return sb.toString();
    }
    
    static public String toSeqNoString(Message msg) {
        byte type = CCSUtil.getNakack2Type(msg);
        switch (type) {
            case NakAckHeader2.XMIT_REQ -> {
                if (msg.getObject() instanceof SeqnoList sl) {
                    return "XMIT_REQ " + sl;
                } else {
                    return "XMIT_REQ {}";
                }
            }
            case NakAckHeader2.HIGHEST_SEQNO, NakAckHeader2.MSG, NakAckHeader2.XMIT_RSP -> {
                long seqno = CCSUtil.getHeader(msg, NakAckHeader2.class).getSeqno();
                return NakAckHeader2.type2Str(type) +" {"+ (seqno == -1 ? "" : Long.toString(seqno)) +"}";
            }
            default -> {
                return "{}";
            }
        }
    }
    
    static public String toString(Iterable<Long> seqnos) {
        if (seqnos == null) return "";
        StringBuilder sb = new StringBuilder("");
        long begin = -1;
        long end = -1;
        int n = 0;
        Iterator<Long> it = seqnos.iterator();
        while (it.hasNext()) {
            Long current = it.next();
            if (current != null) {
                long v = current;
                if (v != -1) {
                    if (begin == -1) {
                        end = begin = v;
                    } else if (v == end + 1) {
                        end = v;
                    } else {
                        if (!sb.isEmpty()) sb.append(",");
                        sb.append(begin);
                        if (begin != end) {
                            sb.append(end - begin == 1 ? "," : "-").append(end);
                        }
                        end = begin = v;
                    }
                }
            }
        }
        if (begin != -1) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(begin);
            if (begin != end) {
                sb.append(end - begin == 1 ? "," : "-").append(end);
            }
        }
        return sb.insert(0, "{").append("}").toString();
    }
    
    static public Level max(Level level1, Level level2) {
        if (level1 == null) return level2;
        if (level2 == null) return level1;
        return level1.intValue() > level2.intValue() ? level1 : level2;
    }

}
