package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.io.FileDescriptor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    //NEW T2.2: Support for fragmentation in physical memory
    protected int process_id;					// unique id of this current process  
    private Semaphore joined = new Semaphore(0);		// atomically put parent thread to sleep
    public int status;						// flag for execution + termination handling
    
    /*// NEW T2.3: Support for multiprogramming
    class childProcess {
	
	UserProcess child;					// allocate fragmented user process
	
	childProcess(UserProcess up) {
	    this.child = up;
	    status = START;					// init user process to START
	    process_id = UserKernel.getFreePage();		// return next page id (non-contiguous)
	    processes.put(up, null;
	}
    }*/
    
    /** Prototype: 
     * public TranslationEntry(int vpn, int ppn, boolean valid, 
     *     boolean readOnly, boolean used, boolean dirty) { 
     */
    
    public UserProcess() {
	// allocate a new process_id to the current
	UserKernel.process_idLock.P();
	//process_id = UserKernel.getNextProcessId();
	process_id = UserKernel.next_pid;			// store the global next free process_id
	UserKernel.next_pid++;					// increment next available pid
	UserKernel.process_idLock.V();
		
	int numPages = Machine.processor().getNumPhysPages();
	pageTable = new TranslationEntry[numPages];
	
	for(int i = 0; i < numPages; i++) {			// initialize pageTable with TranslationEntry instances
	    pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
	    // contiguous allocation where spn = ppn = index of pte
	}
	
	file = new OpenFile[MAX_FILES];
	file[0] = UserKernel.console.openForReading();
	file[1] = UserKernel.console.openForWriting();
    }

    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;

	// NEW T2.2: Initialize new UThread instance
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }
    
    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {



	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {

	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();			// returns reference to physical memory

	// NEW T2.2: Handle the virtual-to-physical page address translation
	int vpn = Processor.pageFromAddress(vaddr);				// store the pte of the curr byte: (vaddr - bytesRead)/pageSize
	int voffset = Processor.offsetFromAddress(vaddr);			// store offset of user process: (vaddr + bytesRead)%pageSize
	
	// NEW T2.2: Create new virtual-to-physical page translation instance
	TranslationEntry newPage = pageTable[vpn];   
	// NEW T2.2: Handle policy flags
	newPage.used = true;							// flag page as read
	int ppn_addr = newPage.ppn*pageSize + voffset;

	if(ppn_addr < 0 || ppn_addr >= memory.length) {					// check if valid
	    return 0;
	}

	if(vpn >= numPages) {							// invalid size (non-contiguous)
	    System.out.print("Invalid page entry: " + vpn);
	    return -1;								// CC 2.1: DO NOT DESTROY CURR PROCESS
	}
	
	if (!pageTable[vpn].valid) { return -1; }				// CC 2.1: ignore invalid entries
	// if(newPage.readOnly) { return -1; }					// CC 2.1: return error if read-only entry
	// newPage.dirty = true;
	// flag page dirty on read -- second chance policy
	
	
	/** write @param amount of bytes from @param vaddr 
	  * into byte @param data array starting at @param offset
	  */
	int next_npaddr = (newPage.ppn)*pageSize;				// fetch addr of next page table entry
	int paddr = next_npaddr + voffset; 					// store offset next page table entry
	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(memory, paddr, data, offset, amount);			// move to physical memory
	
	return amount;
    }

/**
 * Transfer all data from the specified array to this process's virtual
 * memory.
 * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
 *
 * @param	vaddr	the first byte of virtual memory to write.
 * @param	data	the array containing the data to transfer.
 * @return	the number of bytes successfully transferred.
 */
public int writeVirtualMemory(int vaddr, byte[] data) {
    return writeVirtualMemory(vaddr, data, 0, data.length);
}

/**
 * Transfer data from the specified array to this process's virtual memory.
 * This method handles address translation details. This method must
 * <i>not</i> destroy the current process if an error occurs, but instead
 * should return the number of bytes successfully copied (or zero if no
 * data could be copied).
 *
 * @param	vaddr	the first byte of virtual memory to write.
 * @param	data	the array containing the data to transfer.
 * @param	offset	the first byte to transfer from the array.
 * @param	length	the number of bytes to transfer from the array to
 *			virtual memory.
 * @return	the number of bytes successfully transferred.
 */
public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

    // NEW T2.2: Create a new user process reference
    byte[] memory = Machine.processor().getMemory();
    
    // NEW T2.2: Handle the virtual-to-physical page address translation
    int vpn = Processor.pageFromAddress(vaddr);					// store the pte of the curr byte: (vaddr - bytesRead)/pageSize
    int voffset = Processor.offsetFromAddress(vaddr);				// store offset of user process: (vaddr + bytesRead)%pageSize

    /*if(vpn >= numPages) {
	return -1;								// CC T2.2: Return error, do not destroy
    }*/
    
    // NEW T2.2: Create new virtual-to-physical page translation instance
    TranslationEntry newPage = pageTable[vpn];
    // NEW T2.2: Use state variables to handle replacement policy
    newPage.used = true;							// on visit, flag true
    // newPage.dirty = true;							// on write, flag true
    int ppn_addr = newPage.ppn*pageSize + voffset;
    
    if(newPage.readOnly || !newPage.valid) { return -1; }						// CC T2.2: return error if read-only entry

    if(ppn_addr < 0 || ppn_addr >= memory.length) {				// CC T2.2: no free space available
	return 0;								// zero bytes transferred
    }

    int amount = Math.min(length, memory.length-vaddr);
    System.arraycopy(data, offset, memory, vaddr, amount);

    return amount;
}

