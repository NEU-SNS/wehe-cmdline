package mobi.meddle.wehe;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.websocket.DeploymentException;

import mobi.meddle.wehe.bean.ApplicationBean;
import mobi.meddle.wehe.bean.CombinedAppJSONInfoBean;
import mobi.meddle.wehe.bean.JitterBean;
import mobi.meddle.wehe.bean.RequestSet;
import mobi.meddle.wehe.bean.ServerInstance;
import mobi.meddle.wehe.bean.UDPReplayInfoBean;
import mobi.meddle.wehe.combined.CTCPClient;
import mobi.meddle.wehe.combined.CUDPClient;
import mobi.meddle.wehe.combined.CombinedAnalyzerTask;
import mobi.meddle.wehe.combined.CombinedNotifierThread;
import mobi.meddle.wehe.combined.CombinedQueue;
import mobi.meddle.wehe.combined.CombinedReceiverThread;
import mobi.meddle.wehe.combined.CombinedSideChannel;
import mobi.meddle.wehe.combined.WebSocketConnection;
import mobi.meddle.wehe.constant.Consts;
import mobi.meddle.wehe.constant.S;
import mobi.meddle.wehe.util.Config;
import mobi.meddle.wehe.util.Log;
import mobi.meddle.wehe.util.UtilsManager;

/**
 * Runs the replays.
 */
public class Replay {
  private final boolean runPortTests;
  private boolean isIPv6; //true if user's public IP is v6, use to display in results
  private final String serverDisplay; //server to display in the results
  //---------------------------------------------------
  private CombinedAppJSONInfoBean appData;
  private ApplicationBean app;
  private final ArrayList<String> servers = new ArrayList<>(); //server to run the replays to
  private String metadataServer;
  private final ArrayList<WebSocketConnection> wsConns = new ArrayList<>();
  private boolean doTest; //add a tail for testing data if true
  private final ArrayList<String> analyzerServerUrls = new ArrayList<>();
  //true if confirmation replay should run if the first replay has differentiation
  private final boolean confirmationReplays;
  private final boolean useDefaultThresholds;
  private int a_threshold;
  private final int ks2pvalue_threshold;
  private SSLSocketFactory sslSocketFactory = null;
  private HostnameVerifier hostnameVerifier = null;
  private boolean rerun = false; //true if confirmation replay
  //randomID, historyCount, and testId identifies the user, test number, and replay number
  //server uses these to determine which results to send back to client
  private String randomID; //unique user ID for certain device
  //historyCount is the test number; current number can be seen as number of apps run
  //or number of times user hit the run button for ports
  private int historyCount;
  //testId is replay number in a test
  //for apps - 0 is original replay, 1 is random replay
  //for ports - 0 non-443 port, 1 is port 443
  private int testId;
  //---------------------------------------------------
  private String appName; //app/port to run
  private final ArrayList<Timer> timers = new ArrayList<>();
  private final ArrayList<Integer> numMLab = new ArrayList<>(); //number of tries before successful MLab connection

  public Replay() {
    this.appName = Config.appName;
    this.runPortTests = appName.startsWith("port");
    this.serverDisplay = Config.serverDisplay;
    this.confirmationReplays = Config.confirmationReplays;
    this.useDefaultThresholds = Config.useDefaultThresholds;
    this.a_threshold = Config.a_threshold;
    this.ks2pvalue_threshold = Config.ks2pvalue_threshold;
  }

  /**
   * This method begins process to run tests. Step 1: Initialize several variables. Step 2: Run
   * tests. Step 3: Save results.
   *
   * @return exit status
   */
  public int beginTest() {
    /*
     * Step 1: Initialize several variables.
     */
    String[] info;
    try {
      info = Log.readInfo().split(";");
    } catch (IOException e) {
      Log.e("InfoFile", "Info file read error", e);
      Log.ui("ERR_INFO_RD", S.ERROR_IO_INFO);
      return Consts.ERR_INFO_RD;
    }
    historyCount = Integer.parseInt(info[1]);
    // update historyCount
    historyCount++;

    randomID = info[0];
    // This random ID is used to map the test results to a specific instance of app
    // It is generated only once and saved thereafter
    if (randomID == null) {
      Log.e("RecordReplay", "randomID does not exist!");
      Log.ui("ERR_NO_ID", S.ERROR_NO_USER_ID);
      return Consts.ERR_NO_ID;
    }

    //write randomID and updated historyCount to the info file
    Log.incHistoryCount();
    try {
      Log.writeInfo();
    } catch (IOException e) {
      Log.e("InfoFile", "Info file write error", e);
      Log.ui("ERR_INFO_WR", S.ERROR_IO_INFO);
      return Consts.ERR_INFO_WR;
    }
    Log.d("Replay", "historyCount: " + historyCount);

    app = parseAppJSON();
    if (app == null) {
      cleanUp();
      Log.wtf("noSuchApp", "Either there is no app named \"" + appName + "\", or a file"
              + " could not be found.");
      Log.ui("ERR_NO_TEST", S.ERROR_UNKNOWN_TEST);
      return Consts.ERR_NO_TEST;
    }

    //write current historyCount to applicationBean
    app.setHistoryCount(historyCount);

    // metadata here is user's network type device used geolocation if permitted etc
    metadataServer = Consts.METADATA_SERVER;
    int suc = setupServersAndCertificates(serverDisplay, metadataServer);
    if (suc != 0) {
      cleanUp();
      return suc;
    }

    testId = -1;
    doTest = false;

    //timing allows replays to be run with the same timing as when they were recorded
    //for example, if a YouTube video was paused for 2 seconds during recording, then the
    //replay will also pause for 2 seconds at that point in the replay
    //port tests try to run as fast as possible, so there is no timing for them
    Config.timing = !runPortTests;
    String publicIP = getPublicIP("80"); //get user's IP address
    Config.publicIP = publicIP;
    Log.d("Replay", "public IP: " + publicIP);
    //If cannot connect to server, display an error and stop tests
    if (publicIP.equals("-1")) {
      cleanUp();
      Log.ui("ERR_CONN_IP", S.ERROR_NO_CONNECTION);
      return Consts.ERR_CONN_IP;
    }

    /*
     * Step 2: Run tests.
     */
    int exitCode = runTest(); // Run the test on this.app
    cleanUp();

    Log.ui("Finished", S.REPLAY_FINISHED_TITLE);
    if (exitCode == 0) {
      Log.i("Result Channel", "Exiting normally");
    }
    return exitCode;
  }

  /**
   * Close connections and cancel timers before exiting.
   */
  private void cleanUp() {
    for (WebSocketConnection wsConn : wsConns) {
      if (wsConn != null && wsConn.isOpen()) {
        wsConn.close();
      }
    }
    for (Timer t : timers) {
      t.cancel();
    }
  }

