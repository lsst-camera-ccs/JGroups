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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import org.jgroups.ccs.CCSLog;
import org.jgroups.ccs.CCSProperty;
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
    
    // CCS begin
    protected volatile Level[] ccs_prop_bundler_in_level;
    protected volatile Level[] ccs_prop_bundler_out_level;
    // CCS end

    public int     getCapacity()       {return capacity;}
    public Bundler setCapacity(int c)  {this.capacity=c; return this;}
    public int     getMaxSize()        {return max_size;}
    public Bundler setMaxSize(int s)   {max_size=s; return this;}

    public void init(TP transport) {
        this.transport=transport;
        log=transport.getLog();
        output=new ByteArrayDataOutputStream(max_size + MSG_OVERHEAD);
        // CCS begin
        CCSProperty.Listener ccs_prop_bundler_listener = p -> {
            Level[] out = new Level[5];
            if (p.isSet()) {
                for (byte i=1; i<5; i++) {
                    out[i] = p.getLevel(NakAckHeader2.type2Str(i));
                }
            }
            if (p == Protocol.ccs_prop_bundler_in) {
                ccs_prop_bundler_in_level = out;
            } else {
                ccs_prop_bundler_out_level = out;
            }
        };
        Protocol.ccs_prop_bundler_in.addListener(ccs_prop_bundler_listener);
        ccs_prop_bundler_listener.changed(Protocol.ccs_prop_bundler_in);
        Protocol.ccs_prop_bundler_out.addListener(ccs_prop_bundler_listener);
        ccs_prop_bundler_listener.changed(Protocol.ccs_prop_bundler_out);
        // CCS end
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
        Level level = null;
        long time1 = 0;
        long maxTime = 0;
        boolean logEnabled = Protocol.ccs_prop_bundler_out.isSet();
        if (logEnabled) {
            maxTime = Protocol.ccs_prop_bundler_out.getInt();
            byte type = CCSUtil.getNakack2Type(msg);
            level = ccs_prop_bundler_out_level[type];
            logEnabled = type != 0 && log.isEnabled(level);
            if (logEnabled || maxTime > 0) {
                time1 = System.currentTimeMillis();
            }
        }
        // CCS end
        Address dest=msg.getDest();
        try {
            Util.writeMessage(msg, output, dest == null);
            // CCS begin
            long time2 = logEnabled || maxTime > 0 ? System.currentTimeMillis() : 0;
            if (Protocol.ccs_prop_sendfail.isLogEnabled(log) && CCSUtil.isRetransmission(msg) && transport instanceof UDP t) {
                t.sendfail_reportSuccess = true;
            }
            // CCS end
            transport.doSend(output.buffer(), 0, output.position(), dest);
            // CCS begin
            long timeToSerialize = time2-time1;
            long timeToSend = System.currentTimeMillis() - time2;
            if (maxTime > 0 && timeToSerialize + timeToSend > maxTime) {
                log.warn("Bundler: long time to send "+ msg +". Timing: "+ timeToSerialize +" + "+ timeToSend +" ms.");
            }
            if (logEnabled) {
                log.out(level, "Bundler: out "+ CCSLog.toSeqNoString(msg) +". Timing: "+ timeToSerialize +" + "+ timeToSend +" ms. Message: ["+ msg.size() +"] "+ msg);
            }
            // CCS end
            transport.getMessageStats().incrNumSingleMsgsSent();
        }
        catch(Throwable e) {
            // CCS begin
            level = logEnabled ? CCSLog.max(level, Protocol.ccs_prop_bundler_out.getLevel("fail")) : Protocol.ccs_prop_bundler_out.getLevel("fail");
            log.out(level, "Bundler: out "+ CCSLog.toSeqNoString(msg) +". FAILED.", e);
            // CCS end
            log.trace(Util.getMessage("SendFailure"),
                      transport.getAddress(), (dest == null? "cluster" : dest), msg.size(), e.toString(), msg.printHeaders());
        }
    }

    protected void sendMessageList(final Address dest, final Address src, final List<Message> list) {
        // CCS begin
        boolean ccs = Protocol.ccs_prop_bundler_out.isSet();
        long time1 = ccs ? System.currentTimeMillis() : 0;
        // CCS end
        try {
            Util.writeMessageList(dest, src, transport.cluster_name.chars(), list, output, dest == null);
            // CCS begin
            long time2 = ccs ? System.currentTimeMillis() : 0;
            if (Protocol.ccs_prop_sendfail.isLogEnabled(log) && list.stream().anyMatch(m -> CCSUtil.isRetransmission(m)) && transport instanceof UDP t) {
                t.sendfail_reportSuccess = true;
            }
            // CCS end
            transport.doSend(output.buffer(), 0, output.position(), dest);
            // CCS begin
            if (ccs) {
                long timeToSerialize = time2-time1;
                long timeToSend = System.currentTimeMillis() - time2;
                long maxTime = Protocol.ccs_prop_bundler_out.getInt();
                if (maxTime > 0 && timeToSerialize + timeToSend > maxTime) {
                    log.warn("Bundler: long time to send message list. Timing: "+ timeToSerialize +" + "+ timeToSend +" ms.");
                }
                LogString s = toString(list, false);
                if (s != null) {
                    StringBuilder sb = new StringBuilder("Bundler: out ");
                    sb.append(s.text);
                    sb.append(" Timing: ").append(time2-time1).append(" + ").append(System.currentTimeMillis() - time2).append(" ms.");
                    log.out(s.level, sb.toString());
                }
            }
            // CCS end
            transport.getMessageStats().incrNumBatchesSent();
        }
        catch(Throwable e) {
            // CCS begin
            if (ccs) {
                LogString s = toString(list, true);
                if (s != null) {
                    Level level = CCSLog.max(s.level, Protocol.ccs_prop_bundler_out.getLevel("fail"));
                    log.out(level, "Bundler: out "+ s.text + " FAILED.", e);
                }
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
    
    // CCS begin
    protected record LogString(String text, Level level) {};
    protected LogString toString(List<Message> mm, boolean all) {
        Level maxLevel = null;
        Object[] messagesByType = new Object[5];
        for (Message m : mm) {
            byte type = CCSUtil.getNakack2Type(m);
            if (type > 0 && (all || log.isEnabled(ccs_prop_bundler_out_level[type]))) {
                Set<Long> seqnos = (Set<Long>) messagesByType[type];
                if (seqnos == null) {
                    seqnos = new TreeSet<>();
                    messagesByType[type] = seqnos;
                }
                if (type == NakAckHeader2.XMIT_REQ && m.getObject() instanceof SeqnoList sl) {
                    Iterator<Long> it = sl.iterator();
                    while (it.hasNext()) seqnos.add(it.next());
                } else {
                    long seqno = CCSUtil.getSeqNo(m);
                    if (seqno != -1) seqnos.add(seqno);
                }
                maxLevel = CCSLog.max(maxLevel, ccs_prop_bundler_out_level[type]);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte type = 1; type < 5; type++) {
            Set<Long> seqnos = (Set<Long>) messagesByType[type];
            if (seqnos != null) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(NakAckHeader2.type2Str(type)).append(" ").append(CCSLog.toString(seqnos));
            }
        }
        return maxLevel == null ? null : new LogString(sb.toString(), maxLevel);
    }
    // CCS end
}