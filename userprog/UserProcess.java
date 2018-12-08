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
    //NEW T2.2: Support for fragmentation in physical memory (allocate child)
    private UThread thread;					// thread associated with running process
    private int process_id;					// unique id of process
    public int status;						// flag for execution + termination handling
    private int parent_pid;					// unique id of parent process
    
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
    public UserProcess() {
	status = START;						// init to EXIT code
	process_id = UserKernel.getNextProcessId();
	UserKernel.getProcess(this, process_id);		// put null instance into process map
	
	for(int i = 0; i < MAX_FILES; i++) {
	    process_fd[i] = new FileDescriptor();
	}
	
	process_fd[0].file = UserKernel.console.openForReading();
	Lib.assertTrue(process_fd[0] != null);
	
	process_fd[1].file = UserKernel.console.openForWriting();
	Lib.assertTrue(process_fd[1] != null);
	
	process_id = UserKernel.getNextProcessId();
	
	UserKernel.getProcess(this, process_id);
	
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
	thread = new UThread(this);
	thread.setName(name).fork();

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

	if(vpn < 0 || vpn >= memory.length) {					// check if valid
	    return 0;
	}

	if(vpn >= numPages) {							// invalid size (non-contiguous)
	    System.out.print("Invalid page entry: " + vpn);
	    return -1;								// CC 2.1: DO NOT DESTROY CURR PROCESS
	}
	
	// NEW T2.2: Create new virtual-to-physical page translation instance
	TranslationEntry newPage = pageTable[vpn];   

	if (!pageTable[vpn].valid) { return -1; }				// CC 2.1: ignore invalid entries
	// if(newPage.readOnly) { return -1; }					// CC 2.1: return error if read-only entry
	
	// NEW T2.2: Handle policy flags
	newPage.used = true;							// flag page as read
	// newPage.dirty = true;						// flag page dirty on read -- second chance policy
	
	// NEW T2.2: check if translation out of bounds
	int curr_ppn = newPage.ppn;
	
	// int curr_paddr = (curr_ppn*pageSize) + voffset;
	if(curr_ppn < 0 || curr_ppn >= Machine.processor().getNumPhysPages()) {	
	    return 0;								// return 0 bytes transferred
	}
	
	int next_npaddr = (newPage.ppn + 1)*pageSize;				// fetch addr of next page table entry
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
public int writeVirtualMemory(int vaddr, byte[] data, int offset,
	int length) {
    Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

    // NEW T2.2: Create a new user process reference
    byte[] memory = Machine.processor().getMemory();
    
    // NEW T2.2: Handle the virtual-to-physical page address translation
    int vpn = Processor.pageFromAddress(vaddr);					// store the pte of the curr byte: (vaddr - bytesRead)/pageSize
    int voffset = Processor.offsetFromAddress(vaddr);				// store offset of user process: (vaddr + bytesRead)%pageSize

    //    // for now, just assume that virtual addresses equal physical addresses
    //    if (vaddr < 0 || vaddr >= memory.length)
    //	return 0;

    if(vpn >= numPages) {
	return -1;								// CC T2.2: Return error, do not destroy
    }
    // NEW T2.2: Create new virtual-to-physical page translation instance
    TranslationEntry newPage = pageTable[vpn];   

    if(newPage.readOnly) { return -1; }						// CC T2.2: return error if read-only entry

    // NEW T2.2: Use state variables to handle replacement policy
    newPage.used = true;							// on visit, flag true
    newPage.dirty = true;							// on write, flag true

    // NEW T2.2: check if translation out of bounds
    int curr_ppn = newPage.ppn;
    if(curr_ppn < 0 || curr_ppn >= Machine.processor().getNumPhysPages()) {	
	return 0;								// 0 bytes transferred
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
    // NEW T2.2: Load (allocate) pageTable entries 
    pageTable = new TranslationEntry[numPages];
    for(int i = 0; i < numPages; i++) {
	int pp = UserKernel.getNextProcessId();
	if(pp < 0) {
	    for(int j = 0; j < i; j++) {
		// NEW T2.2: Deallocate valid virtual pages
		if(pageTable[j].valid) {
		    UserKernel.returnFreePage(pageTable[j].ppn);
		    pageTable[j].valid = false;					// flag for removal	
		}
	    }
	    coff.close();
	    return false;
	}
	pageTable[i] = new TranslationEntry(i, pp, true, false, false, false);
    }

    // load sections
    for (int s=0; s<coff.getNumSections(); s++) {
	CoffSection section = coff.getSection(s);

	Lib.debug(dbgProcess, "\tinitializing " + section.getName()
	+ " section (" + section.getLength() + " pages)");

	for (int i=0; i<section.getLength(); i++) {
	    int vpn = section.getFirstVPN()+i;

	    // for now, just assume virtual addresses=physical addresses
	    section.loadPage(i, vpn);
	}
    }

    return true;
}

private int handleCreat(int file1) {
    int descriptor = -1;		 								// Default the description to -1
    int i;														// To increment through file indexes
    int j = 0;													// To increment through creatArray[]
    int creatArray[] = new int[255];							// New array to hold all null values
    String fileName = readVirtualMemoryString(file1, MAX_BYTES);	// File to create

    /* 
     * Parse through the file to find which file indexes are null
     */
    Lib.debug(dbgProcess, "handleCreat: Going through the file...");
    for (i = 0; i < this.file.length; i++){
	if (file[i] == null){
	    descriptor = i;										// Set descriptor to value of null index
	    creatArray[j] = i;									// Keep track of all index values that are null in this array
	    j++;												// Increment to next creatArray[] index
	}
    }

    /*
     * Debug process to make sure which values are null in the file index
     */
    Lib.debug(dbgProcess, "handleCreat: Checking in array which descriptor values are null:");
    for(i=0; i < creatArray.length; i++) {
	Lib.debug(dbgProcess, "handleCreat: Value of creatArray[" + i + "] is: " + creatArray[i]);
    }

    /*
     * If file descriptor was not created and the previous for loop did not work right, return an error
     */
    if (descriptor == -1){
	Lib.debug(dbgProcess,"handleCreat: Error, did not create a file descriptor!");
	return -1;
    }


    /*
     * If file is not valid or does not exist, return an error
     */
    if (fileName == null || fileName.length() == 0){
	Lib.debug(dbgProcess, "handleCreat: Error, invalid file.");
	return -1;
    }

    /*
     * Open the file 'fileName' from the kernel
     */
    Lib.debug(dbgProcess, "");
    OpenFile fileOpen = UserKernel.fileSystem.open(fileName, true);

    /*
     * If file is empty, return an error
     */
    if (fileOpen == null){
	Lib.debug(dbgProcess, "handleCreat: Error, file is empty.");
	return -1;
    }

    Lib.debug(dbgProcess, "handleCreat: Success! This file is now created.");
    this.file[descriptor] = fileOpen;							// Open the file created

    return descriptor;											// Returning that this file is now open
}

protected void unloadSections() {
    //close the coff file
    coff.close();
    //empty out page tables(Kernel and Process)
    for(int i = 0; i < numPages; i++){
        UserKernel.freeUpPage(pageTable[i].ppn);
        pageTable[i] = null;
    }
}



/*
Handle the open() system call.
 */
private int handleOpen(int file2) {
    int descriptor = -1;		 								// Default the description to -1
    int i;														// To increment through file indexes
    int j = 0;													// To increment through openArray[]
    int openArray[] = new int[255];								// New array to hold all null values
    String filename = readVirtualMemoryString(file2, MAX_BYTES);	// New file to open

    /*
     * Parse through the file to find which index values are null
     */
    Lib.debug(dbgProcess, "handleOpen: Checking which index values are null:");
    for (i = 0; i < this.file.length; i++){
	if (file[i] == null){
	    descriptor = i;
	    openArray[j] = i;
	    j++;
	}
    }

    /*
     * Debug process to make sure which values are null in the file index
     */
    Lib.debug(dbgProcess, "handleOpen: Checking in array which descriptor values are null:");
    for(i=0; i < openArray.length; i++) {
	Lib.debug(dbgProcess, "handleOpen: Value of openArray[" + i + "] is: " + openArray[i]);
    }

    /*
     * If nothing written to descriptor, return an error
     */
    if (descriptor == -1){
	Lib.debug(dbgProcess, "handleOpen: Error: Nothing written to descriptor.");
	return -1;
    }

    /*
     * If invalid file name, return an error
     */
    if (filename == null || filename.length() == 0){
	Lib.debug(dbgProcess, "handleOpen: Error: Invalid filename");
	return -1;
    }

    /*
     * Removing the last open file from the kernel
     */
    OpenFile fileOpen = UserKernel.fileSystem.open(filename, false);

    /*
     * If file is empty, return an error
     */
    if (fileOpen == null){
	Lib.debug(dbgProcess, "handleOpen: Error: No file open");
	return -1;
    }

    Lib.debug(dbgProcess, "handleOpen: Success! File is opened.");
    this.file[descriptor] = fileOpen;							// Open the file

    return descriptor;											// Return this file as the current open file
}

private int handleRead(int a, int b, int c) {
    int descriptor = a;											// For this file descriptor
    int buff = b;												// For the buffer
    int count = c;												// For the counter
    byte[] testBuff = new byte[1];
    int readByte = 0;											// Counter for bytes read
    int read;

    /*
     * If we have a negative count, return an error
     */
    if(count < 0) {
	Lib.debug(dbgProcess, "handleRead: Error, negative counter.");
	return -1;
    }

    /*
     * Check that the buffer is valid before continuing
     */
    if(readVirtualMemory(buff,testBuff,0,1) != 1) {
	Lib.debug(dbgProcess, "handleRead: Error: Buffer invalid");
	return -1;
    }

    /*
     * If descriptor is outside of byte range, return an error
     */
    if(descriptor < 0 || descriptor > 15) {
	Lib.debug(dbgProcess, "handleRead: Error: Descriptor out of byte range");
	return -1;
    }

    /*
     * If no file descriptor, return an error
     */
    if(file[descriptor] == null) {
	Lib.debug(dbgProcess, "handleRead: Error: No file descriptor given");
	return -1;
    }

    byte[] byteCount = new byte[count];							// New byte for count value

    /*
     * While there's still bytes to read, get the file and read to a new array
     */
    while(readByte < count) {
	read = file[a].read(byteCount, readByte, count - readByte);
	if(read < 0) {											// If negative value read, return error
	    Lib.debug(dbgProcess, "handleRead: Error: Negative value read");
	    return -1;
	}
	readByte += read;										// Increment bytes read counter
    }

    /*
     * If bytes read aren't written to the virtual memory, return an error
     */
    if(writeVirtualMemory(buff, byteCount, 0, readByte) != readByte) {
	Lib.debug(dbgProcess, "handleRead: Error: Virtual memory not equal to the bytes read");
	return -1;
    }

    Lib.debug(dbgProcess, "handleRead: Success!");
    return readByte;
}

private int handleWrite(int a, int b, int c) {
    //		Lib.debug(dbgProcess, "");
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

    /*
     * Checks that the validity of buffer pointer before continuing
     */
    Lib.debug(dbgProcess, "Testing if our buffer pointer is valid.");
    if(readVirtualMemory(buffPt, buffTest, 0, 1) != 1) {		// Compare buffer pointer to the buffer test variable
	return -1;												// If not equal, then False
    }

    /*
     * While there's still buffer space remaining, write to reader.
     */
    Lib.debug(dbgProcess, "Filling up reader to buffer size");
    while(reader < buffSize) {
	reader += numRead;										// Increments reader until it reaches [buffSize] - 1

	/*
	 * If zero counter has increased at some prior point, reset to zero here as we have a success.
	 */
	if(zeroCtr > 0) {
	    Lib.debug(dbgProcess, "handleWrite: Success, reseting zero counter to 0.");
	    zeroCtr = 0;
	}
	/*
	 * If zero counter has increased 3 or more times, return an error; something has gone wrong
	 */
	if (numRead == 0) {
	    if (++zeroCtr > 2) {
		Lib.debug(dbgProcess, "handleWrite: Zero Counter has incremented 3 times. There is an error.");
		return -1;
	    }
	}
    }

    /*
     * If there's an actual valid file open, then
     * while there's still buffer to write to the open file
     */
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
    return dataWrite;											// Save data written
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

    //Initializes a string that will take in the name of the file
    String nameOfFile = readVirtualMemoryString(file1, MAX_BYTES);		

    if (nameOfFile == null) {
	Lib.debug(dbgProcess, "The name of the file is invalid!");
	return -1;
    }

    //Next, create a string that will take in the extension of the file to check if it is
    //a valid .coff file
    String extension = nameOfFile.substring(nameOfFile.length() - 4, nameOfFile.length());		//Creates a substring for the name of the file extension
    if(!extension.equals(".coff")) {
	Lib.debug(dbgProcess, "The file is not a valid .coff file!");
	return -1;
    }
    //----------------------------------------------------------------------------------------------------------------
    String arrArg[] = new String[argc];		//allocate a new array of strings populated of amount argc
    byte check[] = new byte[4];				//The memory allocation must take into considereation of a size 4 bytes
    for(int i = 0; i < argc; i++){			//Cycles through the amount of arguments argc
	int argBytes = readVirtualMemory(argv+i*4, check);
	if(argBytes != 4)
	    return -1;

	int addressOfArgc = Lib.bytesToInt(check, 0);
	arrArg[i] = readVirtualMemoryString(addressOfArgc, MAX_BYTES);
    }

    ///---------------------------------------------------------------------------------------------------------------
    //uses another process derived from userprocess 
    UserProcess anotherProcess = UserProcess.newUserProcess();
    
    anotherProcess.parent_pid = this.process_id;
    this.childPT.add(anotherProcess.process_id);

    boolean runExecute = anotherProcess.execute(nameOfFile, arrArg);

    if(runExecute) {
	return anotherProcess.process_id;
    }
    else {
	Lib.debug(dbgProcess, "The address is invalid!");
	return -1;			
    }

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
     */
    if(fileDescriptor < 0)												// Check fileDescriptor is within bounds of 1-16 in size
	return -1;														// Error if less than 0
    if(fileDescriptor > 15)
	return -1;														// Error if greater than 15

    OpenFile closingFile;
    closingFile = this.file[fileDescriptor];						// File to be closed

    Lib.debug(dbgProcess, "handleClose: Checking if the file being closed is valid");
    if(closingFile == null || closingFile.length() < 0)					// If there's no file or data stored, return an error
	return -1;

    closingFile.close();

    Lib.debug(dbgProcess, "handleClose: Checking to see if file is closed yet:");
    if(closingFile.length() != -1)
	return -1;

    this.file[fileDescriptor] = null;								// Free up the file descriptor

    Lib.debug(dbgProcess, "handleClose: File was closed successfully");
    return 0;															// Return success
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

private void handleExit(int status){

    for(int i = 0; i < MAX_FILES; i++) {
	if(process_fd[i].file != null) {
	    handleClose(i);
	}
    }

    //nullify all children's parents
    while(childPT != null && !childPT.isEmpty()){
	int child_ptr = childPT.removeFirst();
	UserProcess child_rm = UserKernel.getProcessById(child_ptr);
	
        child_rm.parent_pid = 1;			//set the root to 1
    }
    //release resources allocated by pages since they shouldn't be in  use anymore
    this.unloadSections();

    if(this.process_id == 1) {
	Kernel.kernel.terminate();                      // terminate this kernel
    }
    
    else {						//we are the root parent. I am the Terminator
	// check if current KThread is UThread instance
	Lib.assertTrue(KThread.currentThread() == this.thread);
	KThread.finish();
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

/**
 * Handle the halt() system call. 
 */
private int handleHalt() {

    Machine.halt();

    Lib.assertNotReached("Machine.halt() did not halt machine!");
    return 0;
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
	return handleJoin(a0, a1);                  

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

/**
 * Handle an attempt to join a current thread by a child process's thread. 
 * Called by <tt>UserKernel.exceptionHandler()</tt>. 
 * 
 * If process is a child process, remove the child from childPT list
 *   Else not a child, @return -1
 * If the child process finished after call (null) @return -2
 * Else attempt to join child process to current thread
 * Remove child from kernel
 * Write the p_status to virtual memory 
 * 
 * @param	child_pid	the process id of the child thread
 * @param	p_status	the 4-byte exit status
 */
private int handleJoin(int child_pid, int p_status) {
    
    boolean isChild = false;				// return true if child process of curr thread
    int temp;						// allocate byte to store status
    Iterator<Integer> it = this.childPT.iterator();	
    
    while(it.hasNext()) {				// iterate over child process list
	temp = it.next();
	
	if(temp == child_pid) {
	    it.remove();
	    isChild = true;
	    break;					// found a child process, done
	}
    }
    if(isChild == false) {				// no children in childPT list
	return -1;					// return error code
    }
    
    UserProcess child = UserKernel.getProcessById(child_pid);
    
    if(child == null) {					// dead on arrival
	return -2;					// already joined
    }
    
    // TO DO T2.3: Attempt to join child to thread
    child.thread.join();
    
    // Notes: need exit status var, newUserProcess(), execute(), bytesFromInt()
    byte temp_arr[] = new byte[4];
    
    temp_arr = Lib.bytesFromInt(child.status);
    int count = writeVirtualMemory(p_status, temp_arr);
    
    if(count != 4) {
	return 1;
    }
    else { 
	return 0;
    } 
}

/** Find the first empty position */
private int findEmptyFileDescriptor() {
    for(int i = 0; i < MAX_FILES; i++) {
	if(process_fd[i].file == null) {
	    return i;					// index of null byte
	}
    }
    return -1;						// error, no free space
}


/** Find the first position matching @param filename */
private int fileFileDescriptorByName(String filename) {
    for(int i = 0; i < MAX_FILES; i++) {
	if(process_fd[i].filename.equals(filename)) {
	    return i;					// matches desired filename 
	}
    }
    return -1;						// error, no matching file in FD array
}

/** The program being run by this process. */
protected Coff coff;

//NEW VARIABLES
public static final int MAX_FILES = 16;								// Maximum number of opened files per process = 16			

public OpenFile[] file;

public class FileDescriptor {
    public FileDescriptor() { }
    	private String filename = "";
    	private OpenFile file = null;
    	private boolean file_rm = false;
}

private FileDescriptor process_fd[] = new FileDescriptor[MAX_FILES];

private static final int MAX_BYTES = 256;

/** This process's page table. */
protected TranslationEntry[] pageTable;

/** This process's children */
private LinkedList<Integer> childPT = new LinkedList<Integer>();

/** The EXIT flag stored as an int */
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