  /**
   * This method parses apps_list.json file located in res folder. This file has all the basic
   * details of apps for replay.
   */
  private ApplicationBean parseAppJSON() {
    try {
      //get apps_list.json
      StringBuilder buf = new StringBuilder();
      File appInfo = new File(Config.APPS_FILENAME);
      Path tests_info_file = Paths.get(Config.APPS_FILENAME);
      if (!Files.exists(tests_info_file)) {
        Log.e("Load test", "\"" + Config.APPS_FILENAME + "\" file not found.");
        return null;
      }

      //read the apps/ports in the file
      Scanner scanner = new Scanner(appInfo);
      while (scanner.hasNextLine()) {
        buf.append(scanner.nextLine());
      }

      JSONObject jObject = new JSONObject(buf.toString());
      JSONArray jArray = jObject.getJSONArray("apps");
      String port443SmallFile = Config.RESOURCES_ROOT + jObject.getString("port443small");
      String port443LargeFile = Config.RESOURCES_ROOT + jObject.getString("port443large");

      String portType = "SMALL_PORT";
      if (runPortTests) {
        if (appName.toLowerCase().strip().endsWith("l")) {
          portType = "LARGE_PORT";
        }
        appName = appName.substring(0, appName.length() - 1);
      }

      JSONObject appObj;
      ApplicationBean bean;
      //load each app/port into ApplicationBeans
      for (int i = 0; i < jArray.length(); i++) {
        appObj = jArray.getJSONObject(i);

        if (appObj.getString("image").equals(appName)) { //have user enter image name into cmdline
          if (runPortTests && !appObj.getString("category").equals(portType)) {
            continue;
          }
          bean = new ApplicationBean();
          bean.setDataFile(Config.RESOURCES_ROOT + appObj.getString("datafile"));
          bean.setTime(appObj.getInt("time") * 2); //JSON time only for 1 replay
          bean.setImage(appObj.getString("image"));

          String cat = appObj.getString("category");
          //"random" test for ports is port 443
          if (cat.equals("SMALL_PORT")) {
            bean.setRandomDataFile(port443SmallFile);
          } else if (cat.equals("LARGE_PORT")) {
            bean.setRandomDataFile(port443LargeFile);
          } else {
            bean.setRandomDataFile(Config.RESOURCES_ROOT + appObj.getString("randomdatafile"));
          }

          if (cat.equals("SMALL_PORT") || cat.equals("LARGE_PORT")) {
            bean.setName(String.format(S.PORT_NAME, appObj.getString(("name"))));
          } else {
            bean.setName(appObj.getString("name")); //app names stored in JSON file
          }

          Path normal_test = Paths.get(bean.getDataFile());
          if (!Files.exists(normal_test)) {
            Log.e("Load test", bean.getName() + " normal test file \"" + bean.getDataFile()
                    + "\" not found.");
            return null;
          }
          Path rand_test = Paths.get(bean.getRandomDataFile());
          if (!Files.exists(rand_test)) {
            Log.e("Load test", bean.getName() + " random test file \"" + bean.getRandomDataFile()
                    + "\" not found.");
            return null;
          }
          return bean;
        }
      }
    } catch (IOException e) {
      Log.e(Consts.LOG_APP_NAME, "IOException while reading file " + Config.APPS_FILENAME, e);
    } catch (JSONException e) {
      Log.e(Consts.LOG_APP_NAME, "JSONException while parsing file " + Config.APPS_FILENAME, e);
    }
    return null;
  }

  /**
   * Gets IPs of server and metadata server. Connects to MLab authentication WebSocket if necessary.
   * Gets necessary certificates for server and metadata server.
   *
   * @param server         the hostname of the server to connect to
   * @param metadataServer the host name of the metadata server to connect to
   * @return 0 if everything properly sets up; error code otherwise
   */
  private int setupServersAndCertificates(String server, String metadataServer) {
    // We first resolve the IP of the server and then communicate with the server
    // Using IP only, because we have multiple server under same domain and we want
    // the client not to switch server during a test run
    //wehe4.meddle.mobi 90% returns 10.0.0.0 (use MLab), 10% legit IP (is Amazon)

    //extreme hack to temporarily get around French DNS look up issue (currently 100% MLab)
    if (server.equals("wehe4.meddle.mobi")) {
      servers.add("10.0.0.0");
      Log.d("Serverhack", "hacking wehe4");
    } else {
      servers.add(getServerIP(server));
      if (servers.get(servers.size() - 1).equals("")) {
        Log.ui("ERR_UNK_HOST", S.ERROR_UNKNOWN_HOST);
        return Consts.ERR_UNK_HOST;
      }
    }
    // A hacky way to check server IP version
    boolean serverIPisV6 = false;
    if (servers.get(0).contains(":")) {
      serverIPisV6 = true;
    }
    Log.d("ServerIPVersion", servers.get(0) + (serverIPisV6 ? "IPV6" : "IPV4"));
    //Connect to an MLab server if wehe4.meddle.mobi IP is 10.0.0.0 or if the client is using ipv6.
    // Steps to connect: 1) GET
    //request to MLab site to get MLab servers that can be connected to; 2) Parse first
    //server to get MLab server URL and the authentication URL to connect to; 3) Connect to
    //authentication URL with WebSocket; have connection open for entire test so SideChannel
    //server doesn't disconnect (for security). URL valid for connection for 2 min after GET
    //request made. 4) Connect to SideChannel with MLab machine URL. 5) Authentication URL
    //has another 2 min timeout after connecting; every MLab test needs to do this process.
    if (servers.get(0).equals("10.0.0.0") || serverIPisV6) {
      servers.clear();
      wsConns.clear();
      try {
        int numTries = 0; //tracks num tries before successful MLab connection
        JSONArray mLabServers = getMLabServerList();
        WebSocketConnection wsConn = null;
        for (int i = 0; wsConns.size() < Config.numServers && i < mLabServers.length(); i++) {
          try {
            numTries++;
            Log.d("WebSocket", "Attempting to connect to WebSocket " + i + ": " + server);

            JSONObject serverObj = (JSONObject) mLabServers.get(i); //get MLab server
            server = "wehe-" + serverObj.getString("machine"); //SideChannel URL
            String mLabURL = ((JSONObject) serverObj.get("urls"))
                    .getString(Consts.MLAB_WEB_SOCKET_SERVER_KEY); //authentication URL
            wsConn = new WebSocketConnection(i, new URI(mLabURL)); //connect to WebSocket

            //code below runs only if successful connection to WebSocket
            Log.d("WebSocket", "New WebSocket connectivity check: "
                    + (wsConn.isOpen() ? "CONNECTED" : "CLOSED") + " TO " + server);
            wsConns.add(wsConn);
            servers.add(getServerIP(server));
            numMLab.add(numTries);
            numTries = 0;
          } catch (URISyntaxException | JSONException | DeploymentException | NullPointerException
                  | InterruptedException e) {
            if (wsConn != null && wsConn.isOpen()) {
              wsConn.close();
            }
            Log.e("WebSocket", "Failed to connect to WebSocket: " + server, e);
          }
        }
      } catch (JSONException | NullPointerException e) {
        Log.e("WebSocket", "Can't retrieve M-Lab servers", e);
      } catch (NoTopologyFoundException e) {
        Log.ui("ERR_NO_TOPO", S.ERROR_NO_TOPO);
        return Consts.ERR_NO_TOPO;
      } catch (CertificateException e) {
        return Consts.ERR_CERT;
      }
      if (wsConns.size() < Config.numServers) {
        Log.ui("ERR_CONN_WS", S.ERROR_NO_WS);
        return Consts.ERR_CONN_WS;
      }
    }

    for (String srvr : servers) {
      if (srvr.equals("")) { //check to make sure IP was returned by getServerIP
        Log.ui("ERR_UNK_HOST", S.ERROR_UNKNOWN_HOST);
        return Consts.ERR_UNK_HOST;
      }
    }
    Log.d("GetReplayServerIP", "Server IPs: " + servers);
    if (!generateServerCertificate(true)) {
      return Consts.ERR_CERT;
    }

    //get URL for analysis and results
    int port = Config.result_port; //get port to send tests through
    for (String srvr : servers) {
      analyzerServerUrls.add("https://" + srvr + ":" + port + "/Results");
      Log.d("Result Channel", "path: " + srvr + " port: " + port);
    }

    if (metadataServer != null) {
      this.metadataServer = getServerIP(metadataServer);
      if (this.metadataServer.equals("")) { //get IP and certificates for metadata server
        Log.ui("ERR_UNK_META_HOST", S.ERROR_UNKNOWN_META_HOST);
        return Consts.ERR_UNK_META_HOST;
      }
      if (!generateServerCertificate(false)) {
        return Consts.ERR_CERT;
      }
    }
    return Consts.SUCCESS;
  }

