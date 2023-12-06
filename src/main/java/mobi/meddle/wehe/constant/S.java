package mobi.meddle.wehe.constant;

public final class S {
  public static final String PORT_NAME = "Port %s";

  public static final String ERROR_IO_INFO = "IO error related to info file.";
  public static final String ERROR_JSON = "JSON error.";
  public static final String ERROR_NO_CONNECTION = "Cannot connect to server, try again later.";
  public static final String ERROR_NO_WS = "Cannot connect to WebSocket.";
  public static final String ERROR_UNKNOWN_HOST = "Cannot find server. Try again later or try another server.";
  public static final String ERROR_UNKNOWN_META_HOST = "Cannot find metadata server. Try again later.";
  public static final String ERROR_CERTS = "Error with certificates.";
  public static final String ERROR_NO_USER_ID = "Cannot find the user ID.";
  public static final String ERROR_UNKNOWN_TEST = "No such test exists.";
  public static final String ERROR_UNKNOWN_REPLAY = "Replay does not match the replay on the server.";
  public static final String ERROR_IP_CONNECTED = "A client with this IP is already connected.";
  public static final String ERROR_LOW_RESOURCES = "Server is low on resources, try again later.";
  public static final String ERROR_UNKNOWN = "Unknown server error";
  public static final String ERROR_ANALYSIS_FAIL = "Unable to analyze replay.";
  public static final String ERROR_RESULT = "Error getting results.";

  public static final String CREATE_SIDE_CHANNEL = "Creating side channel";
  public static final String ASK4PERMISSION = "Asking for permission";
  public static final String RECEIVE_SERVER_PORT_MAPPING = "Receiving server port mapping";
  public static final String CREATE_TCP_CLIENT = "Creating all TCP client sockets";
  public static final String CREATE_UDP_CLIENT = "Creating all UDP client sockets";
  public static final String RUN_NOTF = "Starting side channel notifier";
  public static final String RUN_RECEIVER = "Starting the Receiver process";
  public static final String RUN_SENDER = "Running the Sender process";
  public static final String SEND_DONE = "Notifying server that replay is complete";
  public static final String WAITING = "Waiting for server result";
  public static final String CONFIRMATION_REPLAY = "Possible differentiation, re-running test to confirm";

  public static final String NO_DIFF = "No differentiation";
  public static final String HAS_DIFF = "Differentiation detected";
  public static final String INCONCLUSIVE = "Results inconclusive, try running the test again";

  public static final String TEST_BLOCKED_APP_TEXT = "The test appears to have been blocked. This "
          + "blockage might come from your ISP or an equipment on your local network or the server side.";
  public static final String TEST_BLOCKED_PORT_TEXT = "HTTPS traffic on this port might be blocked."
          + " This blockage might come from your ISP or an equipment on your local network or the server side.";
  public static final String TEST_PRIORITIZED_APP_TEXT = "The test appears to have been prioritized."
          + " This prioritization might come from your ISP or an equipment on your local network or the server side.";
  public static final String TEST_PRIORITIZED_PORT_TEXT = "HTTPS traffic on this port might be"
          + " prioritized. This prioritization might come from your ISP or an equipment on your"
          + " local network or the server side.";
  public static final String TEST_THROTTLED_APP_TEXT = "The test appears to have been throttled."
          + " This throttling might come from your ISP or an equipment on your local network or the"
          + " server side.\n\n360p: 0.4–1.0 Mbps\n480p: 0.5–2.0 Mbps\n720p: 1.5–4.0 Mbps\n1080p: 3.0–6.0 Mbps";
  public static final String TEST_THROTTLED_PORT_TEXT = "HTTPS traffic on this port might be"
          + " throttled. This throttling might come from your ISP or an equipment on your local"
          + " network or the server side.\n\n360p: 0.4–1.0 Mbps\n480p: 0.5–2.0 Mbps\n720p:"
          + " 1.5–4.0 Mbps\n1080p: 3.0–6.0 Mbps";
  public static final String NOT_ALL_TCP_SENT_TEXT = "It seems that Wehe could not reach a result. "
          + "If this situation persists, it might come from your ISP or an equipment on your local "
          + "network or the server side.";

  public static final String REPLAY_FINISHED_TITLE = "Replays Finished!";

