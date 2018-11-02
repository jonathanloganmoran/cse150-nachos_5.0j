package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.Lock;		// NEW waitLock - P1 T1.3

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
	
	// NEW for T1.3: Alarm class
	waitLock = new Lock();				// init lock for waitUntil()
	this.waitingQueue = new LinkedList<KThread>();	// init queue for waiting threads
	
	
	
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
	// NEW T1.3: check if threads are due on waitingQueue
	
	// NEW T1.3: disable interrupts to place on ready()
	waitLock.acquire();				// grab lock for each thread
	
	// NEW T1.3: check if threads are due on waitingQueue
	if(!waitingQueue.isEmpty()) {
	    for(KThread it : waitingQueue) {		// "look" through queue
		// check dueTime against dynamic CPU clock time and init case == -1
		if(it.getDueTime() <= Machine.timer().getTime() && it.getDueTime() > -1) {
		    it.ready();				// flag for readyQueue
		    waitingQueue.remove(it);		// remove from waitingQueue
		} 					// else proceed
	    }						// till all threads are checked
	} 						// else no waiting threads at currentTime
	
	// NEW T1.3: check if threads are due on waitingQueue
	waitLock.release();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
	// acquire lock to put threads to sleep
	waitLock.acquire();
	
	long dueTime = (Machine.timer().getTime() + x);
	
	// NEW T1.3: update thread's due time -- NEED Get()/Set() methods
	KThread.currentThread().setDueTime(dueTime);
	
	// NEW for Task 1.3: put thread onto waitingQueue
	if(dueTime >= Machine.timer().getTime()) {	// handle due threads
	    waitingQueue.add(KThread.currentThread());
	    KThread.sleep();
	}
	// no more due threads, end of critical section
	waitLock.release();

    }
    // NEW Task 1.3: Alarm Class
    private Lock waitLock;				// acquire lock in waitUntil()
    private LinkedList<KThread> waitingQueue;		// hold waitUntil() threads

}
