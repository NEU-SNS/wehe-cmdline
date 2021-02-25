package mobi.meddle.wehe.constant;

public final class Consts {
  public static final String LOG_APP_NAME = "DiffDetector";

  public static final String METADATA_SERVER = "wehe-metadata.meddle.mobi";
  public static final String MLAB_SERVERS = "https://locate.measurementlab.net/v2/nearest/wehe/replay";
  public static final String MLAB_WEB_SOCKET_SERVER_KEY = "wss://:4443/v0/envelope/access";
  public static final int MLAB_NUM_TRIES_TO_CONNECT = 4; //number MLab servers to try before going to wehe2, must be between 1 and 4

  public static final int A_THRESHOLD = 50; //percent
  public static final int KS2PVAL_THRESHOLD = 1; //percent

  public static final boolean TIMEOUT_ENABLED = true;
  public static final int REPLAY_APP_TIMEOUT = 45; //in seconds
  public static final int REPLAY_PORT_TIMEOUT = 30; //in seconds

  //from BuildConfig in android
  public static final String VERSION_NAME = "1.01";
}
