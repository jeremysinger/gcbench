// This is adapted from a benchmark written by John Ellis and Pete Kovac
// of Post Communications.
// It was modified by Hans Boehm of Silicon Graphics.
// @jsinger modifications for multi-threading: Feb 2014
//
// 	This is no substitute for real applications.  No actual application
//	is likely to behave in exactly this way.  However, this benchmark was
//	designed to be more representative of real applications than other
//	Java GC benchmarks of which we are aware.
//	It attempts to model those properties of allocation requests that
//	are important to current GC techniques.
//	It is designed to be used either to obtain a single overall performance
//	number, or to give a more detailed estimate of how collector
//	performance varies with object lifetimes.  It prints the time
//	required to allocate and collect balanced binary trees of various
//	sizes.  Smaller trees result in shorter object lifetimes.  Each cycle
//	allocates roughly the same amount of memory.
//	Two data structures are kept around during the entire process, so
//	that the measured performance is representative of applications
//	that maintain some live in-memory data.  One of these is a tree
//	containing many pointers.  The other is a large array containing
//	double precision floating point numbers.  Both should be of comparable
//	size.
//
//	The results are only really meaningful together with a specification
//	of how much memory was used.  It is possible to trade memory for
//	better time performance.  This benchmark should be run in a 32 MB
//	heap, though we don't currently know how to enforce that uniformly.
//
//	Unlike the original Ellis and Kovac benchmark, we do not attempt
// 	measure pause times.  This facility should eventually be added back
//	in.  There are several reasons for omitting it for now.  The original
//	implementation depended on assumptions about the thread scheduler
//	that don't hold uniformly.  The results really measure both the
//	scheduler and GC.  Pause time measurements tend to not fit well with
//	current benchmark suites.  As far as we know, none of the current
//	commercial Java implementations seriously attempt to minimize GC pause
//	times.

import java.util.Random;

public class GCBenchRunner implements Runnable {

  /**
   * unique id number for this GCBenchRunner thread
   */
  private int id;

  /**
   * should this thread allocate some
   * long-lived data locally?
   */
  private boolean localLongLivedData;

  /**
   * harness for multi-threaded 
   * GCBench execution
   */
  private GCBenchMT harness;

  public GCBenchRunner(int id) {
    this(id, true, null);
  }

  public GCBenchRunner(int id, boolean localLongLivedData, GCBenchMT harness) {
    this.id = id;
    this.localLongLivedData = localLongLivedData;
    this.harness = harness;
  }

	public static final int kStretchTreeDepth    = 18;	// about 16Mb
	public static final int kLongLivedTreeDepth  = 16;  // about 4Mb
	public static final int kArraySize  = 500000;  // about 4Mb
	public static final int kMinTreeDepth = 4;
	public static final int kMaxTreeDepth = 16;

        @Override
	public void run() {
		Node	root;
		Node	longLivedTree = null; // for local long-lived data
		Node	tempTree;
		long	tStart, tFinish;
		long	tElapsed;
                double [] array = null; // for local long-lived data

		output("Garbage Collector Test");
		output(
			" Stretching memory with a binary tree of depth "
			+ kStretchTreeDepth);
		GCBench.PrintDiagnostics();
		tStart = System.currentTimeMillis();

		// Stretch the memory space quickly
		tempTree = GCBench.MakeTree(kStretchTreeDepth);
		tempTree = null;

                if (this.localLongLivedData) {
                  // Create a long lived object
                  output(
                         " Creating a long-lived binary tree of depth " +
                         kLongLivedTreeDepth);
                  longLivedTree = new Node();
                  GCBench.Populate(kLongLivedTreeDepth, longLivedTree);
                  
                  // Create long-lived array, filling half of it
                  output(
                         " Creating a long-lived array of "
                         + kArraySize + " doubles");
                  array = new double[kArraySize];
                  for (int i = 0; i < kArraySize/2; ++i) {
                    array[i] = 1.0/i;
                  }
                }
                
		GCBench.PrintDiagnostics();
                
                // now allocate local short-lived data
                
		for (int d = kMinTreeDepth; d <= kMaxTreeDepth; d += 2) {
                  GCBench.TimeConstruction(d);

                  // @jsinger
                  // shuffle long-lived pointers here
                  // (from harness-allocated data - change its
                  // static pointers)
                  Random rng = new Random();
                  int r0 = rng.nextInt(harness.getNumThreads());
                  int r1 = rng.nextInt(harness.getNumThreads());
                  int r2 = rng.nextInt(harness.getNumThreads());
                  int r3 = rng.nextInt(harness.getNumThreads());
                  GCBenchMT.longLivedPointer0 = harness.longLivedTrees[r0];
                  GCBenchMT.longLivedPointer1 = harness.longLivedTrees[r1];
                  GCBenchMT.longLivedPointer2 = harness.longLivedTrees[r2];
                  GCBenchMT.longLivedPointer3 = harness.longLivedTrees[r3];
		}
                
                
                if (this.localLongLivedData) {
                  if (longLivedTree == null || array[1000] != 1.0/1000)
                    output("Failed");
                  // fake reference to LongLivedTree
                  // and array
                  // to keep them from being optimized away
                }

		tFinish = System.currentTimeMillis();
		tElapsed = tFinish-tStart;
		GCBench.PrintDiagnostics();
		output("Completed in " + tElapsed + "ms.");
	}


  public void output(String s) {
    System.out.printf("[%d] %s\n", id, s);
  }
} // class JavaGC
