package org.jgroups.ccs;

import org.jgroups.Header;
import org.jgroups.Message;
import org.jgroups.PhysicalAddress;
import org.jgroups.stack.IpAddress;

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
        for (Header h : msg.getHeaders().values()) {
            if (clazz.isInstance(h)) return (T) h;
        }
        return null;
    }
    
}
