package nachos.threads;

import nachos.machine.*;

/* wire LinkedList structure for waitQueue */
import java.util.LinkedList;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
	this.conditionLock = conditionLock;
	
	/* initialize waitQueue using LinkedList constructor */
	waitQueue = new LinkedList<KThread>();
	
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically re-acquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
	    Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	    /* disable interrupts using lock */
	    boolean intStatus = Machine.interrupt().disable();
	
	    /* add current thread to waitQueue */
	    waitQueue.add(KThread.currentThread());
	
	    /* release lock before putting to sleep */
	    conditionLock.release();
	
	    /* put current thread to sleep */
	    KThread.sleep();
	
        /* Re-acquire lock*/
	    conditionLock.acquire();
	
	    /* Enable interrupts */
	    Machine.interrupt().restore(intStatus);
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
      
        /* check lock condition */  
	    Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	
        /* disable interrupts */
        boolean intStatus = Machine.interrupt().disable();
	
        /* remove one thread (first) from waitQueue */
        if(!waitQueue.isEmpty()) {
            /* put thread onto readyQueue */
            waitQueue.removeFirst().ready();  
        }
    
        /* re-enable interrupts */
        Machine.interrupt().restore(intStatus);
    
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    
    
    public void wakeAll() {
	    Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	
	    /* disable interrupts */
        boolean intStatus = Machine.interrupt().disable();
    
	    /* check waitQueue isEmpty() */
        /* isEmpty() can only be called on KThread objects */
        while(!waitQueue.isEmpty()) {
            /* call first thread to execute */
            waitQueue.removeFirst().ready();
        }
        
        /* re-acquire lock */
        Machine.interrupt().restore(intStatus);     
    }

    private Lock conditionLock;
    
    /* using LinkedList for isEmpty() condition */
    private LinkedList<KThread> waitQueue;
}
