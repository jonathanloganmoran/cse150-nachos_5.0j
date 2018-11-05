package nachos.threads;

package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    private Lock waitLock;					// Lock shared by the speaker and listener
    private static Integer noise;				// Used to record what's spoken by speakers for listeners
    private int speakerCount;					// Records number of speakers
    private int listenerCount;					// Records number of listeners
    private Condition2 listener;				// For the listener lock in public Communicator()
    private Condition2 speaker;					// For the speaker lock in public Communicator()
    private boolean wordReady;					// Records if a speaker has spoken yet

    public Communicator() {
	noise = 0;						// Initialize noise to silent
	waitLock = new Lock();					// Create lock for Communicator pair
	this.speaker = new Condition2(waitLock);		// Speaker lock -> binary semaphore
	this.listener = new Condition2(waitLock);		// Listener lock -> counting semaphore
	setListenerCount(0);					// Initialize to zero listeners
	setSpeakerCount(0);					// Initialize to zero speakers
	wordReady = false;					// No words have been spoken
    }
    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param    word    the integer to transfer.
     */
    public void speak(int word) {
	waitLock.acquire();											// Acquire the lock for the speaker
	setSpeakerCount(getSpeakerCount() + 1);			// Increase the speaker count by 1

	/* While no listeners, or while a speaker has spoken a word not yet 'listened' to */
	while(listenerCount == 0 || wordReady == true) {
	    speaker.sleep();
	}

	noise = word;												// Speaker 'speaks'
	wordReady = true;											// Record that a word has been spoken
	listener.wakeAll();											// Wake all listeners
	setSpeakerCount(getSpeakerCount() - 1);			// Decrease speaker count by 1
	waitLock.release();											// Release the lock for the speaker
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return    the integer transferred.
     */
    public int listen() {
	int listenWord;												// Variable to record the spoken word and save to this listener
	waitLock.acquire();											// Acquire the lock for the listener
	setListenerCount(getListenerCount() + 1);		// Increase the listener count by 1

	/* While there is no word spoken yet, wake all speakers and put this listener to sleep */
	while(wordReady == false) {
	    speaker.wakeAll();					// Wake up all speakers
	    listener.sleep();					// Put listener to sleep
	}

	listenWord = noise.intValue();				// Listener hears what's spoken
	wordReady = false;											// Resets to show no word available to other listeners
	setListenerCount(getListenerCount() - 1);		// Decrease number of active listeners by 1
	waitLock.release();											// Release the lock for the listener

	/* Return the recorded word */
	return listenWord;
    }

    /* These were needed to use "get" and "set" for listener and speaker */
    public int getSpeakerCount() {
	return speakerCount;
    }
    public void setSpeakerCount(int speakerCount) {
	this.speakerCount = speakerCount;
    }
    public int getListenerCount() {
	return listenerCount;
    }
    public void setListenerCount(int listenerCount) {
	this.listenerCount = listenerCount;
    }	
}