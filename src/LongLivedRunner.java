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

import java.util.Stack;

public class LongLivedRunner implements Runnable {

  /**
   * unique id for this thread
   */
  private int id;

  /** 
   * pool where allocated Nodes are stored
   */
  private Stack<Node> pool;
  
  /**
   * constructor
   */
  public LongLivedRunner(int id, Stack<Node> pool) {
    this.id = id;
    this.pool = pool;
  }

  @Override
  public void run() {
    output("allocating local nodes");
    int numNodes = GCBench.TreeSize(GCBench.kLongLivedTreeDepth);
    for (int i=0; i<numNodes; i++) {
      pool.push(new Node());
    }
  }
    

  public void output(String s) {
    System.out.printf("[%d] %s\n", id, s);
  }
} // class LongLivedRunner
