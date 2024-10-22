package org.jgroups.ccs;

import java.util.Random;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jgroups.logging.Log;

/**
 * Used to throttle messages before sending them over the network, or simulate message loss.
 *
 * @author onoprien
 */
public class MessageGate {

// -- Fields : -----------------------------------------------------------------
    
    static final long MIN_DELAY = 1000; // ns
    static private final long VETO_TIME = 60*1000000000; // 1 minute
    static private final double VETO_FACTOR = 2;
    
    private final Random rand = new Random();
    private final Log log;
    
    private double loss = -1.; // fraction of discarded messages [0,1]
    
    private long maxSize = 0; // Bytes
    private double maxRate; // Bytes/ns
    
    private long prevDelay, prevTime;
    
    private long accSize = 0;
    private long accTime = System.nanoTime();

// -- Life cycle : -------------------------------------------------------------
    
    /**
     * Constructs an instance.
     * 
     * @param logger Logger.
     */
    public MessageGate(Log logger) {
        log = logger;
    }
    
    
// -- Setters and getters : ----------------------------------------------------
    
    public synchronized void setMessageLoss(double fraction) {
        loss = fraction;
    }
    
    public synchronized void setRateLimit(int sizeMB, int rateMB) {
        maxSize = sizeMB * 1000000L;
        maxRate = rateMB / 1e3;
    }
    
// -----------------------------------------------------------------------------
    
    synchronized public boolean process(int size) {
        
        // Simulate message loss:
        
        boolean lost = loss > 0. && loss > rand.nextDouble();
        if (lost) {
            log.debug("Losing a message of size "+ size +" bytes");
            return false;
        } else {
            log.debug("Publishing a message of size "+ size +" bytes");
        }
        
        // Limit rate:
        
        if (maxSize > 0) limitRate(size);
        return true;
    }
    
    /**
     * Processes the message, taking an appropriate action if rate exceeds threshold.
     * @param size Message size in bytes.
     */
    private void limitRate(int size) {
        if (size <= 0) return;
        long now = System.nanoTime();
        accSize = Math.max(0L, Math.round(accSize - maxRate*(now-accTime)));
        accSize += size;
        accTime = now;
        if (accSize > maxSize) {
            long delay = Math.round((accSize - maxSize)/maxRate);
            if (delay > MIN_DELAY) {
                if (delay > prevDelay * VETO_FACTOR || now > prevTime + VETO_TIME) {
                    if (delay > 1000000000) {
                        log.warn("Throttle : message of size {0} is held for {1} ms.", new Object[]{size, delay / 1000000});
                    } else {
                        log.debug("Throttle : message of size {0} is held for {1} ms.", new Object[]{size, delay / 1000000});
                    }
                    prevDelay = delay;
                    prevTime = now;
                }
                LockSupport.parkNanos(delay);
                now = System.nanoTime();
                accSize = Math.max(0L, Math.round(accSize - maxRate*(now-accTime)));
                accTime = now;
            }
        }
    }    

}