  /**
   * This expection is specific to whether a Y-topology exist for the given client
   * If no topology found, localization test is not possible
   */
  class NoTopologyFoundException extends Exception {
    public NoTopologyFoundException() {
      super("No Y-topology found.");
    }
  }

  /**
   * Find MLab servers to connect to.
   * When running localization test with Y-shaped topology , the method perform two steps:
   *    1- Send Get (getServers) request to Wehe Analyzer server for server-pair site-info
   *    2- Use M-Lab locate service to retrieve the server keys
   * In other cases, the method returns the nearest Mlab server.
   *
   * @return JSONArray with the result of mlab locate service response
   */
  private JSONArray getMLabServerList() throws NoTopologyFoundException, CertificateException {
    JSONObject mLabResp = sendRequest(Config.mLabLocateServers, "GET", false, null, null);
    JSONArray mLabNearestServers = mLabResp.getJSONArray("results");

    if (!Config.useYTopology) {
      return mLabNearestServers;
    }

    //get URL for analysis and results
    int port = Config.result_port; //get port to send tests through

    for (int i = 0; i < mLabNearestServers.length(); i++) {
      JSONObject serverObj = mLabNearestServers.getJSONObject(i); //get MLab server
      String serverIP = getServerIP("wehe-" + serverObj.getString("machine"));
      String analyzerServerUrl = "https://" + serverIP + ":" + port + "/Results";
      Log.d("Result Channel For Y-topology search", "path: " + serverIP + " port: " + port);

      if (!generateServerCertificate(true)) {
        Log.ui("SHI", "certificate error");
        throw new CertificateException();
      }

      ArrayList<String> data = new ArrayList<>();
      data.add("command=" + "getServers");
      JSONObject resp = sendRequest(analyzerServerUrl, "GET", true, data, null);


      if (resp != null && resp.getBoolean("success")) {
        JSONArray serverPairs = resp.getJSONObject("response").getJSONArray("server-pairs");
        for (int j = 0; j < serverPairs.length(); j++) {
          JSONArray pair = serverPairs.getJSONArray(j);
          String mlabLocateURL = String.format("%s?site=%s&site=%s",
                  Config.mLabLocateServers, pair.getString(0), pair.getString(1));

          mLabResp = sendRequest(mlabLocateURL, "GET", false, null, null);
          if (mLabResp.getJSONArray("results").length() >= 2) {
            return mLabResp.getJSONArray("results");
          }
        }
        break;
      }
    }
    throw new NoTopologyFoundException();
  }

