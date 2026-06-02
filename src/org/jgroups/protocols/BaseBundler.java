package org.jgroups.protocols;

import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.View;
import org.jgroups.annotations.GuardedBy;
import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.annotations.Property;
import org.jgroups.conf.AttributeType;
import org.jgroups.logging.Log;
import org.jgroups.util.AverageMinMax;
import org.jgroups.util.ByteArrayDataOutputStream;
import org.jgroups.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import org.jgroups.ccs.CCSLog;
import org.jgroups.ccs.CCSUtil;

import static org.jgroups.protocols.TP.MSG_OVERHEAD;
import org.jgroups.protocols.pbcast.NakAckHeader2;
import org.jgroups.stack.Protocol;
import org.jgroups.util.SeqnoList;

/**
 * Implements storing of messages in a hashmap and sending of single messages and message batches. Most bundler
 * implementations will want to extend this class
 * @author Bela Ban
 * @since  4.0
 */
public abstract class BaseBundler implements Bundler {
    /** Keys are destinations, values are lists of Messages */
    protected final Map<Address,List<Message>>  msgs=new HashMap<>(24);
    protected TP                                transport;
    protected final ReentrantLock               lock=new ReentrantLock();
    protected @GuardedBy("lock") long           count;    // current number of bytes accumulated
    protected ByteArrayDataOutputStream         output;
    protected Log                               log;

    /**
     * Maximum number of bytes for messages to be queued until they are sent.
     * This value needs to be smaller than the largest datagram packet size in case of UDP
     */
    @Property(name="max_size", type=AttributeType.BYTES,
      description="Maximum number of bytes for messages to be queued until they are sent")
    protected int                               max_size=64000;

    @Property(description="The max number of elements in a bundler if the bundler supports size limitations",
      type=AttributeType.SCALAR)
    protected int                               capacity=16384;

    @ManagedAttribute(description="Time (us) to send the bundled messages")
    protected final AverageMinMax               avg_send_time=new AverageMinMax().unit(TimeUnit.MICROSECONDS);



    public int     getCapacity()       {return capacity;}
    public Bundler setCapacity(int c)  {this.capacity=c; return this;}
    public int     getMaxSize()        {return max_size;}
    public Bundler setMaxSize(int s)   {max_size=s; return this;}

    public void init(TP transport) {
        this.transport=transport;
        log=transport.getLog();
        output=new ByteArrayDataOutputStream(max_size + MSG_OVERHEAD);
    }

    public void resetStats() {
        avg_send_time.clear();
    }

    public void start() {}
    public void stop()  {}
    public void send(Message msg) throws Exception {}

    public void viewChange(View view) {
        // code removed (https://issues.redhat.com/browse/JGRP-2324)
    }

    /** Returns the total number of messages in the hashmap */
    @ManagedAttribute(description="The number of unsent messages in the bundler")
    public int size() {
        lock.lock();
        try {
            return msgs.values().stream().map(List::size).reduce(0, Integer::sum);
        }
        finally {
            lock.unlock();
        }
    }

    @ManagedAttribute(description="Size of the queue (if available")
    public int getQueueSize() {
        return -1;
    }

    /**
     * Sends all messages in the map. Messages for the same destination are bundled into a message list.
     * The map will be cleared when done.
     */
    @GuardedBy("lock") protected void sendBundledMessages() {
        long start=System.nanoTime();
        for(Map.Entry<Address,List<Message>> entry: msgs.entrySet()) {
            List<Message> list=entry.getValue();
            if(list.isEmpty())
                continue;
            output.position(0);
            if(list.size() == 1)
                sendSingleMessage(list.get(0));
            else {
                Address dst=entry.getKey();
                sendMessageList(dst, list.get(0).getSrc(), list);
            }
            list.clear();
        }
        count=0;
        long time_us=(System.nanoTime()-start) / 1_000;
        avg_send_time.add(time_us);
    }