/**
 * Load the executable with the specified name into this process, and
 * prepare to pass it the specified arguments. Opens the executable, reads
 * its header information, and copies sections and arguments into this
 * process's virtual memory.
 *
 * @param	name	the name of the file containing the executable.
 * @param	args	the arguments to pass to the executable.
 * @return	<tt>true</tt> if the executable was successfully loaded.
 */
private boolean load(String name, String[] args) {
    Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

    OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
    if (executable == null) {
	Lib.debug(dbgProcess, "\topen failed");
	return false;
    }

    try {
	coff = new Coff(executable);
    }
    catch (EOFException e) {
	executable.close();
	Lib.debug(dbgProcess, "\tcoff load failed");
	return false;
    }

    // make sure the sections are contiguous and start at page 0
    numPages = 0;
    for (int s=0; s<coff.getNumSections(); s++) {
	CoffSection section = coff.getSection(s);
	if (section.getFirstVPN() != numPages) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tfragmented executable");
	    return false;
	}
	numPages += section.getLength();
    }
    
    // NEW P2: store starting index of user process
    int toFill = numPages;						// starting index of freePageList
    
    // make sure the argv array will fit in one page
    byte[][] argv = new byte[args.length][];
    int argsSize = 0;
    for (int i=0; i<args.length; i++) {
	argv[i] = args[i].getBytes();
	// 4 bytes for argv[] pointer; then string plus one for null byte
	argsSize += 4 + argv[i].length + 1;
    }
    if (argsSize > pageSize) {
	coff.close();
	Lib.debug(dbgProcess, "\targuments too long");
	return false;
    }

    // program counter initially points at the program entry point
    initialPC = coff.getEntryPoint();	

    // next comes the stack; stack pointer initially points to top of it
    numPages += stackPages;
    initialSP = numPages*pageSize;

    // and finally reserve 1 page for arguments
    numPages++;

    int endFill = toFill + stackPages + 1;
    
    // NEW P2: initialize freePageList with TranslationEntry instances
    for(int i = toFill; i < endFill; i++) {
	TranslationEntry newPage = pageTable[i];
	
	UserKernel.freePagesLock.P();
	int free_ppn = UserKernel.getFreePageList().removeFirst();
	UserKernel.freePagesLock.V();
	
	// NEW P2: handle process flags
	newPage.ppn = free_ppn;						// set ppn to freePagesList empty index
	newPage.valid = true;						// mark ready to write
    }
    
    if (!loadSections())
	return false;

    // store arguments in last page
    int entryOffset = (numPages-1)*pageSize;
    int stringOffset = entryOffset + args.length*4;

    this.argc = args.length;
    this.argv = entryOffset;

    for (int i=0; i<argv.length; i++) {
	byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	entryOffset += 4;
	Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		argv[i].length);
	stringOffset += argv[i].length;
	Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	stringOffset += 1;
    }

    return true;
}

