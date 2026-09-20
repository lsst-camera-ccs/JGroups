package org.jgroups.protocols;

import java.util.Iterator;
import org.jgroups.Message;
import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.conf.AttributeType;
import org.jgroups.util.ConcurrentBlockingRingBuffer;
import org.jgroups.util.ConcurrentLinkedBlockingQueue;
import org.jgroups.util.FastArray;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.jgroups.ccs.CCSLog;
import org.jgroups.ccs.CCSProperty;
import org.jgroups.ccs.CCSUtil;
import org.jgroups.protocols.pbcast.NakAckHeader2;
import org.jgroups.stack.Protocol;

/**
 * This bundler adds all (unicast or multicast) messages to a queue until max size has been exceeded, but does send
 * messages immediately when no other messages are available. https://issues.redhat.com/browse/JGRP-1540
 */
public class TransferQueueBundler extends BaseBundler implements Runnable {
    protected BlockingQueue<Message> queue;
    protected List<Message>          remove_queue;
    protected Thread                 bundler_thread;
    protected volatile boolean       running=true;
    protected static final String    THREAD_NAME="TQ-Bundler";
    
    // CCS begin
    private final AtomicInteger suppressedRetransmissions = new AtomicInteger();
    private ScheduledExecutorService suppressedRetransmissionsLogger;
    private Level suppressedRetransmissionsLevel;
    // CCS end

    public TransferQueueBundler() {
    }

    @ManagedAttribute(description="Size of the queue",type=AttributeType.SCALAR,gauge=true)
    public int                   getQueueSize()        {return queue.size();}

    @ManagedAttribute(description="Size of the remove-queue",type=AttributeType.SCALAR,gauge=true)
    public int                   removeQueueSize()     {return remove_queue.size();}

    @ManagedAttribute(description="Capacity of the remove-queue")
    public int                   removeQueueCapacity() {return ((FastArray<Message>)remove_queue).capacity();}

    @Override
    public void init(TP transport) {
        super.init(transport);
        if(transport instanceof TCP tcp) {
            tcp.useLockToSend(false); // https://issues.redhat.com/browse/JGRP-2901
            int size=tcp.getBufferedOutputStreamSize();
            if(size < max_size) { // https://issues.redhat.com/browse/JGRP-2903
                int new_size=max_size + Integer.BYTES;
                log.warn("buffered_output_stream_size adjusted from %,d -> %,d", size, new_size);
                tcp.setBufferedOutputStreamSize(new_size);
            }
        }
        // CCS begin
        CCSProperty.Listener updater = p -> {
            synchronized (suppressedRetransmissions) {
                suppressedRetransmissionsLevel = p.getLevel("suppress-bundler");
                if (p.getBoolean("brief") && log.isEnabled(suppressedRetransmissionsLevel)) {
                    suppressedRetransmissionsLogger = Executors.newScheduledThreadPool(1, r -> new Thread(r, "Bundler retransmit suppression logger"));
                    int pp = p.getInt("brief", 10000);
                    long period = pp < 100 ? pp*1000 : pp;  // user entered seconds by mistake
                    suppressedRetransmissionsLogger.scheduleWithFixedDelay(() -> {
                        int n = suppressedRetransmissions.getAndSet(0);
                        if (n > 0) {
                            log.out(suppressedRetransmissionsLevel, "Bundler-suppressed retransmissions in the last "+ (period/1000) +" seconds: "+ n +".");
                        }
                    }, period, period, TimeUnit.MILLISECONDS);
                } else {
                    if (suppressedRetransmissionsLogger != null) {
                        suppressedRetransmissionsLogger.shutdown();
                    }
                }
                suppressedRetransmissions.set(0);
            }
        };
        Protocol.ccs_prop_retransmit.addListener(updater);
        if (Protocol.ccs_prop_retransmit.getBoolean("brief")) {
            updater.changed(Protocol.ccs_prop_retransmit);
        }
        // CCS end
    }

    public synchronized void start() {
        if(running)
            stop();
        // queue blocks on consumer when empty; producers simply drop the message when full
        if(use_ringbuffer)
            queue=new ConcurrentBlockingRingBuffer<>(capacity, true, false);
        else
            queue=new ConcurrentLinkedBlockingQueue<>(capacity, true, false);
        if(remove_queue_capacity == 0)
            remove_queue_capacity=Math.max(capacity/4, 1024);
        remove_queue=new FastArray<>(remove_queue_capacity);
        bundler_thread=transport.getThreadFactory().newThread(this, THREAD_NAME);
        running=true;
        bundler_thread.start();
    }

