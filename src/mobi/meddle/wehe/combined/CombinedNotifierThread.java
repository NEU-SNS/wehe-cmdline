package mobi.meddle.wehe.combined;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

import mobi.meddle.wehe.bean.UDPReplayInfoBean;
import mobi.meddle.wehe.util.Log;

/**
 * Class that seemed to keep track of when UDP data was being received. Doesn't seem to do anything
 * useful now.
 */
public final class CombinedNotifierThread implements Runnable {
  // changes of Arash
  public volatile boolean doneSending;
  private final UDPReplayInfoBean udpReplayInfoBean;
  private DataInputStream dataInputStream = null;
  private int inProcess = 0;
  private int total = 0;

  /**
   * Constructor.
   *
   * @param udpReplayInfoBean contains info about a UDP replay
   * @param socket            server socket to connect to
   */
  CombinedNotifierThread(UDPReplayInfoBean udpReplayInfoBean, Socket socket) {
    this.udpReplayInfoBean = udpReplayInfoBean;
    this.doneSending = false;

    if (socket.isConnected()) {
      try {
        dataInputStream = new DataInputStream(socket.getInputStream());
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      Log.i("Notifier", "socket not connected!");
    }
  }

  @Override
  public void run() {
    Thread.currentThread().setName("CombinedNotifierThread (Thread)");
    try {
      while (true) {
        if (dataInputStream.available() > 0) {
          int objLen = 10;
          byte[] data = receiveObject(objLen);
          String[] Notf = new String(data).split(";");
          if (Notf[0].equalsIgnoreCase("STARTED")) {
            inProcess += 1;
            total += 1;
            Log.i("Notifier", "received STARTED!");
          } else if (Notf[0].equalsIgnoreCase("DONE")) {
            inProcess -= 1;
            Log.i("Notifier", "received DONE!");
          } else {
            Log.wtf("Notifier", "WTF???");
            break;
          }
        } else {
          Thread.sleep(500);
        }

        if (doneSending) {
          if (inProcess == 0) {
            Log.d("Notifier", "Done notifier! total: " + total
                    + " udpSenderCount: " + udpReplayInfoBean.getSenderCount());
            break;
          }
        }
      }
    } catch (Exception e) {
      Log.e("Notifier", "receive data error!", e);
    }
    Log.i("Notifier", "received all packets!");
  }

  /**
   * Receive stuff from the server.
   *
   * @param objLen Number of bytes to read to obtain the size of the stuff
   * @return response from server
   */
  private byte[] receiveObject(int objLen) {
    byte[] recvObjSizeBytes = receiveKbytes(objLen); //receive how big stuff will be
    int recvObjSize = Integer.parseInt(new String(recvObjSizeBytes));
    return receiveKbytes(recvObjSize); //receive stuff
  }

  /**
   * Receive k byes from the server.
   *
   * @param k number of bytes to receive
   * @return response from server
   */
  private byte[] receiveKbytes(int k) {
    int totalRead = 0;
    byte[] b = new byte[k];
    while (totalRead < k) {
      int bufSize = 4096;
      int bytesRead = 0;
      try {
        bytesRead = dataInputStream.read(b, totalRead, Math.min(k - totalRead, bufSize));
        if (bytesRead < 0) {
          throw new IOException("Data stream ended prematurely");
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
      totalRead += bytesRead;
    }
    return b;
  }
}