/**
 * Allocates memory for this process, and loads the COFF sections into
 * memory. If this returns successfully, the process will definitely be
 * run (this is the last step in process initialization that can fail).
 *
 * @return	<tt>true</tt> if the sections were successfully loaded.
 */
protected boolean loadSections() {
    if (numPages > Machine.processor().getNumPhysPages()) {
	coff.close();
	Lib.debug(dbgProcess, "\tinsufficient physical memory");
	return false;
    }
    // NEW T2.2: Load CoffSection entries 
    for(int s = 0; s < coff.getNumSections(); s++) {
	CoffSection section = coff.getSection(s);
	
	// NEW T2.2: Allocate section length in virtual memory
	for(int i = 0; i < section.getLength(); i++) {
	    
	    int vpn = section.getFirstVPN() + i;
	    if(vpn == -1) {							// insufficient physical memory
		coff.close();
		return false;
	    }
	    TranslationEntry freePage = pageTable[vpn];
	    
	    UserKernel.freePagesLock.P();					// acquire lock to handle atomically
	    int free_ppn = UserKernel.getFreePageList().removeFirst();
	    UserKernel.freePagesLock.V();					// free lock after ppn grab
	    
	    freePage.ppn = free_ppn;						// set free page number to empty index
	    freePage.valid = true;						// mark as valid entry
	    freePage.readOnly = section.isReadOnly();				// check if section is flagged as 'readOnly'
	    
	    section.loadPage(i, freePage.ppn);   				// load physical pages: spn = section[i], ppn = vpn
	}
    }
    return true;
}

/**
 * Release any resources allocated by <tt>loadSections()</tt>.
 */
protected void unloadSections() {
    
    //empty out page tables
    for(int i = 0; i < pageTable.length; i++) {
	TranslationEntry entry = pageTable[i];
	
	// check if valid before freeing
	if(entry.valid) {
	    UserKernel.freePagesLock.P();					// acquire lock atomically
	    UserKernel.getFreePageList().add(entry.ppn);			// put entry ppn into freePageList
	    UserKernel.freePagesLock.V();					// release lock and continue
	}
    }
}

/**
 * Initialize the processor's registers in preparation for running the
 * program loaded into this process. Set the PC register to point at the
 * start function, set the stack pointer register to point at the top of
 * the stack, set the A0 and A1 registers to argc and argv, respectively,
 * and initialize all other registers to 0.
 */
public void initRegisters() {
    Processor processor = Machine.processor();

    // by default, everything's 0
    for (int i=0; i< processor.numUserRegisters; i++)
	processor.writeRegister(i, 0);

    // initialize PC and SP according
    processor.writeRegister(Processor.regPC, initialPC);
    processor.writeRegister(Processor.regSP, initialSP);

    // initialize the first two argument registers to argc and argv
    processor.writeRegister(Processor.regA0, argc);
    processor.writeRegister(Processor.regA1, argv);
}

private int handleCreat(int file1) {
    String fileName = readVirtualMemoryString(file1, MAX_BYTES);			// File to create
    OpenFile opened_file = Machine.stubFileSystem().open(fileName, true);
    
    /* 
     * Parse through the file to find which file indexes are null
     */
    Lib.debug(dbgProcess, "handleCreat: Going through the file...");
    for (int i = 0; i < file.length; i++){
	if (file[i] == null) {
	    file[i] = opened_file;							// Keep track of all index values that are null in this array
	    return i;									// Increment to next descriptor index
	}
    }
    return -1;										// ERROR
}
/*
Handle the open() system call.
 */
private int handleOpen(int file2) {
    //int descriptor = -1;		 						// Default the description to -1
    int i;										// To increment through file indexes

    String filename = readVirtualMemoryString(file2, MAX_BYTES);			// New file to open
    OpenFile opened_file = Machine.stubFileSystem().open(filename, false);
   

     /** Parse through the file to find which index values are null
      */
    Lib.debug(dbgProcess, "handleOpen: Checking which index values are null:");
    if(opened_file != null) {
	for (i = 0; i < this.file.length; i++){
	    if (file[i] == null){
		// descriptor = i;
		// openArray[j] = i;
		// j++;
		file[i] = opened_file;
		return i;
	    }
	}
    }
    return -1;
}

