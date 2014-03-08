// GCBenchMT.java
// Jeremy Singer
// 20 Feb 14

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.util.ArrayList;
import java.util.Stack;
import java.util.Random;

import org.apache.commons.cli.Options;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.ParseException;;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;


/**
 * a multi-threaded implementation of Java GCBench,
 * for testing GC performance on NUMA machines
 */
public class GCBenchMT {

  /**
   * benchmark version (for -version flag)
   */
  public static String VERSION = "0.02";

  /**
   * pointers to long-lived data structures,
   * only for -remoteMem flag.
   * (global array across all threads)
   */
  public Node [] longLivedTrees;

  /**
   * if true, we fill the longLivedTrees data
   * structure with node trees that have
   * cross-NUMA-region pointers.
   * set using -remoteMem command line option.
   */
  private boolean enableRemoteMem;
  
  /**
   * number of concurrent GC worker threads
   * to run.
   * set using -numThreads N command line option.
   */
  private int numThreads;

  /**
   * pool (per thread) of Node objects to use
   * when constructing data for
   * longLivedTrees - use for remoteMem
   */
  private ArrayList<Stack<Node>> pools;

  /**
   * main constructor
   */
  public GCBenchMT(int numThreads, boolean enableRemoteMem) {
    this.numThreads = numThreads;
    this.enableRemoteMem = enableRemoteMem;
    // init LongLivedTrees array if necessary
    // and Node pools
    if (enableRemoteMem) {
      longLivedTrees = new Node[numThreads];
      pools = new ArrayList<Stack<Node>>();
      for (int i=0; i<numThreads; i++) {
        pools.add(i, new Stack<Node>());
      }
    }
    else {
      longLivedTrees = null;
      pools = null;
    }
  }

  /**
   * sets up global data structures and 
   * orchestrates execution of parallel
   * worker threads.
   * Three phases:
   * 1) LongLivedRunner threads create pools
   *    of thread-local Node objects
   * 2) generate global trees with cross-NUMA-region
   *    pointers
   * 3) GCBenchRunner threads exercise the GC
   *    with thread-local short- and long-lived 
   *    data structures.
   * Phases 1 and 2 only enabled for remoteMem
   * option. Phase 3 always executes.
   */
  public void start() {
    
    if (enableRemoteMem) {
      // phase 1
      System.out.println("Allocating Node object pools");
      LongLivedRunner [] llRunners = new LongLivedRunner[numThreads];
      for (int i=0; i<numThreads; i++) {
        // allocate single nodes into thread-local pools
        LongLivedRunner l = new LongLivedRunner(i, pools.get(i));
        llRunners[i] = l;
      }

      ExecutorService executor = Executors.newFixedThreadPool(numThreads);
      for (LongLivedRunner l : llRunners) {
        executor.execute(l);
      }

      executor.shutdown();
      // Wait until all threads finish
      try {
        // Wait a while for existing tasks to terminate
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
          executor.shutdownNow(); // Cancel currently executing tasks
          // Wait a while for tasks to respond to being cancelled
          if (!executor.awaitTermination(60, TimeUnit.SECONDS))
            System.err.println("Pool did not terminate");
        }
      } catch (InterruptedException ie) {
        // (Re-)Cancel if current thread also interrupted
        executor.shutdownNow();
        // Preserve interrupt status
        Thread.currentThread().interrupt();
      }
      System.out.println("Finished allocating Node object pools");

      // phase 2
      System.out.println("About to shuffle pointers between thread-local data structures...");

      // iterate over each depth of tree
      // at each depth, make all nodes in trees[i] point to nodes in trees[i+1]
      for (int i=0; i<numThreads; i++) {
        Node remoteTree = MakeRemoteTree(GCBench.kLongLivedTreeDepth, i);
        longLivedTrees[i] = remoteTree;
      }

      System.out.println("Finished shuffling pointers between thread-local data structures.");

      
    } // if (enableRemoteMem)