  /**
   * Does a DNS lookup on a hostname.
   *
   * @param server the hostname to be resolved
   * @return the IP of the host; empty string if there is an error doing so.
   */
  private String getServerIP(String server) {
    Log.d("getServerIP", "Server hostname: " + server);
    InetAddress address;
    for (int i = 0; i < 5; i++) { //5 attempts to lookup the IP
      try {
        server = InetAddress.getByName(server).getHostAddress(); //DNS lookup
        address = InetAddress.getByName(server);
        if (address instanceof Inet4Address) {
          return server;
        }
        if (address instanceof Inet6Address) {
          return "[" + server + "]";
        }
      } catch (UnknownHostException e) {
        if (i == 4) {
          Log.e("getServerIP", "Failed to get IP of server", e);
        } else {
          Log.w("getServerIP", "Failed to get IP of server, trying again");
        }
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ex) {
          Log.w("getServerIP", "Sleep interrupted", ex);
        }
      }
    }
    return "";
  }

  /**
   * Gets the certificates for the servers
   *
   * @param main true if main server; false if metadata server
   * @return true if certificates successfully generated; false otherwise
   */
  private boolean generateServerCertificate(boolean main) {
    try {
      String server = main ? "main" : "metadata";
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      Certificate ca;
      InputStream caInput = new FileInputStream(main ? Config.MAIN_CERT : Config.META_CERT);
      ca = cf.generateCertificate(caInput);
      Log.d("Certificate", server + "=" + ((X509Certificate) ca).getIssuerDN());

      // Create a KeyStore containing our trusted CAs
      String keyStoreType = KeyStore.getDefaultType();
      KeyStore keyStore = KeyStore.getInstance(keyStoreType);
      keyStore.load(null, null);
      keyStore.setCertificateEntry(server, ca);

      // Create a TrustManager that trusts the CAs in our KeyStore
      String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
      tmf.init(keyStore);

      // Create an SSLContext that uses our TrustManager
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, tmf.getTrustManagers(), null);
      if (main) {
        sslSocketFactory = context.getSocketFactory();
        hostnameVerifier = (hostname, session) -> true;
      }
    } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException
            | KeyManagementException | IOException e) {
      Log.e("Certificates", "Error generating certificates", e);
      Log.ui("ERR_CERT", S.ERROR_CERTS);
      return false;
    }
    return true;
  }

  /**
   * Get IP of user's device.
   *
   * @param port port to run replays
   * @return user's public IP or -1 if cannot connect to the server
   */
  private String getPublicIP(String port) {
    String publicIP = "127.0.0.1";

    if (servers.size() != 0 && !servers.get(0).equals("127.0.0.1")) {
      String url = "http://" + servers.get(0) + ":" + port + "/WHATSMYIPMAN";
      Log.d("getPublicIP", "url: " + url);

      int numFails = 0;
      while (publicIP.equals("127.0.0.1")) {
        try {
          URL u = new URL(url);
          //go to server
          HttpURLConnection conn = (HttpURLConnection) u.openConnection();
          conn.setConnectTimeout(3000);
          conn.setReadTimeout(5000);
          BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
          StringBuilder buffer = new StringBuilder();
          String input;

          while ((input = in.readLine()) != null) { //read IP address
            buffer.append(input);
          }
          in.close();
          conn.disconnect();
          publicIP = buffer.toString();
          InetAddress address = InetAddress.getByName(publicIP);
          if (!(address instanceof Inet4Address) && !(address instanceof Inet6Address)) {
            Log.e("getPublicIP", "wrong format of public IP: " + publicIP);
            throw new UnknownHostException();
          }
          isIPv6 = address instanceof Inet6Address;
          if (publicIP.equals("")) {
            publicIP = "-1";
          }
          Log.d("getPublicIP", "public IP: " + publicIP);
        } catch (UnknownHostException e) {
          Log.w("getPublicIP", "failed to get public IP!", e);
          publicIP = "127.0.0.1";
          break;
        } catch (IOException e) {
          Log.w("getPublicIP", "Can't connect to server");
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e1) {
            Log.w("getPublicIP", "Sleep interrupted", e1);
          }
          if (++numFails == 5) { //Cannot connect to server after 5 tries
            Log.w("getPublicIP", "Returning -1", e);
            publicIP = "-1";
            break;
          }
        }
      }
    } else {
      Log.w("getPublicIP", "Client IP is not available");
    }
    return publicIP;
  }

  /**
   * Asks the server for analysis of a replay. For apps, server compares random replay to original
   * replay. For ports, server compares port 443 to a non-443 port. The original replay and non-443
   * port have testId 0; the random replay and port 443 have testId 1. The server compares the
   * throughputs of testId 1 to testId 0 of the same history count. The server then determines if
   * there is differentiation and stores the result on the server.
   *
   * @param url          the url to the server where analysis will take place
   * @param id           the random ID assigned to specific user's device
   * @param historyCount the test to analyze
   * @return a JSONObject: { "success" : true | false }; true if server analyzes successfully
   */
  private JSONObject ask4analysis(String url, String id, int historyCount) {
    HashMap<String, String> pairs = new HashMap<>();

    pairs.put("command", "analyze");
    pairs.put("userID", id);
    pairs.put("historyCount", String.valueOf(historyCount));
    pairs.put("testID", "1");

    return sendRequest(url, "POST", true, null, pairs);
  }

  /**
   * Retrieves a replay result from the server that it previously was requested to analyze.
   *
   * @param url          the url of the server to get the result
   * @param id           the random ID assigned to a specific user's device
   * @param historyCount the test containing the replay to retrieve
   * @return a JSONObject with a key named "success". If value of "success" is false, a key named
   * "error" is also contained in the result. If the value of "success" is true, a key named
   * "response" is the result. The value of "response" contains several keys: "replayName", "date",
   * "userID", "extraString", "historyCount", "testID", "area_test", "ks2_ratio_test",
   * "xput_avg_original", "xput_avg_test", "ks2dVal", "ks2pVal"
   */
  private JSONObject getSingleResult(String url, String id, int historyCount) {
    ArrayList<String> data = new ArrayList<>();

    data.add("userID=" + id);
    data.add("command=" + "singleResult");
    data.add("historyCount=" + historyCount);
    data.add("testID=1");

    return sendRequest(url, "GET", true, data, null);
  }

  /**
   * Send a GET or POST request to the server.
   *
   * @param url    URL to the server
   * @param method either GET or POST
   * @param main   true if request is to main server; false otherwise
   * @param data   data to send to server in a GET request, null if a POST request or if no data to
   *               send to server
   * @param pairs  data to send to server in a POST request, null if a GET request
   * @return a response from the server in the form of a JSONObject
   */
  private JSONObject sendRequest(String url, String method, boolean main,
                                 ArrayList<String> data, HashMap<String, String> pairs) {
    final JSONObject[] json = {null};
    final HttpsURLConnection[] conn = new HttpsURLConnection[1];
    final boolean[] readyToReturn = {false};
    Thread serverComm = new Thread(new Runnable() {
      @Override
      public void run() {
        String url_string = url;
        if (method.equalsIgnoreCase("GET")) {
          if (data != null) {
            String dataURL = URLEncoder(data);
            url_string += "?" + dataURL;
          }
          Log.d("Send GET Request", url_string);

          for (int i = 0; i < 3; i++) {
            try {
              //connect to server
              URL u = new URL(url_string);
              //send data to server
              conn[0] = (HttpsURLConnection) u.openConnection();
              if (main && hostnameVerifier != null && sslSocketFactory != null) {
                conn[0].setHostnameVerifier(hostnameVerifier);
                conn[0].setSSLSocketFactory(sslSocketFactory);
              }
              conn[0].setConnectTimeout(8000);
              conn[0].setReadTimeout(8000);
              BufferedReader in = new BufferedReader(new InputStreamReader(
                      conn[0].getInputStream()));
              StringBuilder buffer = new StringBuilder();
              String input;

              // parse BufferReader rd to StringBuilder res
              while ((input = in.readLine()) != null) { //read response from server
                buffer.append(input);
              }

              in.close();
              conn[0].disconnect();
              json[0] = new JSONObject(buffer.toString()); // parse String to json file
              break;
            } catch (IOException e) {
              Log.e("Send Request", "sendRequest GET failed", e);
            } catch (JSONException e) {
              Log.e("Send Request", "JSON Parse failed", e);
            }
          }
        } else if (method.equalsIgnoreCase("POST")) {
          Log.d("Send POST Request", url_string);

          try {
            //connect to server
            URL u = new URL(url_string);
            conn[0] = (HttpsURLConnection) u.openConnection();
            conn[0].setHostnameVerifier(hostnameVerifier);
            conn[0].setSSLSocketFactory(sslSocketFactory);
            conn[0].setConnectTimeout(5000);
            conn[0].setReadTimeout(5000);
            conn[0].setRequestMethod("POST");
            conn[0].setDoInput(true);
            conn[0].setDoOutput(true);

            OutputStream os = conn[0].getOutputStream();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(os, StandardCharsets.UTF_8));
            writer.write(paramsToPostData(pairs)); //send data to server

            writer.flush();
            writer.close();
            os.close();

            BufferedReader in = new BufferedReader(new InputStreamReader(
                    conn[0].getInputStream()));
            StringBuilder buffer = new StringBuilder();
            String input;

            // parse BufferReader rd to StringBuilder res
            while ((input = in.readLine()) != null) { //read response from server
              buffer.append(input);
            }
            in.close();
            conn[0].disconnect();
            json[0] = new JSONObject(buffer.toString()); // parse String to json file.
          } catch (JSONException e) {
            Log.e("Send Request", "convert string to json failed", e);
            json[0] = null;
          } catch (IOException e) {
            Log.e("Send Request", "sendRequest POST failed", e);
            json[0] = null;
          }
        }
        readyToReturn[0] = true;
      }
    });
    serverComm.start();
    Timer t = new Timer();
    //timeout server after 8 sec; server timeout field times out only when nothing is sent;
    //if stuff sends too slowly, it could take forever, so this external timer prevents that
    t.schedule(new TimerTask() {
      @Override
      public void run() { //set timer to timeout the thread if max time has been reached for replay
        if (conn[0] != null) {
          conn[0].disconnect();
        }
        readyToReturn[0] = true;
      }
    }, 8000);
    timers.add(t);
    //wait until ready to move on (i.e. when result retrieved or timeout), as threads don't
    //block execution
    while (!readyToReturn[0]) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Log.w("Send Request", "Interrupted", e);
      }
    }
    return json[0];
  }

  /**
   * Overload URLEncoder to encode map to a url for a GET request.
   *
   * @param map data to be converted into a string to send to the server
   * @return encoded string containing data to send to server
   */
  private String URLEncoder(ArrayList<String> map) {
    StringBuilder data = new StringBuilder();
    for (String s : map) {
      if (data.length() > 0) {
        data.append("&");
      }
      data.append(s);
    }
    return data.toString();
  }

  /**
   * Encodes data into a string to send POST request to server.
   *
   * @param params data to convert into string to send to server
   * @return an encoded string to send to the server
   */
  private String paramsToPostData(HashMap<String, String> params) {
    StringBuilder result = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> entry : params.entrySet()) {
      if (first) {
        first = false;
      } else {
        result.append("&");
      }

      result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
      result.append("=");
      result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
    }
    return result.toString();
  }

  /**
   * Reads the replay files and loads them into memory as a bean.
   *
   * @param filename filename of the replay
   * @return a bean containing information about the replay
   */
  private CombinedAppJSONInfoBean unpickleJSON(String filename) {
    InputStream inputStream;
    CombinedAppJSONInfoBean appData = new CombinedAppJSONInfoBean(); //info about replay
    ArrayList<RequestSet> Q = new ArrayList<>(); //list of packets for replay
    try {
      inputStream = new FileInputStream(filename); //open replay file
      int size = inputStream.available();
      byte[] buffer = new byte[size];
      inputStream.read(buffer);
      inputStream.close();

      //convert file contents to JSONArray object
      String jsonStr = new String(buffer, StandardCharsets.UTF_8);
      JSONArray json = new JSONArray(jsonStr);

      JSONArray qArray = (JSONArray) json.get(0); //the packets in a replay file
      for (int i = 0; i < qArray.length(); i++) {
        RequestSet tempRS = new RequestSet();
        JSONObject dictionary = qArray.getJSONObject(i);
        tempRS.setc_s_pair(dictionary.getString("c_s_pair")); //client-server pair
        tempRS.setPayload(UtilsManager.hexStringToByteArray(dictionary.getString("payload")));
        tempRS.setTimestamp(dictionary.getDouble("timestamp"));

        //for tcp
        if (dictionary.has("response_len")) { //expected length of response
          tempRS.setResponse_len(dictionary.getInt("response_len"));
        }
        if (dictionary.has("response_hash")) {
          tempRS.setResponse_hash(dictionary.get("response_hash").toString());
        }
        //for udp
        if (dictionary.has("end"))
          tempRS.setEnd(dictionary.getBoolean("end"));

        Q.add(tempRS);
      }

      appData.setQ(Q);

      //udp
      JSONArray portArray = (JSONArray) json.get(1); //udp client ports
      ArrayList<String> portStrArray = new ArrayList<>();
      for (int i = 0; i < portArray.length(); i++) {
        portStrArray.add(portArray.getString(i));
      }
      appData.setUdpClientPorts(portStrArray);

      //for tcp
      JSONArray csArray = (JSONArray) json.get(2); //c_s_pairs
      ArrayList<String> csStrArray = new ArrayList<>();
      for (int i = 0; i < csArray.length(); i++) {
        csStrArray.add(csArray.getString(i));
      }
      appData.setTcpCSPs(csStrArray);
      appData.setReplayName(json.getString(3)); //name of replay
    } catch (JSONException | IOException e) {
      Log.e("UnpickleJSON", "Error reading test files", e);
    }
    return appData;
  }

  /**
   * Run test. This method is called for every app/port the user selects. It is also called if
   * differentiation is detected for a test, and confirmation setting is enabled to run a second
   * test for the app/port to confirm if there is differentiation.
   * <p>
   * Each test has two replays. For apps, the replays consist of the original replay, which contains
   * actual traffic from that app, and a random replay, which replaces the content of the original
   * replay with random traffic. For ports, the "original" replay is the port that is being tested.
   * The "random" replay is port 443. The method uses "open" to denote the "original" replay and
   * "random" to denote the "random" replay.
   * <p>
   * There are three main steps in this method: Step A: Flip a coin to decide which replay type to
   * run first. Step B: Run replays. Step C: Determine if there is differentiation.
   * <p>
   * Step B has several sub-steps which run for each replay: Step 0: Initialize variables. Step 1:
   * Tell server about the replay that is about to happen. Step 2: Ask server for permission to run
   * replay. Step 3: Send noIperf. Step 4: Send device info. Step 5: Get port mapping from server.
   * Step 6: Create TCP clients from CSPairs and UDP clients from client ports. Step 7: Start
   * notifier for UDP. Step 8: Start receiver to log throughputs on a given interval. Step 9: Send
   * packets to server. Step 10: Tell server that replay is finished. Step 11: Send throughputs and
   * slices to server. Step 12: Close side channel and TCP/UDP sockets.
   *
   * @return 0 if successful; error code otherwise
   */
  private int runTest() {
    String[] types;
    /*
     * Step A: Flip a coin to decide which replay type to run first.
     */
    //"random" test for ports is port 443
    if (Math.random() < 0.5) {
      types = new String[]{"open", "random"};
    } else {
      types = new String[]{"random", "open"};
    }

    /*
     * Step B: Run replays.
     */
    int iteration = 1;
    boolean portBlocked = false;
    for (String channel : types) {
      for (WebSocketConnection w : wsConns) { //if using MLab, check that still connected
        Log.d("WebSocket", "Before running test WebSocket (id: " + w.getId() + ") connectivity check: "
                + (w.isOpen() ? "CONNECTED" : "CLOSED"));
      }

      /*
       * Step 0: Initialize variables.
       */
      // Based on the type selected load open or random trace of given application
      if (channel.equalsIgnoreCase("open")) {
        this.appData = unpickleJSON(app.getDataFile());
      } else if (channel.equalsIgnoreCase("random")) {
        this.appData = unpickleJSON(app.getRandomDataFile());
      } else {
        Log.wtf("replayIndex", "replay name error: " + channel);
      }

      try {
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.CREATE_SIDE_CHANNEL);
        int sideChannelPort = Config.combined_sidechannel_port;

        Log.d("Servers", servers + " metadata " + metadataServer);
        // This side channel is used to communicate with the server in bytes mode and to
        // run traces, it send tcp and udp packets and receives the same from the server
        //Server handles communication in handle() function in server_replay.py in server
        //code
        ArrayList<CombinedSideChannel> sideChannels = new ArrayList<>();
        int id = 0;
        //each concurrent test gets its own sidechannel because each concurrent test is run on
        //a different server
        for (String server : servers) {
          sideChannels.add(new CombinedSideChannel(id, sslSocketFactory,
                  server, sideChannelPort, appData.isTCP()));
          id++;
        }

        ArrayList<JitterBean> jitterBeans = new ArrayList<>();
        for (CombinedSideChannel ignored : sideChannels) {
          jitterBeans.add(new JitterBean());
        }

        // initialize endOfTest value
        boolean endOfTest = false; //true if last replay in this test is running
        if (channel.equalsIgnoreCase(types[types.length - 1])) {
          Log.i("Replay", "last replay running " + types[types.length - 1] + "!");
          endOfTest = true;
        }

        //Get user's IP address
        String replayPort = "80";
        String ipThroughProxy = "127.0.0.1";
        if (appData.isTCP()) {
          for (String csp : appData.getTcpCSPs()) {
            replayPort = csp.substring(csp.lastIndexOf('.') + 1);
          }
          ipThroughProxy = getPublicIP(replayPort);
          if (ipThroughProxy.equals("-1")) { //port is blocked; move on to next replay
            portBlocked = true;
            iteration++;
            continue;
          }
        }

        // testId is how server knows if the trace ran was open or random
        testId = channel.equalsIgnoreCase("open") ? 0 : 1;

        if (doTest) {
          Log.w("Replay", "include -Test string");
        }

        /*
         * Step 1: Tell server about the replay that is about to happen.
         */
        int i = 0;
        for (CombinedSideChannel sc : sideChannels) {
          // This is group of values that is used to track traces on server
          // Youtube;False;0;DiffDetector;0;129.10.9.93;1.0
          //set extra string to number tries needed to access MLab server
          Config.extraString = numMLab.size() == 0 ? "0" : numMLab.get(i).toString();
          sc.declareID(appData.getReplayName(), endOfTest ? "True" : "False",
                  randomID, String.valueOf(historyCount), String.valueOf(testId),
                  doTest ? Config.extraString + "-Test" : Config.extraString,
                  ipThroughProxy, Consts.VERSION_NAME);

          // This tuple tells the server if the server should operate on packets of traces
          // and if so which packets to process
          sc.sendChangeSpec(-1, "null", "null");
          i++;
        }

        /*
         * Step 2: Ask server for permission to run replay.
         */
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.ASK4PERMISSION);
        // Now to move forward we ask for server permission
        ArrayList<Integer> numOfTimeSlices = new ArrayList<>();
        for (CombinedSideChannel sc : sideChannels) {
          String[] permission = sc.ask4Permission();
          String status = permission[0].trim();

          Log.d("Replay", "Channel " + sc.getId() + ": permission[0]: " + status
                  + " permission[1]: " + permission[1]);

          String permissionError = permission[1].trim();
          if (status.equals("0")) {
            int errorCode;
            // These are the different errors that server can report
            switch (permissionError) {
              case "1": //server cannot identify replay
                Log.ui("ERR_PERM_REPLAY", S.ERROR_UNKNOWN_REPLAY);
                errorCode = Consts.ERR_PERM_REPLAY;
                break;
              case "2": //only one replay can run at a time per IP
                Log.ui("ERR_PERM_IP", S.ERROR_IP_CONNECTED);
                errorCode = Consts.ERR_PERM_IP;
                break;
              case "3": //server CPU > 95%, disk > 95%, or bandwidth > 2000 Mbps
                Log.ui("ERR_PERM_RES", S.ERROR_LOW_RESOURCES);
                errorCode = Consts.ERR_PERM_RES;
                break;
              default:
                Log.ui("ERR_PERM_UNK", S.ERROR_UNKNOWN);
                errorCode = Consts.ERR_PERM_UNK;
            }
            return errorCode;
          }

          numOfTimeSlices.add(Integer.parseInt(permission[2].trim(), 10));
        }

        /*
         * Step 3: Send noIperf.
         */
        for (CombinedSideChannel sc : sideChannels) {
          sc.sendIperf(); // always send noIperf here
        }

        /*
         * Step 4: Send device info.
         */
        for (CombinedSideChannel sc : sideChannels) {
          sc.sendMobileStats(Config.sendMobileStats);
        }

        /*
         * Step 5: Get port mapping from server.
         */
        /*
         * Ask for port mapping from server. For some reason, port map
         * info parsing was throwing error. so, I put while loop to do
         * this until port mapping is parsed successfully.
         */
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.RECEIVE_SERVER_PORT_MAPPING);

        ArrayList<HashMap<String, HashMap<String, HashMap<String, ServerInstance>>>> serverPortsMaps
                = new ArrayList<>();
        ArrayList<UDPReplayInfoBean> udpReplayInfoBeans = new ArrayList<>();
        for (CombinedSideChannel sc : sideChannels) {
                serverPortsMaps.add(sc.receivePortMappingNonBlock());
          UDPReplayInfoBean udpReplayInfoBean = new UDPReplayInfoBean();
          udpReplayInfoBean.setSenderCount(sc.receiveSenderCount());
          udpReplayInfoBeans.add(udpReplayInfoBean);
          Log.i("Replay", "Channel " + sc.getId() + ": Successfully received"
                  + " serverPortsMap and senderCount!");
        }

        /*
         * Step 6: Create TCP clients from CSPairs and UDP clients from client ports.
         */
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.CREATE_TCP_CLIENT);

        //map of all cs pairs to TCP clients for a replay
        ArrayList<HashMap<String, CTCPClient>> CSPairMappings = new ArrayList<>();

        //create TCP clients
        for (CombinedSideChannel sc : sideChannels) {
          HashMap<String, CTCPClient> CSPairMapping = new HashMap<>();
          for (String csp : appData.getTcpCSPs()) {
            //get server IP and port
            String destIP = csp.substring(csp.lastIndexOf('-') + 1,
                    csp.lastIndexOf("."));
            String destPort = csp.substring(csp.lastIndexOf('.') + 1);
            //pad port to 5 digits with 0s; ex. 00443 or 00080
            destPort = String.format("%5s", destPort).replace(' ', '0');

            //get the server
            ServerInstance instance;
            try {
              instance = Objects.requireNonNull(Objects.requireNonNull(
                      serverPortsMaps.get(sc.getId()).get("tcp")).get(destIP)).get(destPort);
              assert instance != null;
            } catch (NullPointerException | AssertionError e) {
              Log.e("Replay", "Cannot get instance", e);
              Log.ui("ERR_CONN_INST", S.ERROR_NO_CONNECTION);
              return Consts.ERR_CONN_INST;
            }
            if (instance.server.trim().equals(""))
              // Use a setter instead probably
              instance.server = servers.get(sc.getId()); // serverPortsMap.get(destPort);

            //create the client
            CTCPClient c = new CTCPClient(csp, instance.server,
                    Integer.parseInt(instance.port),
                    appData.getReplayName(), Config.publicIP, false);
            CSPairMapping.put(csp, c);
          }
          CSPairMappings.add(CSPairMapping);
          Log.i("Replay", "Channel " + sc.getId() + ": created clients from CSPairs");
          Log.d("Replay", "Channel " + sc.getId() + ": Size of CSPairMapping is "
                  + CSPairMapping.size());
        }

        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.CREATE_UDP_CLIENT);

        //map of all client ports to UDP clients for a replay
        ArrayList<HashMap<String, CUDPClient>> udpPortMappings = new ArrayList<>();

        //create client for each UDP port
        for (CombinedSideChannel sc : sideChannels) {
          HashMap<String, CUDPClient> udpPortMapping = new HashMap<>();
          for (String originalClientPort : appData.getUdpClientPorts()) {
            CUDPClient c = new CUDPClient(Config.publicIP);
            udpPortMapping.put(originalClientPort, c);
          }
          udpPortMappings.add(udpPortMapping);
          Log.i("Replay", "Channel " + sc.getId() + ": created clients from udpClientPorts");
          Log.d("Replay", "Channel " + sc.getId() + ": Size of udpPortMapping is "
                  + udpPortMapping.size());
        }

        /*
         * Step 7: Start notifier for UDP.
         */
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.RUN_NOTF);

        ArrayList<CombinedNotifierThread> notifiers = new ArrayList<>();
        ArrayList<Thread> notfThreads = new ArrayList<>();
        for (CombinedSideChannel sc : sideChannels) {
          CombinedNotifierThread notifier = sc.notifierCreator(udpReplayInfoBeans.get(sc.getId()));
          notifiers.add(notifier);
          Thread notfThread = new Thread(notifier);
          notfThread.start();
          notfThreads.add(notfThread);
        }

        /*
         * Step 8: Start receiver to log throughputs on a given interval.
         */
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.RUN_RECEIVER);

        ArrayList<CombinedAnalyzerTask> analyzerTasks = new ArrayList<>();
        ArrayList<Timer> analyzerTimers = new ArrayList<>();
        ArrayList<CombinedReceiverThread> receivers = new ArrayList<>();
        ArrayList<Thread> rThreads = new ArrayList<>();
        for (CombinedSideChannel sc : sideChannels) {
          CombinedAnalyzerTask analyzerTask = new CombinedAnalyzerTask(app.getTime() / 2.0,
                  appData.isTCP(), numOfTimeSlices.get(sc.getId()), runPortTests); //throughput logged
          Timer analyzerTimer = new Timer(true); //timer to log throughputs on interval
          analyzerTimer.scheduleAtFixedRate(analyzerTask, 0, analyzerTask.getInterval());
          analyzerTasks.add(analyzerTask);
          analyzerTimers.add(analyzerTimer);

          CombinedReceiverThread receiver = new CombinedReceiverThread(
                  udpReplayInfoBeans.get(sc.getId()), jitterBeans.get(sc.getId()), analyzerTask); //receiver for udp
          receivers.add(receiver);
          Thread rThread = new Thread(receiver);
          rThread.start();
          rThreads.add(rThread);
        }

        /*
         * Step 9: Send packets to server.
         */
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.RUN_SENDER);

        CombinedQueue queue = new CombinedQueue(appData.getQ(), jitterBeans, analyzerTasks,
                runPortTests ? Consts.REPLAY_PORT_TIMEOUT : Consts.REPLAY_APP_TIMEOUT);
        long timeStarted = System.nanoTime(); //start time for sending
        //send packets
        ArrayList<HashMap<String, HashMap<String, ServerInstance>>> udpServerMappings = new ArrayList<>();
        for (HashMap<String, HashMap<String, HashMap<String, ServerInstance>>> m : serverPortsMaps) {
          udpServerMappings.add(m.get("udp"));
        }

        queue.run(CSPairMappings, udpPortMappings, udpReplayInfoBeans, udpServerMappings,
                Config.timing, servers);

        //all packets sent - stop logging and receiving
        queue.stopTimers();
        for (Timer t : analyzerTimers) {
          t.cancel();
        }
        for (CombinedNotifierThread n : notifiers) {
          n.doneSending = true;
        }
        for (Thread t : notfThreads) {
          t.join();
        }
        for (CombinedReceiverThread r : receivers) {
          r.keepRunning = false;
        }
        for (Thread t : rThreads) {
          t.join();
        }

        /*
         * Step 10: Tell server that replay is finished.
         */
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.SEND_DONE);

        //time to send all packets
        double duration = ((double) (System.nanoTime() - timeStarted)) / 1000000000;
        for (CombinedSideChannel sc : sideChannels) {
          sc.sendDone(duration);
        }
        Log.d("Replay", "replay finished using time " + duration + " s");

        /*
         * Step 11: Send throughputs and slices to server.
         */
        for (CombinedSideChannel sc : sideChannels) {
          sc.sendTimeSlices(analyzerTasks.get(sc.getId()).getAverageThroughputsAndSlices());
        }

        //TODO: is this necessary?
        //set avg of port 443, so it can be displayed if port being tested is blocked
        if (runPortTests && channel.equalsIgnoreCase("random")) {
          app.randomThroughput = analyzerTasks.get(0).getAvgThroughput();
        }

        // TODO find a better way to do this
        // Send Result;No and wait for OK before moving forward
        for (CombinedSideChannel sc : sideChannels) {
          while (sc.getResult(Config.result)) {
            Thread.sleep(500);
          }
        }

        /*
         * Step 12: Close side channel and TCP/UDP sockets.
         */
        // closing side channel socket
        for (CombinedSideChannel sc : sideChannels) {
          sc.closeSideChannelSocket();
        }

        //close TCP sockets
        for (HashMap<String, CTCPClient> mapping : CSPairMappings) {
          for (String csp : appData.getTcpCSPs()) {
            CTCPClient c = mapping.get(csp);
            if (c != null) {
              c.close();
            }
          }
        }
        Log.i("CleanUp", "Closed CSPairs 1");

        //close UDP sockets
        for (HashMap<String, CUDPClient> mapping : udpPortMappings) {
          for (String originalClientPort : appData.getUdpClientPorts()) {
            CUDPClient c = mapping.get(originalClientPort);
            if (c != null) {
              c.close();
            }
          }
        }

        Log.i("CleanUp", "Closed CSPairs 2");
        iteration++;
      } catch (InterruptedException e) {
        Log.w("Replay", "Replay interrupted!", e);
      } catch (IOException e) { //something wrong with receiveKbytes() or constructor in CombinedSideChannel
        Log.e("Replay", "Some IO issue with server", e);
        Log.ui("ERR_CONN_IO_SERV", S.ERROR_NO_CONNECTION);
        return Consts.ERR_CONN_IO_SERV;
      }
    }

    /*
     * Step C: Determine if there is differentiation.
     */
    return getResults(portBlocked);
  }

  /**
   * Determines if there is differentiation. If app is running, result of random test is compared
   * against original test. If port is running, result of port test is compared against port 443.
   * <p>
   * If results are inconclusive or have differentiation, a confirmation test will run if the
   * confirmation setting is switched on.
   * <p>
   * For port tests, Step 1 and Step 2 are skipped if a port is blocked, as no throughputs are sent
   * to the server to analyze. A response is created for Step 3 instead of retrieving from the
   * server. When a port is blocked, the port throughput is 0 Mbps, while port 443 (which should not
   * be blocked) has a throughput, which is calculated in Step 11 of runTest().
   * <p>
   * Step 1: Ask sever to analyze a test. Step 2: Get result of analysis from server. Step 3: Parse
   * the analysis results. Step 4: Determine if there is differentiation. Step 5: Save and display
   * results to user. Rerun test if necessary.
   *
   * @param portBlocked true if a port in the port tests is blocked; false otherwise
   * @return 0 if successful; otherwise error code
   */
  private int getResults(boolean portBlocked) {
    try {
      ArrayList<JSONObject> results = new ArrayList<>();
      if (!portBlocked) { //skip Step 1 and step 2 if port blocked
        /*
         * Step 1: Ask server to analyze a test.
         */
        JSONObject resp;
        for (String server : analyzerServerUrls) {
          for (int ask4analysisRetry = 3; ask4analysisRetry > 0; ask4analysisRetry--) {
            resp = ask4analysis(server, randomID, app.getHistoryCount()); //request analysis
            if (resp == null) {
              Log.e("Result Channel", server + ": ask4analysis returned null!");
            } else {
              results.add(resp);
              break;
            }
          }
        }

        if (results.size() != analyzerServerUrls.size()) {
          Log.ui("ERR_ANA_NULL", S.ERROR_ANALYSIS_FAIL);
          return Consts.ERR_ANA_NULL;
        }

        boolean success;
        for (JSONObject result : results) {
          success = result.getBoolean("success");
          if (!success) {
            Log.e("Result Channel", "ask4analysis failed!");
            Log.ui("ERR_ANA_NO_SUC", S.ERROR_ANALYSIS_FAIL);
            return Consts.ERR_ANA_NO_SUC;
          }
        }

        Log.ui("updateStatus", S.WAITING);

        // sanity check
        if (app.getHistoryCount() < 0) {
          Log.e("Result Channel", "historyCount value not correct!");
          Log.ui("ERR_ANA_HIST_CT", S.ERROR_ANALYSIS_FAIL);
          return Consts.ERR_ANA_HIST_CT;
        }

        Log.i("Result Channel", "ask4analysis succeeded!");

        /*
         * Step 2: Get result of analysis from server.
         */
        results.clear();
        String resultStatus;
        int exitStatus;
        for (String url : analyzerServerUrls) {
          for (int i = 0; ; i++) { //3 attempts to get analysis from sever
            resp = getSingleResult(url, randomID, app.getHistoryCount()); //get result

            if (resp == null) {
              resultStatus = "ERR_RSLT_NULL";
              exitStatus = Consts.ERR_RSLT_NULL;
              Log.e("Result Channel", url + ": getSingleResult returned null!");
            } else {
              success = resp.getBoolean("success");
              if (success) { //success
                if (resp.has("response")) { //success and has response
                  Log.i("Result Channel", url + ": retrieve result succeeded");
                  results.add(resp);
                  break;
                } else { //success but response is missing
                  resultStatus = "ERR_RSLT_NO_RESP";
                  exitStatus = Consts.ERR_RSLT_NO_RESP;
                  Log.w("Result Channel", url + ": Server result not ready");
                }
              } else if (resp.has("error")) {
                resultStatus = "ERR_RSLT_NO_SUC";
                exitStatus = Consts.ERR_RSLT_NO_SUC;
                Log.e("Result Channel", "ERROR: " + url + ": " + resp.getString("error"));
              } else {
                resultStatus = "ERR_RSLT_NO_SUC";
                exitStatus = Consts.ERR_RSLT_NO_SUC;
                Log.e("Result Channel", "Error: Some error getting results.");
              }
            }

            if (i < 3) { //wait 2 seconds to try again
              try {
                Thread.sleep(2000);
              } catch (InterruptedException e) {
                Log.w("Result Channel", "Sleep interrupted", e);
              }
            } else { //error after 3rd attempt
              if (runPortTests) { //"the port 80 issue"
                portBlocked = true;
                Log.i("Result Channel", "Can't retrieve result, port blocked");
                break;
              } else {
                Log.ui(resultStatus, S.NOT_ALL_TCP_SENT_TEXT);
                return exitStatus;
              }
            }
          }
        }
      }

      /*
       * Step 3: Parse the analysis results.
       */
      int i = 0;
      for (JSONObject result : results) {
        JSONObject response = portBlocked ? new JSONObject() : result.getJSONObject("response");

        if (portBlocked) { //generate result if port blocked
          response.put("userID", randomID);
          response.put("historyCount", historyCount);
          response.put("replayName", app.getDataFile());
          response.put("area_test", -1);
          response.put("ks2pVal", -1);
          response.put("ks2_ratio_test", -1);
          response.put("xput_avg_original", 0);
          response.put("xput_avg_test", app.randomThroughput); //calculated in runTest() Step 11
        }

        Log.d("Result Channel", "SERVER RESPONSE " + i++ + ": " + response.toString());

        String userID = response.getString("userID");
        int historyCount = response.getInt("historyCount");
        double area_test = response.getDouble("area_test");
        double ks2pVal = response.getDouble("ks2pVal");
        double ks2RatioTest = response.getDouble("ks2_ratio_test");
        double xputOriginal = response.getDouble("xput_avg_original");
        double xputTest = response.getDouble("xput_avg_test");

        // sanity check
        if ((!userID.trim().equalsIgnoreCase(randomID))
                || (historyCount != app.getHistoryCount())) {
          Log.e("Result Channel", "Result didn't pass sanity check! "
                  + "correct id: " + randomID
                  + " correct historyCount: " + app.getHistoryCount());
          Log.e("Result Channel", "Result content: " + response.toString());
          Log.ui("ERR_RSLT_ID_HC", S.ERROR_RESULT);
          return Consts.ERR_RSLT_ID_HC;
        }

        /*
         * Step 4: Determine if there is differentiation.
         */
        //area test threshold default is 50%; ks2 p value test threshold default is 1%
        //if default switch is on and one of the throughputs is over 10 Mbps, change the
        //area threshold to 30%, which increases chance of Wehe finding differentiation.
        //If the throughputs are over 10 Mbps, the difference between the two throughputs
        //would need to be much larger than smaller throughputs for differentiation to be
        //triggered, which may confuse users
        //TODO: might have to relook at thresholds and do some formal research on optimal
        // thresholds. Currently thresholds chosen ad-hoc
        if (useDefaultThresholds && (xputOriginal > 10 || xputTest > 10)) {
          a_threshold = 30;
        }

        double area_test_threshold = (double) a_threshold / 100;
        double ks2pVal_threshold = (double) ks2pvalue_threshold / 100;
        //double ks2RatioTest_threshold = (double) 95 / 100;

        boolean aboveArea = Math.abs(area_test) >= area_test_threshold;
        //boolean trustPValue = ks2RatioTest >= ks2RatioTest_threshold;
        boolean belowP = ks2pVal < ks2pVal_threshold;
        boolean differentiation = false;
        boolean inconclusive = false;

        if (portBlocked) {
          differentiation = true;
        } else if (aboveArea) {
          if (belowP) {
            differentiation = true;
          } else {
            inconclusive = true;
          }
        }

        // TODO uncomment following code when you want differentiation to occur
        //differentiation = true;
        //inconclusive = true;

        /*
         * Step 5: Save and display results to user. Rerun test if necessary.
         */
        //determine if the test needs to be rerun
        if ((inconclusive || differentiation) && confirmationReplays && !rerun) {
          Log.ui("updateStatus", S.CONFIRMATION_REPLAY);
          try { //wait 2 seconds so user can read message before it disappears
            Thread.sleep(2000);
          } catch (InterruptedException e) {
            Log.w("Result Channel", "Sleep interrupted", e);
          }
          rerun = true;
          return runTest(); //return so that first result isn't saved
        }

        String saveStatus; //save to disk, so it can appear in the correct language in prev results
        if (inconclusive) {
          saveStatus = "inconclusive";
          Log.ui("RESULT", S.INCONCLUSIVE);
        } else if (differentiation) {
          saveStatus = "has diff";
          Log.ui("RESULT", S.HAS_DIFF);

          String error = runPortTests ? S.TEST_BLOCKED_PORT_TEXT : S.TEST_BLOCKED_APP_TEXT;
          if (!portBlocked) {
            if (xputOriginal > xputTest) {
              error = runPortTests ? S.TEST_PRIORITIZED_PORT_TEXT : S.TEST_PRIORITIZED_APP_TEXT;
            } else {
              error = runPortTests ? S.TEST_THROTTLED_PORT_TEXT : S.TEST_THROTTLED_APP_TEXT;
            }
          }
          Log.ui("RESULT_MSG", error);
        } else {
          saveStatus = "no diff";
          Log.ui("RESULT", S.NO_DIFF);
        }

        app.area_test = area_test;
        app.ks2pVal = ks2pVal;
        app.ks2pRatio = ks2RatioTest;
        app.originalThroughput = xputOriginal;
        app.randomThroughput = xputTest;

        Log.i("Result Channel", "writing result to json array");
        response.put("isPort", runPortTests);
        response.put("appName", app.getName());
        response.put("appImage", app.getImage());
        response.put("status", saveStatus);
        response.put("date", new Date().getTime());
        response.put("areaThreshold", area_test_threshold);
        response.put("ks2pThreshold", ks2pVal_threshold);
        response.put("isIPv6", isIPv6);
        response.put("server", serverDisplay);
        response.put("carrier", "myCarrier");
        Log.d("response", response.toString());

        Log.ui("FinalResults", response.toString()); //save user results to UI log file
      }
    } catch (JSONException e) {
      Log.e("Result Channel", "parsing json error", e);
      Log.ui("ERR_BAD_JSON", S.ERROR_JSON);
      return Consts.ERR_BAD_JSON;
    }
    return Consts.SUCCESS;
  }
}
