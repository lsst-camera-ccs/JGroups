package org.jgroups.ccs;

import java.util.Random;
import java.util.concurrent.locks.LockSupport;
import org.jgroups.logging.Log;
import org.jgroups.stack.Protocol;

/**
 * Used to throttle messages before sending them over the network, or simulate message loss.
 *
 * @author onoprien
 */
public final class MessageGate {

// -- Fields : -----------------------------------------------------------------
    
    static final long MIN_DELAY = 10000; // ns
    static private final long VETO_TIME = 60*1000000000; // 1 minute
    static private final double VETO_FACTOR = 2;
    
    private final Random rand = new Random();
    private final Log log;
    
    private volatile boolean lossy = false; // true if some messages should be lost, also used to ensure that updates are visible
    private double loss = -1.; // fraction of discarded messages [0,1]
    
    private boolean rateLimited = false;
    private double maxRate; // Bytes/ns
    
    private long prevDelay, prevTime;
    
    private long accSize;
    private long accTime;
    
    private final CCSProperty.Listener updater = p -> changed(); // listen to properties changes

// -- Life cycle : -------------------------------------------------------------
    
    public MessageGate(Log logger) {
        log = logger;
        changed();
        Protocol.ccs_prop_debug_loss.addListener(updater);
        Protocol.ccs_prop_throttle.addListener(updater);
    }

    public void changed() {
        int maxRateMB = Protocol.ccs_prop_throttle.getInt();
        double lossRate = Protocol.ccs_prop_debug_loss.getDouble();
        synchronized (this) {
            if (maxRateMB > 0) {
                maxRate = maxRateMB / 1e3; // MB/s --> B/ns
                accSize = 0;
                accTime = System.nanoTime();
                prevDelay = 0;
                prevTime = 0;
                rateLimited = true;
            } else {
                maxRate = 0.;
                rateLimited = false;
            }
            if (Double.isNaN(lossRate) || lossRate <= 0. || lossRate >= 1.) {
                lossRate = -1.;
            }
            this.loss = lossRate;
            lossy = lossRate > 0.;
        }
        if (lossy) log.warn("Simulating losing "+ loss +" of multicast messages.");
        if (rateLimited) log.info("Throttling outgoing messages at "+ maxRateMB +" MB/s.");
    }
    
    
// -----------------------------------------------------------------------------
    
    synchronized public boolean process(int size) {
        
        // Simulate message loss:
        
        if (lossy) {
            boolean lost = loss > rand.nextDouble();
            if (lost) {
                log.out(Protocol.ccs_prop_debug_loss.getLevel(), "Losing a message of size " + size + " bytes");
                return false;
            } else {
                log.trace("Publishing a message of size " + size + " bytes");
            }
        }
        
        // Limit rate:
        
        if (rateLimited) {
            limitRate(size);
        }
        
        return true;
    }
    
    /**
     * Processes the message, taking an appropriate action if rate exceeds threshold.
     * @param size Message size in bytes.
     */
    private void limitRate(int size) {
        if (size <= 0) return;
        long now = System.nanoTime();
        accSize = Math.round(accSize - maxRate*(now-accTime));
        accTime = now;
        if (accSize > 0L) {
            long delay = Math.round(accSize/maxRate);
            if (delay > MIN_DELAY) {
                if (delay > prevDelay * VETO_FACTOR || now > prevTime + VETO_TIME) {
                    prevDelay = delay;
                    prevTime = now;
                    log.out(Protocol.ccs_prop_throttle.getLevel(), "Throttle : message of size "+ size +" bytes is held for "+ (delay / 1000000) +" ms.");
                }
                LockSupport.parkNanos(delay);
                now = System.nanoTime();
                accSize = Math.max(0L, Math.round(accSize - maxRate*(now-accTime)));
            }
            accSize += size;
        } else {
            accSize = size;
        }
    }    

}