    // phase 3
    GCBenchRunner [] runners= new GCBenchRunner[numThreads];
    for (int i=0; i<numThreads; i++) {
      GCBenchRunner r = new GCBenchRunner(i, !enableRemoteMem);
      runners[i] = r;
    }

    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    for (GCBenchRunner r : runners) {
      executor.execute(r);
    }

    executor.shutdown();
    // Wait until all threads finish
    try {
      // Wait a while for existing tasks to terminate
      if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
        executor.shutdownNow(); // Cancel currently executing tasks
        // Wait a while for tasks to respond to being cancelled
        if (!executor.awaitTermination(60, TimeUnit.SECONDS))
          System.err.println("Pool did not terminate");
      }
    } catch (InterruptedException ie) {
      // (Re-)Cancel if current thread also interrupted
      executor.shutdownNow();
      // Preserve interrupt status
      Thread.currentThread().interrupt();
    }
    System.out.println("[harness] Finished all threads");

    if (this.enableRemoteMem) {
      Random rng = new Random();
      int r = rng.nextInt(numThreads);
      if (longLivedTrees[r] == null)
        System.out.println("Failed");
      // fake reference to longLivedTrees
      // to keep them from being optimized away
    }

  }

  /**
   * builds a tree bottom-up, with
   * nodes at each level from a different 
   * thread allocation pool to induce lots
   * of cross-NUMA-region pointers
   */
  public Node MakeRemoteTree(int iDepth, int currentPool) {
    Node n = getNewNodeFromPool(currentPool);
    if (iDepth>0) {
      n.left = MakeRemoteTree(iDepth-1, nextPool(currentPool));
      n.right = MakeRemoteTree(iDepth-1, nextPool(currentPool));
    }
    return n;
  }

  /**
   * fetch a pre-allocated Node
   * instance from the specified
   * thread-local pool
   */
  public Node getNewNodeFromPool(int pool) {
    // return node from the indexed pool (list)
    return pools.get(pool).pop();
  }

  /**
   * which pool should we use for the next level of
   * allocation depth? (rotate round all pools in 
   * round-robin order)
   */
  public int nextPool(int pool) {
    return (pool+1)%this.numThreads;
  }

  /**
   * entry point for GCBenchMT - parses
   * command line options then instantiates
   * object of this type.
   */
  public static void main(String [] args) {
    // command line option parsing (Apache CLI library)
    Options options = new Options();
    Option help = new Option("help", "print this message");
    Option version = new Option("version",
                                "print the version information and exit");
    Option numThreads = OptionBuilder.withArgName("numThreads")
      .hasArg()
      .withDescription("number of threads allocating data ")
      .create("numThreads");
    Option remoteMem = new Option("remoteMem",
                                  "enable aggressive remote memory allocations");
    
    options.addOption(help);
    options.addOption(version);
    options.addOption(numThreads);
    options.addOption(remoteMem);

    CommandLineParser parser = new GnuParser();
    CommandLine line = null;
    try {
      // parse the command line arguments
      line = parser.parse(options, args);
    }
    catch(ParseException pe) {
      // oops, something went wrong
      System.err.println("Parsing failed.  Reason: " + pe.getMessage());
      System.exit(-1);
    }

    if (line.hasOption("help")) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("GCBenchMT", options);
      System.exit(0);
    }

    if (line.hasOption("version")) {
      printVersion();
      System.exit(0);
    }

    boolean useRemoteMem = false;
    if (line.hasOption("remoteMem")) {
      System.out.println("enabling remote mem data structures");
      useRemoteMem = true;
    }

    int n = 1;
    if (line.hasOption("numThreads")) {
      try {
        n = Integer.parseInt(line.getOptionValue("numThreads"));
      }
      catch(NumberFormatException e) {
        System.err.println("unable to parse numThreads parameter: " + line.getOptionValue("numThreads"));
      }
    }
    
    GCBenchMT gcb = new GCBenchMT(n, useRemoteMem);
    gcb.start();
  } // main()

  /**
   * print version of program to stderr
   * (invoke with -version option on CLI)
   */
  public static void printVersion() {
    System.err.printf("GCBench v%s\n", VERSION);
  }
}