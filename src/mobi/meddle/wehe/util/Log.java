package mobi.meddle.wehe.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

import mobi.meddle.wehe.constant.Consts;

/**
 * Based off of the Log class in Android. Does all the logging to the console and UI, and saves the
 * logs on disk.
 */
public class Log {
  private static String randomID; //user ID
  private static int historyCount; //current test ID
  private static final StringBuilder logText = new StringBuilder(); //represents text printed to Android logs
  private static final StringBuilder uiText = new StringBuilder(); //represents text shown on app to user
  private static final StringBuilder tmp = new StringBuilder(); //for constructing line to print (increase efficiency?)
  private static int logCount = 0; //write logs to disk every 1000 lines so that buffer doesn't
  private static int uiCount = 0;  //use too much memory
  private static boolean logAppend = false;
  private static boolean uiAppend = false;
  private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
  private static LocalDateTime now;

  public static void d(String tag, String msg) {
    appendLog(" D/", 5, tag, msg);
  }

  public static void i(String tag, String msg) {
    appendLog(" I/", 4, tag, msg);
  }

  public static void w(String tag, String msg) {
    appendLog(" W/", 3, tag, msg);
  }

  public static void w(String tag, String msg, Throwable tr) {
    StringWriter error = new StringWriter();
    tr.printStackTrace(new PrintWriter(error));
    appendLog(" W/", 3, tag, msg + "\n" + error.toString());
  }

  public static void e(String tag, String msg) {
    appendLog(" E/", 2, tag, msg);
  }

  public static void e(String tag, String msg, Throwable tr) {
    StringWriter error = new StringWriter();
    tr.printStackTrace(new PrintWriter(error));
    appendLog(" E/", 2, tag, msg + "\n" + error.toString());
  }

  public static void wtf(String tag, String msg) {
    appendLog(" WTF/", 1, tag, msg);
  }

  /**
   * Write to the log, and print it out on the console.
   *
   * @param type the log type (ie. d, i, w, e, wtf)
   * @param tag  tag of log
   * @param msg  message to log
   */
  private static synchronized void appendLog(String type, int typeI, String tag, String msg) {
    now = LocalDateTime.now();
    tmp.setLength(0);
    tmp.append(dtf.format(now)).append(type).append(tag).append(": ").append(msg).append("\n");
    logText.append(tmp);
    if (typeI <= Config.logLev) {
      System.out.print(tmp);
    }
    //move log to disk every 1000 lines to prevent buffer from using too much memory
    if (logCount > 1000) {
      appendLogFile(true);
      logCount = 0;
      logText.setLength(0);
    } else {
      logCount++;
    }
  }

  /**
   * Write to the "UI" (in this case, it'll just be printed to the console).
   *
   * @param id  like the tag in appendLog
   * @param msg the message to print
   */
  public static void ui(String id, String msg) {
    now = LocalDateTime.now();
    tmp.setLength(0);
    tmp.append(dtf.format(now)).append(" ").append(id).append(": ").append(msg).append("\n");
    uiText.append(tmp);
    System.out.print(tmp);
    //move log to disk every 1000 lines to prevent buffer from using too much memory
    if (uiCount > 1000) {
      appendLogFile(false);
      uiCount = 0;
      uiText.setLength(0);
    } else {
      uiCount++;
    }
  }

