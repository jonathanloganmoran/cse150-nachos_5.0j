package nachos.threads;

import nachos.ag.BoatGrader;

public class Boat {
    static BoatGrader bg;
    static int boatLocation;
    static int numPassengers;
    static Lock lock = new Lock();    			//must be of data type static
    static int adultsOahu;
    static int adultsMolokai;
    static int childrenOahu;
    static int childrenMolokai;
    static Condition2 Child_Oahu = new Condition2(lock);
    static Condition2 Child_Molokai = new Condition2(lock);
    static Condition2 Boat = new Condition2(lock);
    static Communicator coms = new Communicator();

    public static void selfTest() {
	BoatGrader b = new BoatGrader();

	System.out.println("\n ***Testing Boats with only 2 children***");
	begin(0, 2, b);

	//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
	//  	begin(1, 2, b);

	//  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
	//  	begin(3, 3, b);
    }
    
    // NEW T1.6: 11/5 - @carlos805m & @amunoz35
    public static void begin(int adults, int children, BoatGrader b) {
	// Store the externally generated autograder in a class
	// variable to be accessible by children.
	bg = b;

	// Instantiate global variables here

	// Create threads here. See section 3.4 of the Nachos for Java
	// Walkthrough linked from the projects page.

	int ad = adults;
	int ch = children;

	while (ch > 0) {
	    Runnable child = new Runnable() {
		public void run() {
		    ChildItinerary(0);
		}
	    };
	    KThread t = new KThread(child);
	    t.setName("Child Thread");
	    t.fork();
	    ch--;
	    childrenOahu++;
	}
	while (ad > 0) {
	    Runnable adult = new Runnable() {
		public void run() {
		    AdultItinerary(0);
		}
	    };
	    KThread t = new KThread(adult);
	    t.setName("Child Thread");
	    t.fork();
	    ad--;
	    adultsOahu++;
	}

	//        Runnable r = new Runnable() {
	//            public void run() {
	//                SampleItinerary();
	//            }
	//        };
	//        KThread t = new KThread(r);
	//        t.setName("Sample Boat Thread");
	//        t.fork();

    }

    static void AdultItinerary(int location) {
	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	 */
	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	 */
	lock.acquire();

	while (true)//Used to constantly loop
	    if (location == 0) {//0 will be used for Oahu
		while (childrenOahu > 1 || numPassengers > 0 || boatLocation != 0) {
		    Boat.sleep();
		}
		bg.AdultRowToMolokai();

		numPassengers++;
		//update count and location
		adultsOahu--;
		adultsMolokai++;
		boatLocation = 1;
		location = 1;

		//communicating number of people on Molokai
		coms.speak(childrenMolokai + adultsMolokai);

		//wake everyone up and sleep on Molokai
		Child_Molokai.wakeAll();
		Child_Molokai.sleep();

	    } else if (location == 1) { //Molokai
		Child_Molokai.sleep();
	    } else {
		break;
	    }
	lock.release();
    }

    static void ChildItinerary(int location) {

	lock.acquire();
	do {
	    //if child is in Oahu
	    if (location == 0) {//oahu
		// wait until boat's arrival (in oahu = 0) and an available seat on boat or if one child left in Oahu
		while (boatLocation != 0 || numPassengers >= 2 || (adultsOahu > 0 && childrenOahu == 1)) {
		    Child_Oahu.sleep();
		}
		//If there is a boat and we and should can actually ride or pilot it we wake
		Child_Oahu.wakeAll();
		// if theres only 1 child and no adults take boat back to Molokai
		if (adultsOahu == 0 && childrenOahu == 1) {
		    childrenOahu--;//child leaves Oahu
		    bg.ChildRowToMolokai();
		    boatLocation = 1;
		    location = 1;
		    childrenMolokai++;//New member in Molokai
		    numPassengers = 0;//reset the passenger count
		    coms.speak(childrenMolokai + adultsMolokai);//let peeps know how many poeple are in Molokai
		    Child_Molokai.sleep();//Now we sleep child in Molokai
		    //PROGRAM SHOULD END HERE SINCE EVERYONE SHOULD BE IN MOLOKAI

		} else if (childrenOahu > 1) {// In order to have enough boat trips for adults to go to Molokai they children must travel to Molokai whenever there are more than 1
		    numPassengers++;//Get a passenger on the boat....rider or pilo
		    // two children on boat, the second child rides to Molokai
		    if (numPassengers == 2) {//child that rides the boat
			Boat.wake();//get the boat
			Boat.sleep();
			childrenOahu--;//We going to Molokai
			bg.ChildRideToMolokai();
			numPassengers = numPassengers - 1;//rider gets off
			boatLocation = 1;//Molokai variables
			location = 1;
			childrenMolokai++;//the rider is gonna stay in Molokai
			coms.speak(childrenMolokai + adultsMolokai);
			Child_Molokai.wakeAll();
			Child_Molokai.sleep();
		    } else if (numPassengers == 1) {//if we only have a pilot wait for child to get on since we know there is more than one in OAHU
			Boat.sleep();//sleep boat
			childrenOahu--;//now we ride to Molokai
			bg.ChildRowToMolokai();
			location = 1;
			childrenMolokai++;
			// notify another passenger on baord to leave
			Boat.wake();
			Child_Molokai.sleep();//sleep in molokai
		    }
		}
	    } else if (location == 1) {//if we in Molokai
		while (boatLocation != 1) {//but the boat aint here
		    Child_Molokai.sleep();//sleep
		}//once the boat is back to Molokai (thanks to an adult)deliver boat for adult
		childrenMolokai--;
		bg.ChildRowToOahu();
		boatLocation = 0;
		location = 0;
		childrenOahu++;
		Child_Oahu.wakeAll();
		Child_Oahu.sleep();
	    }
	    if (location == 1234) {// we need to have a break to be able to compile past an "infinite" loop
		break;
	    }
	} while (true);

	lock.release();
    }

    static void SampleItinerary() {
	// Please note that this isn't a valid solution (you can't fit
	// all of them on the boat). Please also note that you may not
	// have a single thread calculate a solution and then just play
	// it back at the autograder -- you will be caught.
	System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
	bg.AdultRowToMolokai();
	bg.ChildRideToMolokai();
	bg.AdultRideToMolokai();
	bg.ChildRideToMolokai();
    }

}