private int handleRead(int a, int b, int c) {
    OpenFile opened_file = file[a];							// file descriptor = a
    if(opened_file == null) { return -1; }						// invalid open
    
    byte[] buff = new byte[c];								// buff length = c
    int bytesRead;									// counter if successful
    
    bytesRead = opened_file.read(buff, 0, c);						// read in file, offset = 0
    writeVirtualMemory(b, buff);							// write to buffer `b`
    
    return bytesRead;
    
    /*int descriptor = a;								// For this file descriptor
    int buff = b;									// For the buffer
    int count = c;									// For the counter
    byte[] testBuff = new byte[1];
    int readByte = 0;									// Counter for bytes read
    int read;

    
     * If we have a negative count, return an error
     
    if(count < 0) {
	Lib.debug(dbgProcess, "handleRead: Error, negative counter.");
	return -1;
    }

    
     * Check that the buffer is valid before continuing
     
    if(readVirtualMemory(buff,testBuff,0,1) != 1) {
	Lib.debug(dbgProcess, "handleRead: Error: Buffer invalid");
	return -1;
    }

    
     * If descriptor is outside of byte range, return an error
     
    if(descriptor < 0 || descriptor > 15) {
	Lib.debug(dbgProcess, "handleRead: Error: Descriptor out of byte range");
	return -1;
    }

    
     * If no file descriptor, return an error
     
    if(file[descriptor] == null) {
	Lib.debug(dbgProcess, "handleRead: Error: No file descriptor given");
	return -1;
    }

    byte[] byteCount = new byte[count];							// New byte for count value

    
     * While there's still bytes to read, get the file and read to a new array
     
    while(readByte < count) {
	read = file[a].read(byteCount, readByte, count - readByte);
	if(read < 0) {											// If negative value read, return error
	    Lib.debug(dbgProcess, "handleRead: Error: Negative value read");
	    return -1;
	}
	readByte += read;										// Increment bytes read counter
    }

    
     * If bytes read aren't written to the virtual memory, return an error
     
    if(writeVirtualMemory(buff, byteCount, 0, readByte) != readByte) {
	Lib.debug(dbgProcess, "handleRead: Error: Virtual memory not equal to the bytes read");
	return -1;
    }

    Lib.debug(dbgProcess, "handleRead: Success!");
    return readByte;*/
}

/**
 * Handle write() system call.
 * Reads in file from buffer
 * Writes into new file using temp buffer
 * @param    a     is the file descriptor
 * @param    b     is the buffer to write
 * @param    c     is the size of desired buffer
 * 
 * @return   bW    size of successfully written buffer
 */
