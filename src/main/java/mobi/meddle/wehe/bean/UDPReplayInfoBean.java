package mobi.meddle.wehe.bean;

import java.nio.channels.DatagramChannel;
import java.util.ArrayList;

/**
 * Contains information about a UDP replay.
 */
public class UDPReplayInfoBean {
  private final ArrayList<DatagramChannel> udpSocketList = new ArrayList<>();
  private int senderCount = 0;

  public synchronized ArrayList<DatagramChannel> getUdpSocketList() {
    return udpSocketList;
  }

  public synchronized int getSenderCount() {
    return senderCount;
  }

  public synchronized void setSenderCount(int senderCount) {
    this.senderCount = senderCount;
  }

  public synchronized void addSocket(DatagramChannel channel) {
    udpSocketList.add(channel);
  }
}
