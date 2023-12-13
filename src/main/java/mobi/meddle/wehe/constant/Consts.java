package mobi.meddle.wehe.constant;

public final class Consts {
  public static final String LOG_APP_NAME = "DiffDetector";

  public static final String METADATA_SERVER = "wehe-metadata.meddle.mobi";
  public static final String MLAB_WEB_SOCKET_SERVER_KEY = "wss://:4443/v0/envelope/access";

  public static final int A_THRESHOLD = 50; //percent
  public static final int KS2PVAL_THRESHOLD = 1; //percent

  public static final boolean TIMEOUT_ENABLED = true;
  public static final int REPLAY_APP_TIMEOUT = 45; //in seconds
  public static final int REPLAY_PORT_TIMEOUT = 30; //in seconds
  public static final String LOC_XPUT_PAIR_VS_SINGLE = "pairsum_vs_single_xput";
  public static final String LOC_LOSS_CORR = "loss_correlation";
  public static final int MWU_PVAL_THRESHOLD = 5; //percent
  public static final int CORR_PVAL_THRESHOLD = 5; //percent
  public static final int CORR_RATIO_THRESHOLD = 50; //percent

  //from BuildConfig in android
  public static final String VERSION_NAME = "4.0.0";

  //exit codes
  public static final int SUCCESS = 0;
  public static final int ERR_GENERAL = 1;
  public static final int ERR_CMDLINE = 2;
  public static final int ERR_INFO_RD = 3;
  public static final int ERR_INFO_WR = 4;
  public static final int ERR_WR_LOGS = 5;
  public static final int ERR_CONN_IP = 6;
  public static final int ERR_CONN_INST = 7;
  public static final int ERR_CONN_IO_SERV = 8;
  public static final int ERR_CONN_WS = 9;
  public static final int ERR_UNK_HOST = 10;
  public static final int ERR_UNK_META_HOST = 11;
  public static final int ERR_CERT = 12;
  public static final int ERR_NO_ID = 13;
  public static final int ERR_NO_TEST = 14;
  public static final int ERR_PERM_REPLAY = 15;
  public static final int ERR_PERM_IP = 16;
  public static final int ERR_PERM_RES = 17;
  public static final int ERR_PERM_UNK = 18;
  public static final int ERR_ANA_NULL = 19;
  public static final int ERR_ANA_NO_SUC = 20;
  public static final int ERR_ANA_HIST_CT = 21;
  public static final int ERR_RSLT_NULL = 22;
  public static final int ERR_RSLT_NO_RESP = 23;
  public static final int ERR_RSLT_NO_SUC = 24;
  public static final int ERR_RSLT_ID_HC = 25;
  public static final int ERR_BAD_JSON = 26;
  public static final int ERR_LOC_TEST = 27;
}
