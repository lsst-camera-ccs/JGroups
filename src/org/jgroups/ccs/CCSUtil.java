package org.jgroups.ccs;

import org.jgroups.Address;
import org.jgroups.PhysicalAddress;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.NameCache;
import org.jgroups.util.UUID;

/**
 * CCS-specific utilities.
 *
 * @author onoprien
 */
public class CCSUtil { // CCS begin
    
    static public String toString(Address address) {
        if (address == null) return "null";
        StringBuilder sb = new StringBuilder(address.getClass().getSimpleName());
        if (address instanceof PhysicalAddress) {
            sb.append(" ip=").append(((PhysicalAddress)address).printIpAddress());
        } else if (address instanceof UUID) {
            String name = NameCache.get(address);
            if (name != null) sb.append(" ").append(name);
            sb.append(" uuid=").append(((UUID)address).toStringLong());
        }
        return sb.toString();
    }
    
    static public boolean isValid(PhysicalAddress address) {
        if (address instanceof IpAddress) {
            IpAddress a = (IpAddress) address;
            return a.getIpAddress() != null && a.getPort() > 0;
        } else {
            return false;
        }
    }
    
} // CCS end