private int handleWrite(int a, int b, int c) {
    OpenFile opened_file = file[a];
    
    if(opened_file == null) { return -1; }
    byte[] buff = new byte[c];
    
    readVirtualMemory(b, buff);
    int bW = opened_file.write(buff, 0, c);
    
    return bW;
    
/*    //		Lib.debug(dbgProcess, "");
    int fileDes = a;											// File Descriptor
    int buffPt = b;												// Buffer pointer
    int buffSize = c;											// Buffer Size
    int zeroCtr = 0;											// Counts zero values while we fill the buffer
    int reader = 0;												// Holds max of buffer size
    int dataWrite = 0;											// Holds data to write to the buffer
    int countStuck = 0;											// Counts when the program gets stuck attempting to write

    byte[] buffTest = new byte[1];								// Variable to test if the buffer pointer is accurate
    byte[] buff = new byte[buffSize];							// Create new buffer of size of buffer size

    int numRead = readVirtualMemory(buffPt, buff, reader, buffSize - reader);

    if(buffSize < 0) {											// Return false if negative buffer size
	return -1;
    }

    
     * Checks that the validity of buffer pointer before continuing
     
    Lib.debug(dbgProcess, "Testing if our buffer pointer is valid.");
    if(readVirtualMemory(buffPt, buffTest, 0, 1) != 1) {		// Compare buffer pointer to the buffer test variable
	return -1;												// If not equal, then False
    }

    
     * While there's still buffer space remaining, write to reader.
     
    Lib.debug(dbgProcess, "Filling up reader to buffer size");
    while(reader < buffSize) {
	reader += numRead;										// Increments reader until it reaches [buffSize] - 1

	
	 * If zero counter has increased at some prior point, reset to zero here as we have a success.
	 
	if(zeroCtr > 0) {
	    Lib.debug(dbgProcess, "handleWrite: Success, reseting zero counter to 0.");
	    zeroCtr = 0;
	}
	
	 * If zero counter has increased 3 or more times, return an error; something has gone wrong
	 
	if (numRead == 0) {
	    if (++zeroCtr > 2) {
		Lib.debug(dbgProcess, "handleWrite: Zero Counter has incremented 3 times. There is an error.");
		return -1;
	    }
	}
    }

    
     * If there's an actual valid file open, then
     * while there's still buffer to write to the open file
     
    Lib.debug(dbgProcess, "handleWrite: Checking if we have a valid file open and buffer to use.");
    if(fileDes > -1 && file[fileDes] != null && fileDes < file.length) {
	while(dataWrite < buffSize && countStuck < 3) {			// If stuck counter gets to 3 or larger, stop attempting to write to open file
	    int newWriter = file[fileDes].write(buff, 		// Write to the current open file, save in newWriter to preserve dataWrite
		    dataWrite, buffSize - dataWrite);			
	    dataWrite += newWriter;								// Add values of newWriter to dataWrite

	    Lib.debug(dbgProcess, "handleWrite: Checking if counter is stuck");
	    if(newWriter == 0) {								// If nothing written to newWriters, increment stuck counter
		countStuck++;
	    }

	    Lib.debug(dbgProcess, "handleWrite: Checking newWriter variable validity");
	    if(newWriter == 1) {								// If newWriter variable is 1, return error
		return -1;
	    }
	}

	Lib.debug(dbgProcess, "handleWrite: Checking if stuck counter has reached 3. Terminating program if so.");
	if(countStuck > 2) {									// Once stuck counter reaches 3 or larger, end while loop and return data written
	    return dataWrite;
	}
    }
    else {														// Return an error if no valid file
	return -1;
    }

    Lib.debug(dbgProcess, "handleWrite: Write successful! Finalizing write.");
    return dataWrite;	*/										// Save data written
}	

/**
 * Move process to child instance and executes with the given arguments 
 * @return	child_pid 	process_id of child instance 
 * 
 * @param	file1		null-terminated string specifying name of .coff executable file
 * @param	argc 		non-negative count of required processes to allocate
 * @param	argv		array of null-terminated file strings beginning at argv[0] to argv[argc-1]
 */
private int handleExec(int file1, int argc, int argv) {
    if (argc < 1) {
	Lib.debug(dbgProcess, "argc is not a positive value!: " + argc);
	return -1;
    }
    // Initialize new array to store argc processes to allocate
    String[] temp_fd = new String[argc];
    
    for(int i = 0; i < argc; i++) {
	byte[] arg_temp = new byte[4];					// size of each 4-byte address
	readVirtualMemory(argv + i*4, arg_temp);
	temp_fd[i] = readVirtualMemoryString((Lib.bytesToInt(arg_temp, 0)), MAX_BYTES);
    }
    ///---------------------------------------------------------------------------------------------------------------
    //uses another process derived from userprocess 
    UserProcess childProcess = UserProcess.newUserProcess();
    
    childPT.add(childProcess);
    String p_id = readVirtualMemoryString(file1, MAX_BYTES);		// read in process name
    boolean runExecute = childProcess.execute(p_id, temp_fd);

    if(!runExecute) {
	Lib.debug(dbgProcess, "The address is invalid!");
	return -1;
    }
    return childProcess.process_id;
}

