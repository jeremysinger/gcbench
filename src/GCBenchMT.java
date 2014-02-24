// GCBenchMT.java
// Jeremy Singer
// 20 Feb 14

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
 * for testing on NUMA machines
 */
public class GCBenchMT {

  public static String VERSION = "0.01";
  
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
    // command line option parsing (Apache CLI library)
    Options options = new Options();
    Option help = new Option("help", "print this message");
    Option version = new Option("version",
                                "print the version information and exit");
    Option numThreads = OptionBuilder.withArgName("numThreads")
      .hasArg()
      .withDescription("number of threads allocating data ")
      .create("numThreads");
    
    options.addOption(help);
    options.addOption(version);
    options.addOption(numThreads);

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

    int n = 1;
    if (line.hasOption("numThreads")) {
      try {
        n = Integer.parseInt(line.getOptionValue("numThreads"));
      }
      catch(NumberFormatException e) {
        System.err.println("unable to parse numThreads parameter: " + line.getOptionValue("numThreads"));
      }
    }
    
    
    GCBenchMT gcb = new GCBenchMT(n);
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