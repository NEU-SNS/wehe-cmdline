package mobi.meddle.wehe.util;

import mobi.meddle.wehe.constant.Consts;

/**
 * Replaces the Config class and configurations.properties file in the Android version.
 */
public class Config {
  public static String appName;
  public static String serverDisplay = "wehe4.meddle.mobi";
  public static String mLabLocateServers = "https://locate.measurementlab.net/v2/nearest/wehe/replay";
  public static Boolean useYTopology = false;
  public static String mLabYTopologiesURL = "https://storage.googleapis.com/wehe/v0";
  public static int numServers = 1;
  public static boolean confirmationReplays = true;
  public static boolean useDefaultThresholds = true;
  public static int a_threshold = Consts.A_THRESHOLD;
  public static int ks2pvalue_threshold = Consts.KS2PVAL_THRESHOLD;
  public static int logLev = 0;

  public static boolean timing;
  public static String publicIP;
  public static int result_port = 56566;
  public static int combined_sidechannel_port = 55556;
  public static String extraString = "DiffDetector";
  public static String sendMobileStats = "false";
  public static String result = "false";
  public static String client_replayname = "doesn't exist in android code, would crash if header added";

  //resources
  public static String RESOURCES_ROOT = "res/";
  public static String APPS_FILENAME = RESOURCES_ROOT + "apps_list.json";
  public static String MAIN_CERT = RESOURCES_ROOT + "main";
  public static String META_CERT = RESOURCES_ROOT + "metadata";

  //logging
  public static String RESULTS_ROOT = "test_results/";
  public static String RESULTS_UI = RESULTS_ROOT + "ui/";
  public static String RESULTS_LOGS = RESULTS_ROOT + "logs/";
  public static String INFO_FILE = RESULTS_ROOT + "info.txt";

  /**
   * Must call this after RESOURCES_ROOT changed to properly update other variables
   */
  public static void updateResourcesRoot() {
    APPS_FILENAME = RESOURCES_ROOT + "apps_list.json";
    MAIN_CERT = RESOURCES_ROOT + "main";
    META_CERT = RESOURCES_ROOT + "metadata";
  }

  /**
   * Must call this after RESULTS_ROOT changed to properly update other variables
   */
  public static void updateResultsRoot() {
    RESULTS_UI = RESULTS_ROOT + "ui/";
    RESULTS_LOGS = RESULTS_ROOT + "logs/";
    INFO_FILE = RESULTS_ROOT + "info.txt";
  }
}
