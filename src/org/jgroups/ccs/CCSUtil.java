package org.jgroups.ccs;

import org.jgroups.Header;
import org.jgroups.Message;
import org.jgroups.PhysicalAddress;
import org.jgroups.protocols.pbcast.NakAckHeader2;
import org.jgroups.stack.IpAddress;
import org.jgroups.stack.Protocol;

/**
 * Miscellaneous CCS-specific static utility methods.
 *
 * @author onoprien
 */
public class CCSUtil {
    
    static public boolean isValid(PhysicalAddress address) {
        if (address instanceof IpAddress a) {
            return a.getIpAddress() != null && a.getPort() > 0;
        } else {
            return false;
        }
    }
    
    static public <T extends Header> T getHeader(Message msg, Class<T> clazz) {
        if (msg == null) return null;
        for (Header h : msg.getHeaders().values()) {
            if (clazz.isInstance(h)) return (T) h;
        }
        return null;
    }
    
    static public boolean isRetransmission(Message msg) {
        NakAckHeader2 hdr = CCSUtil.getHeader(msg, NakAckHeader2.class);
        return (hdr != null && msg.getDest() == null
                && (hdr.getType() == NakAckHeader2.XMIT_RSP || (hdr.getType() == NakAckHeader2.MSG && msg.isFlagSet(Message.TransientFlag.DONT_BLOCK))));
    }
    
    static public byte getNakack2Type(Message msg) {
        if (msg== null) return 0;
        NakAckHeader2 hdr = CCSUtil.getHeader(msg, NakAckHeader2.class);
        if (hdr == null) return 0;
        byte type = hdr.getType();
        return switch (type) {
            case NakAckHeader2.HIGHEST_SEQNO, NakAckHeader2.XMIT_REQ, NakAckHeader2.XMIT_RSP -> type;
            case NakAckHeader2.MSG -> msg.isFlagSet(Message.TransientFlag.DONT_BLOCK) ? NakAckHeader2.XMIT_RSP : NakAckHeader2.MSG;
            default -> 0;
        };
    }
    
    /**
     * Returns seqno of outgoing retransmission, or -1.
     */
    static public long getRetransmissionSeqNo(Message msg) {
        if (msg == null) return -1;
        NakAckHeader2 hdr = CCSUtil.getHeader(msg, NakAckHeader2.class);
        if (hdr != null && msg.getDest() == null
                && (hdr.getType() == NakAckHeader2.XMIT_RSP || (hdr.getType() == NakAckHeader2.MSG && msg.isFlagSet(Message.TransientFlag.DONT_BLOCK)))) {
            return hdr.getSeqno();
        } else {
            return -1L;
        }
    }
    
    static public long getSeqNo(Message msg) {
        if (msg == null) return -1;
        NakAckHeader2 hdr = CCSUtil.getHeader(msg, NakAckHeader2.class);
        return hdr == null ? -1 : hdr.getSeqno();
    }
    
}