/**
 * Close a file descriptor, so that it no longer refers to any file or stream
 * and may be reused.
 *
 * If the file descriptor refers to a file, all data written to it by write()
 * will be flushed to disk before close() returns.
 * If the file descriptor refers to a stream, all data written to it by write()
 * will eventually be flushed (unless the stream is terminated remotely), but
 * not necessarily before close() returns.
 *
 * The resources associated with the file descriptor are released. If the
 * descriptor is the last reference to a disk file which has been removed using
 * unlink, the file is deleted (this detail is handled by the file system
 * implementation).
 *
 * Returns 0 on success, or -1 if an error occurred.
 */
private int handleClose(int fileDescriptor) {
    Lib.debug(dbgProcess, "handleClose: Closing a file");
    /*
     * Check if fileDescriptor is valid before doing anything
     
    if(fileDescriptor < 0)												// Check fileDescriptor is within bounds of 1-16 in size
	return -1;														// Error if less than 0
    if(fileDescriptor > 15)
	return -1;														// Error if greater than 15
*/
    OpenFile closingFile;
    closingFile = file[fileDescriptor];						// File to be closed

    Lib.debug(dbgProcess, "handleClose: Checking if the file being closed is valid");
    if(closingFile != null) {						// If there's no file or data stored, return an error
	file[fileDescriptor].close();					// atomically close file
    	file[fileDescriptor] = null;
    	
    	return fileDescriptor;
    }
    Lib.debug(dbgProcess, "handleClose: Checking to see if file is closed yet:");
    return -1;
}

private int handleUnlink(int nameAddr) {
    String name;														// For the file name
    boolean closed;														// If the file is closed, true or false

    name = readVirtualMemoryString(nameAddr, MAX_BYTES);					// Get the address in memory for the file's name

    Lib.debug(dbgProcess, "handleUnlink: Checking if we have an accurate memory address for our file:");
    if(name.length() == 0 || name == null)
	return -1;

    Lib.debug(dbgProcess, "handleUnlink: Successfully found memory address. Removing file from memory: ");
    closed = UserKernel.fileSystem.remove(name);						// Remove the file from memory

    Lib.debug(dbgProcess, "handleUnlink: Checking if file was properly removed: ");
    if(!closed)
	return -1;														// If file wasn't removed from memory, return an error

    Lib.debug(dbgProcess, "handleUnlink: Successfully closed file and removed from memory.");
    return 0;															// Return success
}

/**
 * Handle the exit() system call
 * @return     exitFlag     flag containing exit status
 * 
 * Flush all ints out of file array, check for sleeping parent process
 * If parent thread is joined, call KThread to finish()
 * Halts if the last process to exit()
 * 
 * @param      status       exit type variable to set flag
 */
private int handleExit(int status){
    
    unloadSections();						// start flagging sections for removal
    for(int i = 0; i < file.length; i++) {
	if(file[i] != null) {
	    file[i].close();					// close file and all associated resources
	}
    }
    exitFlag = status;
    UserKernel.freePagesLock.P();
    
    if(process_id == 0) {
	Machine.halt();
    }
    KThread.finish();
    return exitFlag;
}

/**
 * Handle the halt() system call
 * Puts the waiting process to sleep
 * If child process is the current process_id
 * 
 * @return    process_id     of current sleeping parent
 */


/** 
 * Handle the halt() system call
 * 
 * @return  0    if file has not been halted
 */
private int handleHalt() {

    unloadSections();						// start flagging sections for removal
    for(int i = 0; i < file.length; i++) {
	if(file[i] != null) {
	    file[i].close();					// close file and all associated resources
	}
    }
    
    Machine.halt();
    Lib.assertNotReached("Machine.halt() did not halt machine!");
    return 0;
}

/**
 * Handle an attempt to join a current thread by a child process's thread. 
 * Called by <tt>UserKernel.exceptionHandler()</tt>. 
 * 
 * Puts the waiting process to sleep
 * If child process is the current process_id
 * 
 * @return    process_id     of current sleeping parent
 */
private int handleJoin(int child_pid) {
    
    UserProcess temp;						// allocate byte to store status
    Iterator<UserProcess> it = this.childPT.iterator();	
    
    while(it.hasNext()) {					// iterate over child process list
	temp = it.next();
	if(temp.process_id == process_id) {
	    temp.joined.P();					// acquire lock before putting to sleep
	    return temp.exitFlag;				// found a child process, done
	}
    }
    return -1;
}