  /**
   * Try to read in the existing randomID and historyCount, making new ones if nonexistent.
   *
   * @return the randomID and historyCount
   * @throws FileNotFoundException shouldn't be needed, as there is already a check
   */
  public static String readInfo() throws FileNotFoundException {
    //try to get an existing randomID and historyCount
    boolean needNewInfo = true;
    Path info_file = Paths.get(Config.INFO_FILE);
    if (Files.exists(info_file)) {
      File file = new File(Config.INFO_FILE);
      Scanner scanner = new Scanner(file);
      if (scanner.hasNextLine()) {
        randomID = scanner.nextLine();
        if (randomID.length() == 10 && randomID.startsWith("@") && scanner.hasNextLine()) {
          try {
            historyCount = Integer.parseInt(scanner.nextLine());
            if (historyCount >= 0) {
              needNewInfo = false;
            }
          } catch (NumberFormatException ignored) {

          }
        }
      }
    }

    //make new randomID and historyCount if necessary
    if (needNewInfo) {
      randomID = "@" + new RandomString(9).nextString();
      historyCount = 0;
      File file = new File(Config.INFO_FILE);
      if (file.getParentFile().mkdirs()) {
        System.out.println("\tDirectory made: " + file.getParentFile().getName());
      }
    }
    return randomID + ";" + historyCount;
  }

  /**
   * Write the new randomID and historyCount to disk.
   *
   * @throws IOException Unable to write to file
   */
  public static void writeInfo() throws IOException {
    FileWriter writer = new FileWriter(Config.INFO_FILE);
    writer.write(randomID + "\n" + historyCount);
    System.out.println("\tRandomID and History Count written to " + Config.INFO_FILE);
    writer.close();
  }

  public static void incHistoryCount() {
    historyCount++;
  }

  /**
   * Call the functions to write the logs and UI text to disk.
   *
   * @param exitCode 0 if the test was successful, otherwise the error code as defined in Consts.java
   * @return program exit code
   */
  public static int writeLogs(int exitCode) {
    boolean success = appendLogFile(true);
    success = appendLogFile(false) && success;
    if (!success && exitCode == 0) {
      exitCode = Consts.ERR_WR_LOGS;
    } else if (!success) {
      exitCode = -exitCode;
    }
    renameLog(true, exitCode);
    renameLog(false, exitCode);
    return exitCode;
  }

  /**
   * Rename console or UI log to final name after test finish.
   *
   * @param isConsoleLog true if changing console log name; false if changing UI log name
   * @param exitCode 0 if the test was successful, otherwise the error code as defined in Consts.java
   */
  private static void renameLog(boolean isConsoleLog, int exitCode) {
    String dir = isConsoleLog ? Config.RESULTS_LOGS : Config.RESULTS_UI;
    String type = isConsoleLog ? "logs_" : "ui_";
    String oldLogName = dir + type + randomID + "_" + historyCount + ".txt";
    String newLogName = dir + type + randomID + "_" + historyCount + "_" + exitCode + ".txt";
    File log = new File(oldLogName);
    File logNew = new File(newLogName);
    if (logNew.exists()) {
      System.out.println("\tERROR: " + newLogName + " exists. Log will remain at " + oldLogName);
    } else {
      if (log.renameTo(logNew)) {
        System.out.println("\tWritten to " + newLogName);
      } else {
        System.out.println("\tFailed to change log name. Logs will remain written to " + oldLogName);
      }
    }
  }

  /**
   * Write or append to a log file.
   *
   * @param isConsoleLog true if write/append to console log; false if write/append to UI log
   * @return true if log successfully appended; false otherwise
   */
  private static boolean appendLogFile(boolean isConsoleLog) {
    String dir = isConsoleLog ? Config.RESULTS_LOGS : Config.RESULTS_UI;
    String type = isConsoleLog ? "logs_" : "ui_";
    String filename = dir + type + randomID + "_" + historyCount + ".txt";
    File file = new File(filename);
    if (file.getParentFile().mkdirs()) {
      System.out.println("\tDirectory made: " + file.getParentFile().getName());
    }
    try {
      FileWriter writer = new FileWriter(filename, isConsoleLog ? logAppend : uiAppend);
      writer.write(isConsoleLog ? logText.toString() : uiText.toString());
      writer.close();
      if (isConsoleLog) {
        logAppend = true;
      } else {
        uiAppend = true;
      }
    } catch (IOException e) {
      System.out.println("\tUnable to write logs.");
      e.printStackTrace();
      return false;
    }
    return true;
  }
}