    public synchronized void stop() {
        running=false;
        Thread tmp=bundler_thread;
        bundler_thread=null;
        if(tmp != null) {
            tmp.interrupt();
            if(tmp.isAlive()) {
                try {
                    tmp.join(500);
                }
                catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        drain();
    }

    public void renameThread() {
        transport.getThreadFactory().renameThread(THREAD_NAME, bundler_thread);
    }

    @ManagedAttribute(description="The number of unsent messages in the bundler",type=AttributeType.SCALAR,gauge=true)
    public int size() {
        return super.size() + removeQueueSize() + getQueueSize();
    }

    public void send(Message msg) throws Exception {
        if(!running)
            return;

        // CCS begin
//        if(!queue.offer(msg))
//            num_drops_on_full_queue.increment();

        // Suppress retransmissions already in queue

        boolean filter = Protocol.ccs_prop_retransmit.getBoolean("suppress-bundler");
        long seqno = -1;
        long now = -1;
        if (filter) {
            now = System.currentTimeMillis();
            long old = now - retransmissionsInQueueLIFE;
            if (retransmissionsInQueueLastClean < old) { // remove old entries
                retransmissionsInQueueLastClean = now;
                Iterator<Map.Entry<Long, Long>> it = retransmissionsInQueue.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Long, Long> e = it.next();
                    if (e.getValue() < old) {
                        it.remove();
                    }
                }
            }
            NakAckHeader2 hdr = CCSUtil.getHeader(msg, NakAckHeader2.class);
            if (hdr != null && msg.getDest() == null
                    && (hdr.getType() == NakAckHeader2.XMIT_RSP || (hdr.getType() == NakAckHeader2.MSG && msg.isFlagSet(Message.TransientFlag.DONT_BLOCK)))) {
                seqno = hdr.getSeqno();
                Long prev = retransmissionsInQueue.get(seqno);
                if (prev != null && now - prev <= retransmissionsInQueueLIFE) {
                    if (suppressedRetransmissionsLogger == null) {
                        Level level = Protocol.ccs_prop_retransmit.getLevel("suppress-bundler");
                        if (log.isEnabled(level)) {
                            log.out(level, "Bundler: supressing retransmission " + seqno);
                        }
                    } else {
                        suppressedRetransmissions.incrementAndGet();
                    }
                    return; // dropping retransmission
                }
            }
        }
        
        // Try putting it into queue
        
        if (queue.offer(msg)) {
            if (seqno > -1) { // retransmission goes into bundle queue - add it to retransmissionsInQueue
                retransmissionsInQueue.put(seqno, now);
            }
            if (Protocol.ccs_prop_bundler_in.isSet()) {
                byte type = CCSUtil.getNakack2Type(msg);
                if (type > 0 && log.isEnabled(ccs_prop_bundler_in_level[type])) {
                    log.out(ccs_prop_bundler_in_level[type], "Bundler: in " + CCSLog.toSeqNoString(msg) + ".");
                }
            }
        } else {
            num_drops_on_full_queue.increment();
            if (Protocol.ccs_prop_bundler_in.isSet()) {
                byte type = CCSUtil.getNakack2Type(msg);
                if (type > 0 && log.isEnabled(ccs_prop_bundler_in_level[type])) {
                    log.out(ccs_prop_bundler_in_level[type], "Bundler: dropped " + CCSLog.toSeqNoString(msg) + ".");
                }
            }
        }
        // CCS end
    }

    public void run() {
        while(running) {
            Message msg=null;
            try {
                if((msg=queue.take()) == null)
                    continue;
                addAndSendIfSizeExceeded(msg);
                while(true) {
                    remove_queue.clear();
                    int num_msgs=queue.drainTo(remove_queue, remove_queue_capacity);
                    if(num_msgs <= 0)
                        break;
                    avg_remove_queue_size.add(num_msgs);
                    remove_queue.forEach(this::addAndSendIfSizeExceeded); // ArrayList.forEach() avoids array bounds check
                }
                if(count > 0) {
                    if(transport.statsEnabled())
                        avg_fill_count.add(count);
                    sendBundledMessages();
                    num_sends_because_no_msgs.increment();
                }
            }
            catch(InterruptedException iex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    protected void addAndSendIfSizeExceeded(Message msg) {
        int size=msg.size();
        // CCS begin : do not bundle HIGHEST_SEQNO with previously submitted messages
//        if(count + size > max_size) {
        if(count + size > max_size || (CCSUtil.getNakack2Type(msg) == NakAckHeader2.HIGHEST_SEQNO && Protocol.ccs_prop_hseqno.isSet())) {
        // CCS end
            if(transport.statsEnabled())
                avg_fill_count.add(count);
            sendBundledMessages();
            num_sends_because_full_queue.increment();
        }
        addMessage(msg, size);
    }

    /** Takes all messages from the queue, adds them to the hashmap and then sends all bundled messages */
    protected void drain() {
        Message msg;
        if(queue != null) {
            while((msg=queue.poll()) != null)
                addAndSendIfSizeExceeded(msg);
        }
        if(!msgs.isEmpty())
            sendBundledMessages();
    }


}