  public static final String USAGE = "Usage: java -jar wehe-cmdline.jar -n [TEST_NAME] [OPTION]...\n\n"
          + "Command line version of the Wehe client. Wehe is an app that allows users to detect\n"
          + "if their network is throttling the traffic of certain apps and ports. This command line\n"
          + "client was created to test the servers, and thus, it does not have all of the features\n"
          + "currently present on the app.\n\n"
          + "Example: java -jar wehe-cmdline.jar -n applemusic -c -r results/\n\n"
          + "Options:\n"
          + "  -n TEST_NAME name of the test to run (required argument; see below for list of tests)\n"
          + "  -s SERV_NAME hostname or IP of server to run the tests (Default: wehe4.meddle.mobi)\n"
          + "  -m MLAB_API  URL of the API to retrieve access envelopes to run tests on M-Lab servers\n"
          + "                 (Default: https://locate.measurementlab.net/v2/nearest/wehe/replay)\n"
          + " -y Run a localization test\n"
          + "  -u NUM_SRVR  number of servers to run test concurrently; available only with M-Lab\n"
          + "                 servers; must be between 1 and 4 inclusive (Default: 1)\n"
          + "  -c           turn off confirmation replays (if test is inconclusive, it will automatically\n"
          + "                 rerun by default)\n"
          + "  -a A_THRESH  area threshold percentage for determining differentiation (Default: 50)\n"
          + "  -k KS2P_VAL  KS2P-value threshold percentage for determining differentiation (Default: 1)\n"
          + "  -t RESR_ROOT resources root containing apps_list.json and the tests (Default: res/)\n"
          + "  -r RSLT_ROOT results root containing the result logs and info (Default: test_results/)\n"
          + "  -l LOG_LEVEL level of logs and above that should be printed to console (all levels will\n"
          + "                 be saved to the logs on disk regardless of the level printed to console);\n"
          + "                 either wtf, error, warn, info, or debug (Default: none of these, only UI logs)\n"
          + "  -h           print this help message\n"
          + "  -v           print the version number\n\n"
          + "App Name          Test Name (-n arg) || Port Name               Test Name (-n arg)\n"
          + "-------------------------------------||-------------------------------------------\n"
          + "Apple Music       applemusic         || 80 HTTP small           port80s\n"
          + "Dailymotion       dailymotion        || 81 HTTP small           port81\n"
          + "Deezer            deezer             || 465 SMTPS small         port465s\n"
          + "Disney+           disneyplus         || 853 DoT small           port853s\n"
          + "Facebook Video    facebookvideo      || 993 IMAPS small         port993s\n"
          + "Google Meet       meet               || 995 POP3S small         port995s\n"
          + "Hulu              hulu               || 1194 OpenVPN small      port1194s\n"
          + "Microsoft Teams   teams              || 1701 L2TP small         port1701s\n"
          + "NBC Sports        nbcsports          || 5061 SIPS small         port5061s\n"
          + "Netflix           netflix            || 6881 BitTorrent small   port6881s\n"
          + "Molotov TV        molotovtv          || 8080 SpeedTest small    port8080s\n"
          + "myCANAL           mycanal            || 8443 SpeedTest small    port8443s\n"
          + "OCS               ocs                || 80 HTTP large           port80l\n"
          + "Prime Video       amazon             || 81 HTTP large           port81l\n"
          + "Salto             salto              || 465 SMTPS large         port465l\n"
          + "SFR Play          sfrplay            || 853 DoT large           port853l\n"
          + "Skype             skype              || 993 IMAPS large         port993l\n"
          + "Spotify           spotify            || 995 POP3S large         port995l\n"
          + "Twitch            twitch             || 1194 OpenVPN large      port1194l\n"
          + "Twitter Video     twittervideo       || 1701 L2TP large         port1701l\n"
          + "Vimeo             vimeo              || 5061 SIPS large         port5061l\n"
          + "Webex             webex              || 6881 BitTorrent large   port6881l\n"
          + "WhatsApp          whatsapp           || 8080 SpeedTest large    port8080l\n"
          + "YouTube           youtube            || 8443 SpeedTest large    port8443l\n"
          + "Zoom              zoom               || ";
}
