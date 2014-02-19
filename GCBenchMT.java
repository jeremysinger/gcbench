// GCBenchMT.java
// Jeremy Singer
// 20 Feb 14

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * a multi-threaded implementation of Java GCBench,
 * for testing on NUMA machines
 */
public class GCBenchMT {
  
  /**
   * number of concurrent GC worker threads
   * to run
   */
  private int numThreads;

  public GCBenchMT(int numThreads) {
    this.numThreads = numThreads;
  }

  public void start() {
    GCBenchRunner [] runners= new GCBenchRunner[numThreads];
    for (int i=0; i<numThreads; i++) {
      GCBenchRunner r = new GCBenchRunner(i);
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
  }

  public static void main(String [] args) {
    GCBenchMT gcb = new GCBenchMT(2);
    gcb.start();
  }
}