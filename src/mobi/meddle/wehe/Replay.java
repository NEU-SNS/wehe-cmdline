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
 * Runs the replays. XML layout: activity_replay.xml adapter.ImageReplayRecyclerViewAdapter.java for
 * layout of each replay
 */
public class Replay {
  private final boolean runPortTests;
  private boolean isIPv6; //true if user's public IP is v6, use to display in results
  private final String serverDisplay; //server to display in the results
  //---------------------------------------------------
  private CombinedAppJSONInfoBean appData;
  private ApplicationBean app;
  private String server; //server to run the replays to
  private String metadataServer;
  private WebSocketConnection wsConn = null;
  private boolean doTest; //add a tail for testing data if true
  private String analyzerServerUrl;
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
  private boolean success = false;
  private final ArrayList<Timer> timers = new ArrayList<>();

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
   */
  public boolean beginTest() throws IOException {
    /*
     * Step 1: Initialize several variables.
     */
    String[] info = Log.readInfo().split(";");
    historyCount = Integer.parseInt(info[1]);
    // update historyCount
    historyCount++;

    randomID = info[0];
    // This random ID is used to map the test results to a specific instance of app
    // It is generated only once and saved thereafter
    if (randomID == null) {
      Log.e("RecordReplay", "randomID does not exist!");
      Log.ui("ERR_NO_ID", S.ERROR_NO_USER_ID);
      return success;
    }

    //write randomID and updated historyCount to the info file
    Log.incHistoryCount();
    Log.writeInfo();
    Log.d("Replay", "historyCount: " + historyCount);

    app = parseAppJSON();
    if (app == null) {
      cleanUp();
      Log.wtf("noSuchApp", "Either there is no app named \"" + appName + "\", or a file"
              + " could not be found.");
      return success;
    }

    //write current historyCount to applicationBean
    app.setHistoryCount(historyCount);

    // metadata here is user's network type device used geolocation if permitted etc
    metadataServer = Consts.METADATA_SERVER;
    if (!setupServersAndCertificates(serverDisplay, metadataServer)) {
      cleanUp();
      return success;
    }

    testId = -1;
    doTest = false;

    //timing allows replays to be run with the same timing as when they were recorded
    //for example, if a YouTube video was paused for 2 seconds during recording, then the
    //replay will also pause for 2 seconds at that point in the replay
    //port tests try to run as fast as possible, so there is no timing for them
    Config.timing = !runPortTests;
    Config.server = server;
    String publicIP = getPublicIP("80"); //get user's IP address
    Config.publicIP = publicIP;
    Log.d("Replay", "public IP: " + publicIP);
    //If cannot connect to server, display an error and stop tests
    if (publicIP.equals("-1")) {
      cleanUp();
      Log.ui("ERR_CONN_IP", S.ERROR_NO_CONNECTION);
      return success;
    }

    /*
     * Step 2: Run tests.
     */
    runTest(); // Run the test on this.app
    cleanUp();

    Log.ui("Finished", S.REPLAY_FINISHED_TITLE);
    Log.i("Result Channel", "Exiting normally");
    return success;
  }

  /**
   * Close connections and cancel timers before exiting.
   */
  private void cleanUp() {
    if (wsConn != null && wsConn.isOpen()) {
      wsConn.close();
    }
    for (Timer t : timers) {
      t.cancel();
    }
  }

  /**
   * This method parses apps_list.json file located in assets folder. This file has all the basic
   * details of apps for replay.
   */
  private ApplicationBean parseAppJSON() {
    try {
      StringBuilder buf = new StringBuilder();
      File appInfo = new File(Config.APPS_FILENAME);
      Path tests_info_file = Paths.get(Config.APPS_FILENAME);
      if (!Files.exists(tests_info_file)) {
        Log.e("Load test", "\"" + Config.APPS_FILENAME + "\" file not found.");
        return null;
      }

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
    } catch (IOException ex) {
      Log.d(Consts.LOG_APP_NAME,
              "IOException while reading file " + Config.APPS_FILENAME);
      ex.printStackTrace();
    } catch (JSONException ex) {
      Log.d(Consts.LOG_APP_NAME,
              "JSONException while parsing JSON file " + Config.APPS_FILENAME);
      ex.printStackTrace();
    }
    return null;
  }

