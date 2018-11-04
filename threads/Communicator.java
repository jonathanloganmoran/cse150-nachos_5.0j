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
	private Lock waitLock;
	private static Integer noise;
	private int speakerCount, listenerCount;	// Records number of speakers, listeners
	private Condition2 listener;
	private Condition2 speaker;
	private boolean wordReady;					// Records if a speaker has spoken yet
	//private int listenWord;
	
	public Communicator() {
		noise = 0;
		waitLock = new Lock();
		listenerCount = 0;
		setSpeakerCount(0);
		wordReady = false;
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
		waitLock.acquire();						// Acquire the lock for the speaker
		setSpeakerCount(getSpeakerCount() + 1);						// Increase the speaker count by 1

		/* While no listeners, or while a speaker has spoken a word not yet 'listened' to */
		while(listenerCount == 0 || wordReady == true) {
			speaker.sleep();
		}
		
		noise = word;							// Speaker 'speaks'
		wordReady = true;						// Record that a word has been spoken
		listener.wake();						// Wake listeners
		setSpeakerCount(getSpeakerCount() - 1);						// Decrease speaker count by 1
		waitLock.release();						// Release the lock for the speaker
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return
	 * the <i>word</i> that thread passed to <tt>speak()</tt>.
	 *
	 * @return    the integer transferred.
	 */
	public int listen() {
		int listenWord;
		waitLock.acquire();						// Acquire the lock for the listener
		listenerCount += 1;						// Increase the listener count by 1
		
		/* While there is no word ready (no words spoken yet) */
		while(wordReady == false) {
			speaker.wakeAll();					// Wake up all speakers
			listener.sleep();					// Put listener to sleep
		}
		
		listenWord = noise.intValue();			// Listener hears what's spoken
		wordReady = false;						// Resets to show no word available to other listeners
		listenerCount -= 1;						// Decrease number of active listeners by 1
		waitLock.release();						// Release the lock for the listener
		
		return listenWord;						// Returns the recorded word
	}
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