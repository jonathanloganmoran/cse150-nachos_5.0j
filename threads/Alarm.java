package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;


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
	
	// NEW for T1.3: Remove lock instance -> disable machine interrupts (Issue #3)
	//waitLock = new Lock();				// init lock for waitUntil()
	waitingQueue = new LinkedList<AlarmThread>();		// init queue for waiting threads
	
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
	// NEW T1.3: check if threads are due on waitingQueue
	
	/* NEW T1.3: disable interrupts to place on ready()
	/*waitLock.acquire();					// grab lock for each thread
	 */
	boolean intStatus = Machine.interrupt().disable();
	
	// NEW T1.3: check if threads are due on waitingQueue -> prep for T1.5 PriorityQueue implementation
	while(!waitingQueue.isEmpty() && waitingQueue.peek().sleepUntil <= Machine.timer().getTime()) {
	    AlarmThread thread = waitingQueue.remove();
	    thread.waitingThread.ready();
	}
	
	Machine.interrupt().restore(intStatus);
	    // OLD T1.3: checking without comparator
//	    for(AlarmThread it : waitingQueue) {		// "look" through LinkedList
//		// check dueTime against this CPU clock time and init case == -1
//		if(it.sleepUntil <= Machine.timer().getTime()) {
//		    thread = waitingQueue.remove(it);		// hold temporarily
//		    it.ready();					// flag for readyQueue
//		    waitingQueue.remove(it);			// remove from waitingQueue
//		} 						// else proceed
	    	// till all threads are checked
	} 							// else no waiting threads at currentTime

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
	
	boolean intStatus = Machine.interrupt().disable();
	long dueTime = (Machine.timer().getTime() + x);
	
	// NEW T1.3: update AlarmThread's due time
	AlarmThread waitingThread = new AlarmThread(KThread.currentThread(), dueTime);
	
	// NEW for Task 1.3: put thread onto waitingQueue
	waitingQueue.add(waitingThread);
	KThread.sleep();

	Machine.interrupt().restore(intStatus);
    }
    
    // NEW for T1.3: container class AlarmThread to sort in queue - @jonathanloganmoran 11/4
    public class AlarmThread implements Comparable<AlarmThread> {
	
	// constructor for new AlarmThread priority instance
	public AlarmThread(KThread waitingThread, long sleepUntil) {
	    this.waitingThread = waitingThread;
	    this.sleepUntil = sleepUntil;		// sort in ascending (FIFO)
	}
	
	// NEW for T1.3 -> T1.5: sort threads by dueTime (FIFO) for PriorityQueue
	public int compareTo(AlarmThread alarmThread) {

	    // NEW T1.5: extends Java Long compareTo, returns effectivePriority for waitingQueue 
	    return (new Long(this.sleepUntil).compareTo(new Long (alarmThread.sleepUntil)));
	    
	    // OLD T1.3: compares this thread's dueTime against waitingQueue instance's dueTime 	    
//	    if(this.sleepUntil > alarmThread.sleepUntil) {
//		return 1; 				// due before -> put front
//	    }
//	    else if(this.sleepUntil < alarmThread.sleepUntil) {
//		return -1;				// due after -> push back
//	    }
//	    else {
//		return 0;				// not due
//	    }
	}
	
	//NEW for T1.3: AlarmThread instance variables - @jonathanmoran 11/4
	private KThread waitingThread;			// waitingQueue thread
	private long sleepUntil;			// thread dueTime ref
    }

    // NEW Task 1.3: Alarm Class - not using PriorityQueue yet
    private LinkedList<AlarmThread> waitingQueue;	// hold waitUntil() threads

}
