package nachos.threads;

import nachos.machine.*;

/* unused PriorityScheduler imports
 * import java.util.TreeSet;
 * import java.util.HashSet;
 * import java.util.Iterator;
 */

// NEW T1.5: LinkedList for PriorityQueue
import java.util.LinkedList;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
	// NEW T1.5: waitingQueue to store thread state
	PriorityQueue(boolean transferPriority) {
	    this.transferPriority = transferPriority;    
	    this.waitingQueue = new LinkedList<ThreadState>();	// using ThreadState KThread pointer
	}
	// NEW T1.5: add to waitingQueue
	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    this.waitingQueue.add(getThreadState(thread));	// add thread to queue
	    getThreadState(thread).waitForAccess(this);
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).acquire(this);
	}

	public KThread nextThread() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement me -- Task 1.5: @amunoz35 11/3

	    // NEW T1.5: clean up nextThread - @jonathanloganmoran 11/4
	    ThreadState check = this.pickNextThread(); 		// get this thread's state
	    if(check != null) {					// if thread state needs handling
		check.acquire(this);
		return check.thread;		  		// will return the thread
	    }
	    return null;			  		// if not, returns nothing otherwise
	}

	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected ThreadState pickNextThread() {
	    // implement me - @jonathanloganmoran 11/4
	    ThreadState nextThread = null;			// pointer to next in line
	    
	    // NEW T1.5: find thread that has waited the longest
	    for(ThreadState wQ : this.waitingQueue) {
		int thisPriority = getThreadState(wQ.thread).getEffectivePriority();
		if(nextThread == null) {			// case 1: init nextThread pointer
		    nextThread = getThreadState(wQ.thread);	// next on PriorityQueue
		}
		// NEW T1.5: handle changes to priority after nextThread is check
		else if(thisPriority > getThreadState(wQ.thread).getEffectivePriority()) {
		    nextThread = getThreadState(wQ.thread);
		}
	    }
	    return nextThread;
	}
	
	public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement me (if you want)
	}

	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
	
	// NEW T1.5: added waiting threads PriorityQueue
	protected LinkedList<ThreadState> waitingQueue;
	
	// NEW T1.5: thread waiting for priority
	protected ThreadState waitingThread = null;
	
	
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    
    // NEW T1.5: cached effectivePriority implementation - @jonathanloganmoran
    protected class ThreadState {
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread) {
	    this.thread = thread;
	    // NEW T1.5: thread holds lists to queued resources
	    this.runningQueue = new SynchList();
	    this.waitingQueue = new SynchList();
	    
	    setPriority(priorityDefault);
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
	    // implement me - 11/4 @jonathanloganmoran
	    // check if any runningThreads have cached priorities
	    //   if none are cached, request priority
	    //   else if check if priorityFlag is true
	    //     update priority for all threads in runningQueue list
	    //     set priorityFlag = false;
	    //   else return cached priority
	    return priority;
	}

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    if (this.priority == priority)
		return;
	    
	    this.priority = priority;
	    
	    // implement me - 11/4 @jonathanloganmoran
	    // reset all cached priorities in waitingQueue
	}

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
	    // implement me - 11/4 @jonathanloganmoran
	    // add to thread's runningQueue
	    // remove from thread's waitingQueue
	    // call update to cached priority
	}

	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(PriorityQueue waitQueue) {
	    // implement me - 11/4 @jonathanloganmoran
	    // add to thread's waitingQueue
	    // remove from threads runningQueue
	    
	}
	// NEW T1.5: thread will release waitLock
	public void release(PriorityQueue waitQueue) {
	    // remove waitQueue from this thread's allocated resources
	    // call update to cached priority value
	}	

	/** The thread with which this object is associated. */	   
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority;
	
	// NEW T1.5: cache thread's priority
	protected int effectivePriority = priorityMinimum;
	
	// NEW T1.5: handle priority updates when ThreadState changes
	protected boolean priorityFlag = false;
	
	// NEW T1.5: thread's allocated resources
	protected SynchList runningQueue;
	protected SynchList waitingQueue;
	
	// NEW T1.5: thread's 
    }
}
