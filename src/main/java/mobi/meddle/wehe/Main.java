package mobi.meddle.wehe;

import mobi.meddle.wehe.constant.Consts;
import mobi.meddle.wehe.constant.S;
import mobi.meddle.wehe.util.Config;
import mobi.meddle.wehe.util.Log;

/**
 * Main class for Wehe command line client.
 */
public class Main {

  private static final String[] APP_IDS = {"applemusic", "dailymotion", "deezer", "disneyplus",
          "facebookvideo", "meet", "hulu", "teams", "nbcsports", "netflix", "molotovtv", "mycanal",
          "ocs", "amazon", "salto", "sfrplay", "skype", "spotify", "twitch", "twittervideo", "vimeo",
          "webex", "whatsapp", "youtube", "zoom", "port80s", "port81s", "port465s", "port853s", "port993s",
          "port995s", "port1194s", "port1701s", "port5061s", "port6881s", "port8080s", "port8443s",
          "port80l", "port81l", "port465l", "port853l", "port993l", "port995l", "port1194l",
          "port1701l", "port5061l", "port6881l", "port8080l", "port8443l"}; //for -n argument
  private static String logLevel = "UI";

  /**
   * Main starting point of Wehe command line client.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    parseArgs(args);
    Log.ui("Configs", "\n\tReplay name: " + Config.appName
            + "\n\tServer name: " + Config.serverDisplay
            + "\n\tM-Lab Server API: " + Config.mLabServers
            + "\n\tNumber servers: " + Config.numServers
            + "\n\tConfirmation replays: " + Config.confirmationReplays
            + "\n\tArea threshold: " + Config.a_threshold
            + "\n\tKS2P-value threshold: " + Config.ks2pvalue_threshold
            + "\n\tResources root: " + Config.RESOURCES_ROOT
            + "\n\tResults root: " + Config.RESULTS_ROOT
            + "\n\tLogging level: " + logLevel
            + "\n\tVersion: " + Consts.VERSION_NAME);

    Replay replay = new Replay();
    int exitCode = replay.beginTest();
    exitCode = Log.writeLogs(exitCode);
    System.exit(exitCode);
  }

  /**
   * Prints an error.
   *
   * @param error error message
   */
  private static void printError(String error) {
    System.out.println(error + "\nUse the -h option for additional help.");
    System.exit(Consts.ERR_CMDLINE);
  }

  /**
   * Check to make sure option is valid.
   *
   * @param arg the option to check
   * @return true if option is valid; false otherwise
   */
  private static boolean isValidArg(String arg) {
    return arg.equals("-n") || arg.equals("-s") || arg.equals("-m") || arg.equals("-u")
            || arg.equals("-c") || arg.equals("-a") || arg.equals("-k") || arg.equals("-t")
            || arg.equals("-r") || arg.equals("-l");
  }

  /**
   * The command line argument parser.
   *
   * @param args args from the command line
   */
  private static void parseArgs(String[] args) {
    boolean testSpecified = false;
    for (String arg : args) {
      if (arg.equals("-h")) {
        System.out.println(S.USAGE);
        System.exit(Consts.SUCCESS);
      }
      if (arg.equals("-v")) {
        System.out.println("Wehe command line client\nVersion: " + Consts.VERSION_NAME);
        System.exit(Consts.SUCCESS);
      }
      if (arg.equals("-n")) { //test name must be specified
        testSpecified = true;
      }
    }
    if (!testSpecified) {
      printError("Test name (-n) must be specified.");
    }

    String opt;
    String arg = "";
    for (int i = 0; i < args.length; i++) {
      //get args 2 at a time ([-opt] [arg]), except for -c which doesn't take an arg
      opt = args[i];
      if (!isValidArg(opt)) { //get option
        printError("The \"" + opt + "\" option is not a valid.");
      }
      if (!opt.equals("-c")) {
        if (i == args.length - 1) { //option (except for -c) cannot be the last arg
          printError("The \"" + opt + "\" option requires an argument.");
        } else {
          arg = args[++i]; //get the argument for the option
        }
      }

      switch (opt) {
        case "-n": //name of test
          boolean found = false;
          String name = arg.toLowerCase().strip();
          for (String id : APP_IDS) { //make sure test name is valid
            if (name.equals(id)) {
              found = true;
              break;
            }
          }
          if (found) {
            Config.appName = name;
          } else {
            printError("\"" + name + "\" is not a valid test name.");
          }
          break;
        case "-s": //url of server
          Config.serverDisplay = arg.toLowerCase();
          if (!Config.serverDisplay.matches("[a-z0-9.-]+")) {
            printError("\"" + arg + "\" is not a valid server name. Can only contain alphanumerics, "
                    + "period, and hyphen.");
          }
          break;
        case "-m": //url of mlab server api
          Config.mLabServers = arg;
          break;
        case "-u": //number of mlab servers to use (must be between 1 and 4 inclusive)
          try {
            int numServers = Integer.parseInt(arg);
            if (numServers >= 1 && numServers <= 4) {
              Config.numServers = numServers;
            } else {
              printError("Number of servers must be between 1 and 4 inclusive.");
            }
          } catch (NumberFormatException e) {
            printError("Number of servers must be between 1 and 4 inclusive.");
          }
          break;
        case "-c": //turn off confirmation replay
          Config.confirmationReplays = false;
          break;
        case "-a": //set area threshold
          Config.useDefaultThresholds = false;
          try {
            Config.a_threshold = Integer.parseInt(arg);
            if (Config.a_threshold < 0 || Config.a_threshold > 100) {
              printError("Area threshold must be an integer between 0 and 100.");
            }
          } catch (NumberFormatException e) {
            printError("\"" + arg + "\" is not an integer.");
          }
          break;
        case "-k": //set ks2p-value threshold
          Config.useDefaultThresholds = false;
          try {
            Config.ks2pvalue_threshold = Integer.parseInt(arg);
            if (Config.ks2pvalue_threshold < 0 || Config.ks2pvalue_threshold > 100) {
              printError("ks2pvalue must be an integer between 0 and 100.");
            }
          } catch (NumberFormatException e) {
            printError("\"" + arg + "\" is not an integer.");
          }
          break;
        case "-t": //set root directory containing the tests and test info
          if (!arg.endsWith("/")) {
            arg += "/";
          }
          Config.RESOURCES_ROOT = arg;
          Config.updateResourcesRoot();
          break;
        case "-r": //set root directory to place results/logs
          if (!arg.endsWith("/")) {
            arg += "/";
          }
          Config.RESULTS_ROOT = arg;
          Config.updateResultsRoot();
          break;
        case "-l":
          logLevel = arg.toLowerCase();
          switch (logLevel) {
            case "wtf":
              Config.logLev = 1;
              break;
            case "error":
              Config.logLev = 2;
              break;
            case "warn":
              Config.logLev = 3;
              break;
            case "info":
              Config.logLev = 4;
              break;
            case "debug":
              Config.logLev = 5;
              break;
            default:
              printError("\"" + arg + "\" is not a log level. Choose from wtf, error, warn, info, or debug.");
          }
          break;
        default: //options should have already been checked for validity before switch
          printError("Something went horribly wrong parsing arguments.");
      }
    }
  }
}
