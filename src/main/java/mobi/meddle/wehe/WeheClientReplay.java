package mobi.meddle.wehe;

import mobi.meddle.wehe.bean.CombinedAppJSONInfoBean;
import mobi.meddle.wehe.bean.RequestSet;
import mobi.meddle.wehe.constant.Consts;
import mobi.meddle.wehe.util.Config;
import mobi.meddle.wehe.util.Log;
import mobi.meddle.wehe.util.UtilsManager;
import org.checkerframework.checker.units.qual.A;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class WeheClientReplay {

    /**
     * Reads the replay files and loads them into memory as a bean.
     *
     * @param filename filename of the replay
     * @return a bean containing information about the replay
     */
    private static CombinedAppJSONInfoBean unpickleJSON(String filename) {
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

    private final String backServerIP = "34.122.168.31";

    private static void runBackgroundTraffic() {


        // first find the sample staring time of traces
        int min = 2, max = 6;
        int version = min + (int) (Math.random() * (max - min + 1));
        String timesFile = Config.RESOURCES_ROOT + "/times_v" + version + ".csv";

        ArrayList<Long> startTimes = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(timesFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (Math.random() < 0.05) {
                    String[] values = line.split(",");
                    startTimes.add((long) (Float.parseFloat(values[0]) * 1e3));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // the client for trace replay
        final int[] threadCount = {-1};
        Runnable traceReplay = new Runnable() {

            @Override
            public void run() {
                threadCount[0]++;
                try {
                    System.out.println(threadCount[0] + "   " + new Date());
                    Thread.sleep(100000);

//                    //-------------- open socket --------------//
//                    Socket socket = new Socket();
//                    socket.setTcpNoDelay(true);
//                    socket.setReuseAddress(true);
//                    socket.setKeepAlive(true);
//                    socket.setSoTimeout(30000);
//                    socket.connect(new InetSocketAddress(backServerIP, 1234));
//
//                    //-------------- read trace --------------//
//                    CombinedAppJSONInfoBean appData = unpickleJSON("");
//
//                    for (RequestSet RS : appData.getQ()) {
//                        // send payload directly
//                        DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
//                        dataOutputStream.write(RS.getPayload());
//
//                        //-------------- receive response --------------//
//                        if (RS.getResponse_len() > 0) {
//                            DataInputStream dataInStream = new DataInputStream(socket.getInputStream());
//
//                            int totalRead = 0;
//                            byte[] buffer = new byte[RS.getResponse_len()];
//                            while (totalRead < buffer.length) {
//                                // @@@ offset is wrong?
//                                int bufSize = 4096;
//                                int bytesRead = dataInStream.read(buffer, totalRead,
//                                        Math.min(buffer.length - totalRead, bufSize));
//                                if (bytesRead < 0) {
//                                    break;
//                                }
//                                totalRead += bytesRead;
//                            }
//                        }
//                    }
//
//                    //-------------- close socket --------------//
//                    socket.close();
                } catch (InterruptedException e) {
                    Log.e("BackReplay", "thread running background Wehe trace error: " + e);
                    System.out.println("cancelled");
                } finally {
                    System.out.println("Stopping at " + threadCount[0] + "   " + new Date());
                }
            }
        };

        // start the threads of client replay
        ScheduledThreadPoolExecutor threadPool = new ScheduledThreadPoolExecutor(startTimes.size());
        ArrayList<ScheduledFuture<?>> replays = new ArrayList<>();
        System.out.println(startTimes.size());
        for (Long time : startTimes) {
            replays.add(threadPool.schedule(traceReplay, time, TimeUnit.MILLISECONDS));
        }

        ArrayList<Thread> timeouts = new ArrayList<>(startTimes.size());
        for (ScheduledFuture<?> replay : replays) {
            Thread timeout = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        replay.get(10, TimeUnit.SECONDS);
                    } catch (InterruptedException | TimeoutException | ExecutionException e) {
                        replay.cancel(true);
                        System.out.println("error " + e);
                    }
                }
            });
            timeout.start();
            timeouts.add(timeout);
        }

        for (Thread timeout: timeouts) {
            try {
                timeout.join(15 * 1000L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        threadPool.shutdown();
    }

    public static void main(String[] args) {
        System.out.println("Yes");

//        runBackgroundTraffic();
    }
}