private static final int
syscallHalt = 0,
syscallExit = 1,
syscallExec = 2,
syscallJoin = 3,
syscallCreate = 4,
syscallOpen = 5,
syscallRead = 6,
syscallWrite = 7,
syscallClose = 8,
syscallUnlink = 9;

/**
 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
 * <i>syscall</i> argument identifies which syscall the user executed:
 *
 * <table>
 * <tr><td>syscall#</td><td>syscall prototype</td></tr>
 * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
 * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
 * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
 * 								</tt></td></tr>
 * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
 * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
 * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
 * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
 *								</tt></td></tr>
 * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
 *								</tt></td></tr>
 * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
 * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
 * </table>
 * 
 * @param	syscall	the syscall number.
 * @param	a0	the first syscall argument.
 * @param	a1	the second syscall argument.
 * @param	a2	the third syscall argument.
 * @param	a3	the fourth syscall argument.
 * @return	the value to be returned to the user.
 */
public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
    
    switch (syscall) {

    case syscallHalt:
	return handleHalt();

    case syscallCreate:           
	return handleCreat(a0); 

    case syscallOpen:	            
	return handleOpen(a0);   

    case syscallRead:	            
	return handleRead(a0, a1, a2); 

    case syscallWrite:	           
	return handleWrite(a0, a1, a2);

    case syscallClose:	         
	return handleClose(a0);

    case syscallUnlink:
	return handleUnlink(a0);                    

    case syscallExit:                               
	handleExit(a0);                              
	Lib.assertNotReached();                    
	return 0;                                         

    case syscallExec:                                
	return handleExec(a0, a1, a2);              

    case syscallJoin:                               
	return handleJoin(a0);                  

    default:
	Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	Lib.assertNotReached("Unknown system call!");
    }
    return 0;
}

/**
 * Handle a user exception. Called by
 * <tt>UserKernel.exceptionHandler()</tt>. The
 * <i>cause</i> argument identifies which exception occurred; see the
 * <tt>Processor.exceptionZZZ</tt> constants.
 *
 * @param	cause	the user exception that occurred.
 */
public void handleException(int cause) {
    Processor processor = Machine.processor();

    switch (cause) {
    case Processor.exceptionSyscall:
	int result = handleSyscall(processor.readRegister(Processor.regV0),
		processor.readRegister(Processor.regA0),
		processor.readRegister(Processor.regA1),
		processor.readRegister(Processor.regA2),
		processor.readRegister(Processor.regA3)
		);
	processor.writeRegister(Processor.regV0, result);
	processor.advancePC();
	break;				       

    default:
	Lib.debug(dbgProcess, "Unexpected exception: " +
		Processor.exceptionNames[cause]);
	Lib.assertNotReached("Unexpected exception");
    }
}

/** Find the first empty position *//*
private int findEmptyFileDescriptor() {
    for(int i = 0; i < MAX_FILES; i++) {
	if(process_fd[i].file == null) {
	    return i;					// index of null byte
	}
    }
    return -1;						// error, no free space
}


*//** Find the first position matching @param filename *//*
private int fileFileDescriptorByName(String filename) {
    for(int i = 0; i < MAX_FILES; i++) {
	if(process_fd[i].filename.equals(filename)) {
	    return i;					// matches desired filename 
	}
    }
    return -1;						// error, no matching file in FD array
}*/

/** The program being run by this process. */
protected Coff coff;

//NEW VARIABLES
public static final int MAX_FILES = 16;								// Maximum number of opened files per process = 16			

protected OpenFile[] file;

private static final int MAX_BYTES = 256;

/** This process's page table. */
protected TranslationEntry[] pageTable;

/** This process's children */
private LinkedList<UserProcess> childPT = new LinkedList<UserProcess>();

/** The EXIT flag stored as an int */
public int exitFlag;

private static final int START = -255;

/** The number of contiguous pages occupied by the program. */
protected int numPages;

/** The number of pages in the program's stack. */
protected final int stackPages = 8;

private int initialPC, initialSP;
private int argc, argv;

private static final int pageSize = Processor.pageSize;
private static final char dbgProcess = 'a';
}