    protected void sendSingleMessage(final Message msg) {
        // CCS begin
        boolean ccs = Protocol.ccs_prop_retransmit.isLogEnabled(log);
        NakAckHeader2 hdr = null;
        Level level = null;
        StringBuilder sb = null;
        if (ccs) {
            hdr = CCSUtil.getHeader(msg, NakAckHeader2.class);
            ccs = hdr != null && hdr.getType() != NakAckHeader2.HIGHEST_SEQNO &&
                 (hdr.getType() == NakAckHeader2.XMIT_RSP || hdr.getType() == NakAckHeader2.XMIT_REQ || msg.isFlagSet(Message.TransientFlag.DONT_BLOCK));
            if (ccs) {
                level = Protocol.ccs_prop_retransmit.getLevel();
                sb = new StringBuilder();
                sb.append("BaseBundler: sent retransmit ").append(NakAckHeader2.type2Str(hdr.getType())).append(" ");
                if (hdr.getType() == NakAckHeader2.XMIT_REQ) {
                    if (msg.getObject() instanceof SeqnoList s) {
                        sb.append(s);
                    } else {
                        sb.append("{unknown}");
                    }
                } else {
                    sb.append("{").append(hdr.getSeqno()).append("}");
                }
                sb.append(". Message: ").append(msg);
            }
        }
        long time1 = ccs ? System.currentTimeMillis() : 0;
        // CCS end
        Address dest=msg.getDest();
        try {
            Util.writeMessage(msg, output, dest == null);
            // CCS begin
            long time2 = ccs ? System.currentTimeMillis() : 0;
            // CCS end
            transport.doSend(output.buffer(), 0, output.position(), dest);
            // CCS begin
            if (ccs) {
                sb.append(" Timing: ").append(time2-time1).append(" + ").append(System.currentTimeMillis() - time2).append(" ms.");
                log.out(level, sb.toString());
            }
            // CCS end
            transport.getMessageStats().incrNumSingleMsgsSent();
        }
        catch(Throwable e) {
            // CCS begin
            if (ccs) {
                log.out(level, sb.append(" FAILED.").toString(), e);
            }
            // CCS end
            log.trace(Util.getMessage("SendFailure"),
                      transport.getAddress(), (dest == null? "cluster" : dest), msg.size(), e.toString(), msg.printHeaders());
        }
    }

    protected void sendMessageList(final Address dest, final Address src, final List<Message> list) {
        // CCS begin
        boolean ccs = Protocol.ccs_prop_retransmit.isLogEnabled(log);
        Level level = null;
        long time1 = 0;
        List<String> requests = new LinkedList<>();
        List<String> responses = new LinkedList<>();
        StringBuilder sb = null;
        if (ccs) {
            for (Message msg : list) {
                NakAckHeader2 hdr = CCSUtil.getHeader(msg, NakAckHeader2.class);
                if (hdr != null) {
                    byte type = hdr.getType();
                    if (type == NakAckHeader2.XMIT_REQ && msg.getObject() instanceof SeqnoList seqnoList) {
                        requests.add(seqnoList.toString());
                    } else if ((msg.isFlagSet(Message.TransientFlag.DONT_BLOCK) && type == NakAckHeader2.MSG) || type == NakAckHeader2.XMIT_RSP) {
                        responses.add(Long.toString(hdr.getSeqno()));
                    }
                }
            }
            ccs = !(requests.isEmpty() && responses.isEmpty());
            if (ccs) {
                time1 = System.currentTimeMillis();
                level = Protocol.ccs_prop_retransmit.getLevel();
                sb = new StringBuilder();
                sb.append("BaseBundler: sending retransmit bundle. ");
                if (!requests.isEmpty()) {
                    sb.append("Requests: ").append(String.join(", ", requests)).append(". ");
                }
                if (!responses.isEmpty()) {
                    sb.append("Responses: ").append(String.join(", ", responses)).append(". ");
                }
                sb.append(" Destination: ").append(CCSLog.toString(dest));
            }
        }
        // CCS end
        try {
            Util.writeMessageList(dest, src, transport.cluster_name.chars(), list, output, dest == null);
            // CCS begin
            long time2 = ccs ? System.currentTimeMillis() : 0;
            // CCS end
            transport.doSend(output.buffer(), 0, output.position(), dest);
            // CCS begin
            if (ccs) {
                sb.append(" Timing: ").append(time2-time1).append(" + ").append(System.currentTimeMillis() - time2).append(" ms.");
                log.out(level, sb.toString());
            }
            // CCS end
            transport.getMessageStats().incrNumBatchesSent();
        }
        catch(Throwable e) {
            // CCS begin
            if (ccs) {
                log.out(Protocol.ccs_prop_retransmit.getLevel(), sb.append(" FAILED.").toString(), e);
            }
            // CCS end
            log.trace(Util.getMessage("FailureSendingMsgBundle"), transport.getAddress(), e);
        }
    }

    @GuardedBy("lock") protected void addMessage(Message msg, int size) {
        Address dest=msg.getDest();
        List<Message> tmp=msgs.computeIfAbsent(dest, k -> new ArrayList<>(16));
        tmp.add(msg);
        count+=size;
    }
}