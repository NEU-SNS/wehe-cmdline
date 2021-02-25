package mobi.meddle.wehe.bean;

/**
 * Data structure to hold info about an app/port
 */
public class ApplicationBean {
  public double area_test = 0;
  public double ks2pVal = 0;
  public double ks2pRatio = 0;
  public double originalThroughput = 0; //original replay throughput for apps
  public double randomThroughput = 0; //random replay throughput for apps; throughput for port 443
  private String name = null; //name of the app
  //number of seconds needed to run both replays; port time is not accurate, as the goal is to
  //run those as fast as the user's internet connection can support
  private int time = 0;
  private int historyCount = -1; //the ID for the replay for this specific user
  private String dataFile = null; //filename of the replay
  private String randomDataFile = null; //filename of the random replay for apps; port 443 for ports
  private String image = null; //filename of the app/port image

  public int getTime() {
    return time;
  }

  public void setTime(int time) {
    this.time = time;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getHistoryCount() {
    return historyCount;
  }

  public void setHistoryCount(int historyCount) {
    this.historyCount = historyCount;
  }

  public String getDataFile() {
    return dataFile;
  }

  public void setDataFile(String dataFile) {
    this.dataFile = dataFile;
  }

  public String getRandomDataFile() {
    return randomDataFile;
  }

  public void setRandomDataFile(String randomDataFile) {
    this.randomDataFile = randomDataFile;
  }
}
