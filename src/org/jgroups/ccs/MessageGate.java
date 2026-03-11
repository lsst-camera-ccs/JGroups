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
    
    static final long MIN_DELAY = 10000; // ns
    static private final long VETO_TIME = 60*1000000000; // 1 minute
    static private final double VETO_FACTOR = 2;
    
    private final Random rand = new Random();
    private final Log log;
    private final Level lev;
    
    private double loss = -1.; // fraction of discarded messages [0,1]
    
    private double maxRate; // Bytes/ns
    
    private long prevDelay, prevTime;
    
    private long accSize = 0;
    private long accTime = System.nanoTime();

// -- Life cycle : -------------------------------------------------------------
    
    public MessageGate(Log logger) {
        this(logger, Level.FINE);
    }
    
    public MessageGate(Log logger, Level level) {
        log = logger;
        lev = level == null ? Level.ALL : level;
    }
    
    
// -- Setters and getters : ----------------------------------------------------
    
    public synchronized void setMessageLoss(double fraction) {
        loss = fraction;
    }
    
    public synchronized void setRateLimit(int rateMB) {
        maxRate = rateMB / 1e3; // MB/s --> B/ns
    }
    
// -----------------------------------------------------------------------------
    
    synchronized public boolean process(int size) {
        
        // Simulate message loss:
        
        boolean lost = loss > 0. && loss > rand.nextDouble();
        if (lost) {
            log.out(lev, "Losing a message of size "+ size +" bytes");
            return false;
        } else {
            log.trace("Publishing a message of size "+ size +" bytes");
        }
        
        // Limit rate:
        
        limitRate(size);
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
                    log.out(lev, "Throttle : message of size "+ size +" bytes is held for "+ (delay / 1000000) +" ms.");
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
