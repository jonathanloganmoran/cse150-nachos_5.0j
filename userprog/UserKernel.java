package nachos.userprog;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
    /**
     * Allocate a new user kernel.
     */
    public UserKernel() {
	super();
    }
    
    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    
    // NEW P2: Create space for free page table
    public void initialize(String[] args) {
	// Get the number of the machines physical pages
	int numPhysPages = Machine.processor().getNumPhysPages();
		
	// Initialize the free page list and fill with Machine.numPhysPages number of pages
	freePageList = new LinkedList<Integer>();
	for(int i = 0; i < numPhysPages; i++) {
		freePageList.add(i);
	}
	// Initialize the free page list lock
	freePageListLock = new Lock();
	
	super.initialize(args);

	console = new SynchConsole(Machine.console());
	
	Machine.processor().setExceptionHandler(new Runnable() {
		public void run() { exceptionHandler(); }
	    });
    }

    /**
    * Function returns an entry from the freePageList
    * 
    * @return free page in range from 0 to numPhysPages-1
    * If no free pages, return -1
    */
    public static int getFreePage() {
      	int freePageNumber = -1;
        	
    	// Attempt to acquire the lock in order to access the free page list
      	freePageListLock.acquire();
        	
        // Get the number for a free page, if any
        if(!freePageList.isEmpty()) {
        	freePageNumber = freePageList.removeFirst();
        }
        
        // Release the lock, waking up a process waiting to acquire this lock, if any
        freePageListLock.release();
        
        return freePageNumber;
    }
        
    /**
     * This function returns an entry back to the freePageList
     * 
     */
    public static void returnFreePage(int pageNumber) {
       	//Attempt to acquire the lock in order to access the free page list
       	freePageListLock.acquire();
       	
       	//Return the page to the free page list
       	freePageList.add(pageNumber);
       	
       	//Release the lock, waking up a process waiting to acquire this lock, if any
       	freePageListLock.release();
    }
    
    /**
     * Test the console device.
     */	
    public void selfTest() {
	super.selfTest();

	System.out.println("Testing the console device. Typed characters");
	System.out.println("will be echoed until q is typed.");

	char c;

	do {
	    c = (char) console.readByte(true);
	    console.writeByte(c);
	}
	while (c != 'q');

	System.out.println("");
    }

    /**
     * Returns the current process.
     *
     * @return	the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
	if (!(KThread.currentThread() instanceof UThread))
	    return null;
	
	return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
	Lib.assertTrue(KThread.currentThread() instanceof UThread);

	UserProcess process = ((UThread) KThread.currentThread()).process;
	int cause = Machine.processor().readRegister(Processor.regCause);
	process.handleException(cause);
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see	nachos.machine.Machine#getShellProgramName
     */
    public void run() {
	super.run();

	UserProcess process = UserProcess.newUserProcess();
	
	String shellProgram = Machine.getShellProgramName();	
	Lib.assertTrue(process.execute(shellProgram, new String[] { }));

	KThread.currentThread().finish();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
	super.terminate();
    }

    /** Globally accessible reference to the synchronized console. */
    public static SynchConsole console;
    
    // NEW P2: global free page list, protected by Lock
    public static LinkedList<Integer> freePageList;
    private static Lock freePageListLock;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;
}
