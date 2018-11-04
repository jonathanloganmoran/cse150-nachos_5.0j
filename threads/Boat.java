package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
    // Task 1.6: Boat Simulation - @carlos805m 11/4
    static BoatGrader bg;
    static int adultsOahu;
    static int adultsMolokai;
    static int childrenOahu;
    static int childrenMolokai;
    static boolean childPilot;
    static boolean adultPilot;
    static boolean pilot;
    static boolean boatInOahu;
    public static void selfTest()
    {
	BoatGrader b = new BoatGrader();

	System.out.println("\n ***Testing Boats with only 2 children***");
	begin(0, 2, b);

	//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
	//  	begin(1, 2, b);

	//  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
	//  	begin(3, 3, b);
    }

    // NEW Task 1.6: Boat Simulation - @carlos805m
    public static void begin( int adults, int children, BoatGrader b )
    {
	// Store the externally generated autograder in a class
	// variable to be accessible by children.
	bg = b;
	childPilot = false;
	adultPilot = false;
	pilot = false;
	adultsOahu = 0;
	childrenOahu = 0;
	adultsMolokai = 0;
	childrenMolokai = 0;
	// Instantiate global variables here

	// Create threads here. See section 3.4 of the Nachos for Java
	// Walkthrough linked from the projects page.
	while(adults > 0){
	    Runnable adult = new Runnable() {
		public void run() {
		    AdultItinerary();
		}
	    };
	    KThread t = new KThread(adult);
	    t.setName("An Adult Itinerary");
	    t.fork();
	    adultsOahu++;
	}
	while(children > 0){
	    Runnable adult = new Runnable() {
		public void run() {
		    AdultItinerary();
		}
	    };
	    KThread t = new KThread(adult);
	    t.setName("An Adult Itinerary");
	    t.fork();
	    childrenOahu++;
	}

    }//endbegin
    //	Runnable r = new Runnable() {
    //	    public void run() {
    //                SampleItinerary();
    //            }
    //        };
    //        KThread t = new KThread(r);
    //        t.setName("Sample Boat Thread");
    //        t.fork();
    //
    //    }

    static void AdultItinerary()
    {
	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	 */




    }
    
    // NEW Task 1.6: 11/4 @carlos805m 
    static void ChildItinerary()
    {
	//If theres 2 or more children in Oahu, we can send 2 of them to Molokai
	if(childrenOahu >= 2 && boatInOahu){
	    pilot = true;
	    childPilot = true;
	}else if (childrenOahu <= 1){
	    //thres 1 or less child on Oahu
	}
    }

    static void SampleItinerary()
    {
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