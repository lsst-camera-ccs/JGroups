package org.jgroups.protocols;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.PhysicalAddress;
import org.jgroups.util.NameCache;
import org.jgroups.util.Responses;

import java.io.InterruptedIOException;
import java.util.List;
import java.util.stream.Collectors;
import org.jgroups.ccs.CCSUtil;


/**
 * The PING protocol retrieves the initial membership by mcasting a discovery request (via the multicast capable
 * transport) to all current cluster members<p/>
 * The responses should allow us to determine the coordinator which we have to contact, e.g. in case we want to join
 * the group.  When we are a server (after having received the BECOME_SERVER event), we'll respond to discovery requests
 * with a discovery response.
 * @author Bela Ban
 */
public class PING extends Discovery {

    public boolean isDynamic() {
        return true;
    }


    public void findMembers(List<Address> members, boolean initial_discovery, Responses responses) {
        try {
            sendDiscoveryRequest(cluster_name, members, initial_discovery);
        }
        catch(InterruptedIOException | InterruptedException ie) {
            log.warn(String.format("%s: failed sending discovery request", local_addr), ie);
        }
        catch(Throwable ex) {
            log.error(String.format("%s: failed sending discovery request", local_addr), ex);
        }
    }


    protected void sendDiscoveryRequest(String cluster_name, List<Address> members_to_find, boolean initial_discovery) throws Exception {
        
        PingData data=null;

        if(!use_ip_addrs || !initial_discovery) {
            PhysicalAddress physical_addr=(PhysicalAddress)down(new Event(Event.GET_PHYSICAL_ADDRESS, local_addr));
            // https://issues.jboss.org/browse/JGRP-1670
            data=new PingData(local_addr, false, NameCache.get(local_addr), physical_addr);
            if(members_to_find != null && members_to_find.size() <= max_members_in_discovery_request)
                data.mbrs(members_to_find);
        }

        // message needs to have DONT_BUNDLE flag: if A sends message M to B, and we need to fetch B's physical
        // address, then the bundler thread blocks until the discovery request has returned. However, we cannot send
        // the discovery *request* until the bundler thread has returned from sending M
        PingHeader hdr=new PingHeader(PingHeader.GET_MBRS_REQ).clusterName(cluster_name).initialDiscovery(initial_discovery);
        Message msg=new Message(null).putHeader(getId(),hdr)
          .setFlag(Message.Flag.INTERNAL,Message.Flag.DONT_BUNDLE,Message.Flag.OOB)
          .setTransientFlag(Message.TransientFlag.DONT_LOOPBACK);
        if(data != null)
            msg.setBuffer(marshal(data));
        
        // CCS begin
        if (ccs_physical || ccs_connect) {
            StringBuilder sb = new StringBuilder();
            sb.append("PING.sendDiscoveryRequest, initial: ").append(initial_discovery).append(", members: ").append(members_to_find).append(".");
            sb.append(" Cluster: " ).append(cluster_name).append(".");
            if (data != null) {
                sb.append(" Sender: ").append(data.getLogicalName()).append(", Logical: ").append(CCSUtil.toString(data.getAddress())).append(", Physical: ").append(CCSUtil.toString(data.getPhysicalAddr()));
                sb.append(", coord: ").append(data.isCoord()).append(", server: ").append(data.isServer()).append(".");
                if (data.mbrs() != null) {
                    sb.append(" Members: ").append(String.join(", ", data.mbrs().stream().map(m -> CCSUtil.toString(m)).collect(Collectors.toList()))).append(".");
                }
            }
            if (data == null || cluster_name == null || data.getLogicalName() == null || data.getAddress() == null || !CCSUtil.isValid(data.getPhysicalAddr())) {
                log.warn(sb.toString());
            } else {
                log.info(sb.toString());
            }
        }
        // CCS end
        
        sendMcastDiscoveryRequest(msg);
    }

    protected void sendMcastDiscoveryRequest(Message msg) {
        down_prot.down(msg);
    }
}
