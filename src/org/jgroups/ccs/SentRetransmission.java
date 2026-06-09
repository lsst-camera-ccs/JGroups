package org.jgroups.ccs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author onoprien
 */
public class SentRetransmission implements Serializable {
    
    private long seqno;
    private ArrayList<Long> seqnos;
    private boolean success;
    
    private boolean notSorted = true;
    
    public SentRetransmission(long seqno) {
        this(seqno, true);
    }
    
    public SentRetransmission(long seqno, boolean success) {
        this.seqno = seqno;
        seqnos = null;
        this.success = success;
    }
    
    public SentRetransmission() {
        this(true);
    }
    
    public SentRetransmission(boolean success) {
        seqno = -1;
        seqnos = null;
        this.success = success;
    }
    
    public void add(long seqno) {
        if (seqnos != null) {
            seqnos.add(seqno);
        } else if (this.seqno == -1) {
            this.seqno = seqno;
        } else {
            seqnos = new ArrayList<>();
            seqnos.add(this.seqno);
            seqnos.add(seqno);
            this.seqno = -1;
        }
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public List<Long> getSeqnos() {
        if (seqnos == null) {
            return seqno == -1 ? Collections.emptyList() : Collections.singletonList(seqno);
        } else {
            if (notSorted) {
                Collections.sort(seqnos);
                notSorted = false;
            }
            return seqnos;
        }

    }
    
    public int size() {
        if (seqno == -1) {
            return seqnos == null ? 0 : seqnos.size();
        } else {
            return 1;
        }
    }
    
    public boolean isEmpty() {
        return seqno == -1 && seqnos == null;
    }

    @Override
    public String toString() {
        if (seqnos == null) {
            return seqno == -1 ? "{}" : "{"+ Long.toString(seqno) +"}";
        } else {
            if (notSorted) {
                Collections.sort(seqnos);
                notSorted = false;
            }
            return "{"+ String.join(", ", seqnos.stream().map(s -> Long.toString(s)).toList()) +"}";
        }
    }
  
}