  /**
   * Gets IPs of server and metadata server. Connects to MLab authentication WebSocket if necessary.
   * Gets necessary certificates for server and metadata server.
   *
   * @param server         the hostname of the server to connect to
   * @param metadataServer the host name of the metadata server to connect to
   * @return true if everything properly sets up; false otherwise
   */
  private boolean setupServersAndCertificates(String server, String metadataServer) {
    // We first resolve the IP of the server and then communicate with the server
    // Using IP only, because we have multiple server under same domain and we want
    // the client not to switch server during a test run
    //wehe4.meddle.mobi 90% returns 10.0.0.0 (use MLab), 10% legit IP (is Amazon)

    //extreme hack to temporarily get around French DNS look up issue
    if (server.equals("wehe4.meddle.mobi")) {
      this.server = "10.0.0.0";
      Log.d("Serverhack", "hacking wehe4");
    } else {
      this.server = getServerIP(server);
    }
    // A hacky way to check server IP version
    boolean serverIPisV6 = false;
    if (this.server.contains(":")) {
      serverIPisV6 = true;
    }
    Log.d("ServerIPVersion", this.server + (serverIPisV6 ? "IPV6" : "IPV4"));
    //Connect to an MLab server if wehe4.meddle.mobi IP is 10.0.0.0 or if the client is using ipv6.
    // Steps to connect: 1) GET
    //request to MLab site to get MLab servers that can be connected to; 2) Parse first
    //server to get MLab server URL and the authentication URL to connect to; 3) Connect to
    //authentication URL with WebSocket; have connection open for entire test so SideChannel
    //server doesn't disconnect (for security). URL valid for connection for 2 min after GET
    //request made. 4) Connect to SideChannel with MLab machine URL. 5) Authentication URL
    //has another 2 min timeout after connecting; every MLab test needs to do this process.
    if (this.server.equals("10.0.0.0") || serverIPisV6) {
      JSONObject mLabResp = sendRequest(Consts.MLAB_SERVERS, "GET", false, null, null);
      boolean webSocketConnected = false;
      int i = 0;
      while (!webSocketConnected) { //try the 4 servers before going to wehe2
        try {
          JSONArray servers = (JSONArray) mLabResp.get("results"); //get MLab servers list
          JSONObject serverObj = (JSONObject) servers.get(i); //get first MLab server
          server = "wehe-" + serverObj.getString("machine"); //SideChannel URL
          this.server = getServerIP(server);
          String mLabURL = ((JSONObject) serverObj.get("urls"))
                  .getString(Consts.MLAB_WEB_SOCKET_SERVER_KEY); //authentication URL
          wsConn = new WebSocketConnection(new URI(mLabURL)); //connect to WebSocket
          Log.d("WebSocket", "New WebSocket connectivity check: "
                  + (wsConn.isOpen() ? "CONNECTED" : "CLOSED") + " TO " + server);
          webSocketConnected = true;
        } catch (URISyntaxException | JSONException | DeploymentException | NullPointerException | InterruptedException e) {
          if (wsConn != null && wsConn.isOpen()) {
            wsConn.close();
          }
          Log.w("WebSocket", "Can't connect to WebSocket", e);
          if (i == Consts.MLAB_NUM_TRIES_TO_CONNECT - 1) {
            //if can't connect to mlab, try an amazon server using wehe2.meddle.mobi
            Log.i("GetReplayServerIP", "Can't get MLab server, trying Amazon");
            this.server = getServerIP("wehe2.meddle.mobi");
            webSocketConnected = true;
          }
          i++;
        }
      }
    }

    if (this.server.equals("")) { //check to make sure IP was returned by getServerIP
      Log.ui("ERR_UNK_HOST_NO_SERVER", S.ERROR_UNKNOWN_HOST);
      if (wsConn != null && wsConn.isOpen()) {
        wsConn.close();
      }
      return false;
    }
    Log.d("GetReplayServerIP", "Server IP: " + this.server);
    generateServerCertificate(true);

    //get URL for analysis and results
    int port = Config.result_port; //get port to send tests through
    analyzerServerUrl = ("https://" + this.server + ":" + port + "/Results");
    Log.d("Result Channel", "path: " + this.server + " port: " + port);

    if (metadataServer != null) {
      this.metadataServer = getServerIP(metadataServer);
      if (this.metadataServer.equals("")) { //get IP and certificates for metadata server
        Log.ui("ERR_UNK_HOST_NO_META_SERVER", S.ERROR_UNKNOWN_META_HOST);
        return false;
      }
      generateServerCertificate(false);
    }
    return true;
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
          ex.printStackTrace();
        }
      }
    }
    return "";
  }

  /**
   * Gets the certificates for the servers
   *
   * @param main true if main server; false if metadata server
   */
  private void generateServerCertificate(boolean main) {
    try {
      String server = main ? "main" : "metadata";
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      Certificate ca;
      try (InputStream caInput = new FileInputStream(main ? Config.MAIN_CERT : Config.META_CERT)) {
        ca = cf.generateCertificate(caInput);
        Log.d("Certificate", server + "=" + ((X509Certificate) ca).getIssuerDN());
      }

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
      e.printStackTrace();
    }
  }

  /**
   * Get IP of user's device.
   *
   * @param port port to run replays
   * @return user's public IP or -1 if cannot connect to the server
   */
  private String getPublicIP(String port) {
    String publicIP = "127.0.0.1";

    if (server != null && !server.equals("127.0.0.1")) {
      String url = "http://" + server + ":" + port + "/WHATSMYIPMAN";
      Log.d("getPublicIP", "url: " + url);

      int numFails = 0;
      while (publicIP.equals("127.0.0.1")) {
        try {
          URL u = new URL(url);
          //go to server
          HttpURLConnection conn = (HttpURLConnection) u.openConnection();
          conn.setConnectTimeout(3000);
          conn.setReadTimeout(5000);
          BufferedReader in = new BufferedReader(new InputStreamReader(
                  conn.getInputStream()));
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
            e1.printStackTrace();
          }
          if (++numFails == 5) { //Cannot connect to server after 5 tries
            Log.w("getPublicIP", "Returning -1", e);
            publicIP = "-1";
            break;
          }
        }
      }
    } else {
      Log.w("getPublicIP", "server ip is not available: " + server);
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
   * @param id           the random ID assigned to specific user's device
   * @param historyCount the test to analyze
   * @return a JSONObject: { "success" : true | false }; true if server analyzes successfully
   */
  private JSONObject ask4analysis(String id, int historyCount) {
    HashMap<String, String> pairs = new HashMap<>();

    pairs.put("command", "analyze");
    pairs.put("userID", id);
    pairs.put("historyCount", String.valueOf(historyCount));
    pairs.put("testID", "1");

    return sendRequest(analyzerServerUrl, "POST", true, null, pairs);
  }

  /**
   * Retrieves a replay result from the server that it previously was requested to analyze.
   *
   * @param id           the random ID assigned to a specific user's device
   * @param historyCount the test containing the replay to retrieve
   * @return a JSONObject with a key named "success". If value of "success" is false, a key named
   * "error" is also contained in the result. If the value of "success" is true, a key named
   * "response" is the result. The value of "response" contains several keys: "replayName", "date",
   * "userID", "extraString", "historyCount", "testID", "area_test", "ks2_ratio_test",
   * "xput_avg_original", "xput_avg_test", "ks2dVal", "ks2pVal"
   */
  private JSONObject getSingleResult(String id, int historyCount) {
    ArrayList<String> data = new ArrayList<>();

    data.add("userID=" + id);
    data.add("command=" + "singleResult");
    data.add("historyCount=" + historyCount);
    data.add("testID=1");

    return sendRequest(analyzerServerUrl, "GET", true, data, null);
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
          Log.d("Send Request", url_string);

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
          Log.d("Send Request", url_string);

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
      e.printStackTrace();
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
   */
  private void runTest() {
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
      if (wsConn != null) { //if using MLab, check that still connected
        Log.d("WebSocket", "Before running test WebSocket connectivity check: "
                + (wsConn.isOpen() ? "CONNECTED" : "CLOSED"));
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

        Log.d("Server", server + " metadata " + metadataServer);
        // This side channel is used to communicate with the server in bytes mode and to
        // run traces, it send tcp and udp packets and receives the same from the server
        //Server handles communication in handle() function in server_replay.py in server
        //code
        CombinedSideChannel sideChannel = new CombinedSideChannel(sslSocketFactory,
                server, sideChannelPort, appData.isTCP());

        JitterBean jitterBean = new JitterBean();

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
        // This is group of values that is used to track traces on server
        // Youtube;False;0;DiffDetector;0;129.10.9.93;1.0
        sideChannel.declareID(appData.getReplayName(), endOfTest ? "True" : "False",
                randomID, String.valueOf(historyCount), String.valueOf(testId),
                doTest ? Config.extraString + "-Test" : Config.extraString,
                ipThroughProxy, Consts.VERSION_NAME);

        // This tuple tells the server if the server should operate on packets of traces
        // and if so which packets to process
        sideChannel.sendChangeSpec(-1, "null", "null");

        /*
         * Step 2: Ask server for permission to run replay.
         */
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.ASK4PERMISSION);
        // Now to move forward we ask for server permission
        String[] permission = sideChannel.ask4Permission();
        String status = permission[0].trim();

        Log.d("Replay", "permission[0]: " + status
                + " permission[1]: " + permission[1]);

        String permissionError = permission[1].trim();
        if (status.equals("0")) {
          // These are the different errors that server can report
          switch (permissionError) {
            case "1": //server cannot identify replay
              Log.ui("ERR_PERM_1", S.ERROR_UNKNOWN_REPLAY);
              break;
            case "2": //only one replay can run at a time per IP
              Log.ui("ERR_PERM_2", S.ERROR_IP_CONNECTED);
              break;
            case "3": //server CPU > 95%, disk > 95%, or bandwidth > 2000 Mbps
              Log.ui("ERR_PERM_3", S.ERROR_LOW_RESOURCES);
              break;
            default:
              Log.ui("ERR_PERM_4", S.ERROR_UNKNOWN);
          }
          return;
        }

        int numOfTimeSlices = Integer.parseInt(permission[2].trim(), 10);

        /*
         * Step 3: Send noIperf.
         */
        sideChannel.sendIperf(); // always send noIperf here

        /*
         * Step 4: Send device info.
         */
        sideChannel.sendMobileStats(Config.sendMobileStats);

        /*
         * Step 5: Get port mapping from server.
         */
        /*
         * Ask for port mapping from server. For some reason, port map
         * info parsing was throwing error. so, I put while loop to do
         * this until port mapping is parsed successfully.
         */
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.RECEIVE_SERVER_PORT_MAPPING);

        HashMap<String, HashMap<String, HashMap<String, ServerInstance>>> serverPortsMap
                = sideChannel.receivePortMappingNonBlock();
        UDPReplayInfoBean udpReplayInfoBean = new UDPReplayInfoBean();
        udpReplayInfoBean.setSenderCount(sideChannel.receiveSenderCount());
        Log.i("Replay", "Successfully received serverPortsMap and senderCount!");

        /*
         * Step 6: Create TCP clients from CSPairs and UDP clients from client ports.
         */
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.CREATE_TCP_CLIENT);

        //map of all cs pairs to TCP clients for a replay
        HashMap<String, CTCPClient> CSPairMapping = new HashMap<>();

        //create TCP clients
        for (String csp : appData.getTcpCSPs()) {
          //get server IP and port
          String destIP = csp.substring(csp.lastIndexOf('-') + 1,
                  csp.lastIndexOf("."));
          String destPort = csp.substring(csp.lastIndexOf('.') + 1);

          //get the server
          ServerInstance instance;
          try {
            instance = Objects.requireNonNull(Objects.requireNonNull(
                    serverPortsMap.get("tcp")).get(destIP)).get(destPort);
          } catch (NullPointerException e) {
            Log.e("Replay", "Cannot get instance", e);
            Log.ui("ERR_NO_CONN_INST", S.ERROR_NO_CONNECTION);
            return;
          }
          assert instance != null;
          if (instance.server.trim().equals(""))
            // Use a setter instead probably
            instance.server = server; // serverPortsMap.get(destPort);

          //create the client
          CTCPClient c = new CTCPClient(csp, instance.server,
                  Integer.parseInt(instance.port),
                  appData.getReplayName(), Config.publicIP, false);
          CSPairMapping.put(csp, c);
        }
        Log.i("Replay", "created clients from CSPairs");

        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.CREATE_UDP_CLIENT);

        //map of all client ports to UDP clients for a replay
        HashMap<String, CUDPClient> udpPortMapping = new HashMap<>();

        //create client for each UDP port
        for (String originalClientPort : appData.getUdpClientPorts()) {
          CUDPClient c = new CUDPClient(Config.publicIP);
          udpPortMapping.put(originalClientPort, c);
        }

        Log.i("Replay", "created clients from udpClientPorts");
        Log.d("Replay", "Size of CSPairMapping is " + CSPairMapping.size());
        Log.d("Replay", "Size of udpPortMapping is " + udpPortMapping.size());

        /*
         * Step 7: Start notifier for UDP.
         */
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.RUN_NOTF);

        CombinedNotifierThread notifier = sideChannel.notifierCreator(udpReplayInfoBean);
        Thread notfThread = new Thread(notifier);
        notfThread.start();

        /*
         * Step 8: Start receiver to log throughputs on a given interval.
         */
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.RUN_RECEIVER);

        CombinedAnalyzerTask analyzerTask = new CombinedAnalyzerTask(app.getTime() / 2.0,
                appData.isTCP(), numOfTimeSlices, runPortTests); //throughput logged
        Timer analyzerTimer = new Timer(true); //timer to log throughputs on interval
        analyzerTimer.scheduleAtFixedRate(analyzerTask, 0, analyzerTask.getInterval());

        CombinedReceiverThread receiver = new CombinedReceiverThread(
                udpReplayInfoBean, jitterBean, analyzerTask); //receiver for udp
        Thread rThread = new Thread(receiver);
        rThread.start();

        /*
         * Step 9: Send packets to server.
         */
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.RUN_SENDER);

        CombinedQueue queue = new CombinedQueue(appData.getQ(), jitterBean, analyzerTask,
                runPortTests ? Consts.REPLAY_PORT_TIMEOUT : Consts.REPLAY_APP_TIMEOUT);
        long timeStarted = System.nanoTime(); //start time for sending
        //send packets
        queue.run(CSPairMapping, udpPortMapping, udpReplayInfoBean, serverPortsMap.get("udp"),
                Config.timing, server);

        //all packets sent - stop logging and receiving
        queue.stopTimers();
        analyzerTimer.cancel();
        notifier.doneSending = true;
        notfThread.join();
        receiver.keepRunning = false;
        rThread.join();

        /*
         * Step 10: Tell server that replay is finished.
         */
        Log.ui("updateStatus", iteration + "/" + types.length + " " + S.SEND_DONE);

        //time to send all packets
        double duration = ((double) (System.nanoTime() - timeStarted)) / 1000000000;
        sideChannel.sendDone(duration);
        Log.d("Replay", "replay finished using time " + duration + " s");

        /*
         * Step 11: Send throughputs and slices to server.
         */
        ArrayList<ArrayList<Double>> avgThroughputsAndSlices
                = analyzerTask.getAverageThroughputsAndSlices();
        sideChannel.sendTimeSlices(avgThroughputsAndSlices);

        //set avg of port 443, so it can be displayed if port being tested is blocked
        if (runPortTests && channel.equalsIgnoreCase("random")) {
          app.randomThroughput = analyzerTask.getAvgThroughput();
        }

        // TODO find a better way to do this
        // Send Result;No and wait for OK before moving forward
        while (sideChannel.getResult(Config.result)) {
          Thread.sleep(500);
        }

        /*
         * Step 12: Close side channel and TCP/UDP sockets.
         */
        // closing side channel socket
        sideChannel.closeSideChannelSocket();

        //close TCP sockets
        for (String csp : appData.getTcpCSPs()) {
          CTCPClient c = CSPairMapping.get(csp);
          if (c != null) {
            c.close();
          }
        }
        Log.i("CleanUp", "Closed CSPairs 1");

        //close UDP sockets
        for (String originalClientPort : appData.getUdpClientPorts()) {
          CUDPClient c = udpPortMapping.get(originalClientPort);
          if (c != null) {
            c.close();
          }
        }

        Log.i("CleanUp", "Closed CSPairs 2");
        iteration++;
      } catch (InterruptedException e) {
        Log.w("Replay", "Replay interrupted!", e);
      } catch (IOException e) { //something wrong with receiveKbytes() or constructor in CombinedSideChannel
        Log.e("Replay", "Some IO issue with server", e);
        Log.ui("ERR_CONN_IO_SERVER", S.ERROR_NO_CONNECTION);
        return;
      }
    }

    /*
     * Step C: Determine if there is differentiation.
     */
    getResults(portBlocked);
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
   */
  private void getResults(boolean portBlocked) {
    try {
      JSONObject result = null;
      if (!portBlocked) { //skip Step 1 and step 2 if port blocked
        /*
         * Step 1: Ask server to analyze a test.
         */
        for (int ask4analysisRetry = 5; ask4analysisRetry > 0; ask4analysisRetry--) {
          result = ask4analysis(randomID, app.getHistoryCount()); //request analysis
          if (result == null) {
            Log.e("Result Channel", "ask4analysis returned null!");
          } else {
            break;
          }
        }

        if (result == null) {
          Log.ui("ERR_NO_ANA1", S.ERROR_ANALYSIS_FAIL);
          return;
        }

        boolean success = result.getBoolean("success");
        if (!success) {
          Log.e("Result Channel", "ask4analysis failed!");
          Log.ui("ERR_NO_ANA2", S.ERROR_ANALYSIS_FAIL);
          return;
        }

        Log.ui("updateStatus", S.WAITING);

        // sanity check
        if (app.getHistoryCount() < 0) {
          Log.e("Result Channel", "historyCount value not correct!");
          Log.ui("ERR_NO_ANA3", S.ERROR_ANALYSIS_FAIL);
          return;
        }

        Log.i("Result Channel", "ask4analysis succeeded!");

        /*
         * Step 2: Get result of analysis from server.
         */
        for (int i = 0; ; i++) { //3 attempts to get analysis from sever
          result = getSingleResult(randomID, app.getHistoryCount()); //get result

          if (result == null) {
            Log.e("Result Channel", "getSingleResult returned null!");
          } else {
            success = result.getBoolean("success");
            if (success) { //success
              if (result.has("response")) { //success and has response
                Log.i("Result Channel", "retrieve result succeeded");
                break;
              } else { //success but response is missing
                Log.w("Result Channel", "Server result not ready");
              }
            } else if (result.has("error")) {
              Log.e("Result Channel", "ERROR: " + result.getString("error"));
            } else {
              Log.e("Result Channel", "Error: Some error getting results.");
            }
          }

          if (i < 3) { //wait 2 seconds to try again
            try {
              Thread.sleep(2000);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          } else { //error after 3rd attempt
            if (runPortTests) { //"the port 80 issue"
              portBlocked = true;
              Log.i("Result Channel", "Can't retrieve result, port blocked");
              break;
            } else {
              Log.ui("ERR_TCP_SEND", S.NOT_ALL_TCP_SENT_TEXT);
              return;
            }
          }
        }
      }

      /*
       * Step 3: Parse the analysis results.
       */
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

      Log.d("Result Channel", "SERVER RESPONSE: " + response.toString());

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
        Log.ui("ERR_RESULT1", S.ERROR_RESULT);
        return;
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
          e.printStackTrace();
        }
        rerun = true;
        runTest();
        return; //return so that first result isn't saved
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

      success = true;
      Log.ui("FinalResults", response.toString()); //save user results to UI log file
    } catch (JSONException e) {
      Log.e("Result Channel", "parsing json error", e);
    }
  }
}
