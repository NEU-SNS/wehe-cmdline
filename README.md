# Wehe Command Line Client

This is the Wehe command line client. This app runs tests to help users determine whether their ISP is throttling network traffic for certain apps or ports. Please see https://wehe.meddle.mobi/ for more details.

## About the Code

* The code is based off of the Wehe [Android Client](https://github.com/NEU-SNS/wehe-android). 
* Building the source code requires two additional libraries: [Tyrus Standalone Client v1.9](https://mvnrepository.com/artifact/org.glassfish.tyrus.bundles/tyrus-standalone-client/1.9) for WebSocket support and [JSON](https://github.com/stleary/JSON-java) ([Maven repo](https://mvnrepository.com/artifact/org.json/json/20201115)).
* The `src/` directory contains the source files.
* The `res/` directory contains the app and port tests.
* The code runs one test at a time.

## Run the jar

A compiled jar comes with the repo (`wehe-cmdline.jar`). This jar was compiled using Java 11.

### Usage

Usage: `java -jar wehe-cmdline.jar -n [TEST_NAME] [OPTION]...`

Example: `java -jar wehe-cmdline.jar -n applemusic -c -r results/ -l info`

**Options**

`-n TEST_NAME` - The name of the test to run. **Argument required. See below for list of test names.**

`-s SERV_NAME` - The hostname or IP of server to run the tests. Default: `wehe4.meddle.mobi`.

`-c` - Turn off confirmation replays (if test is inconclusive, it will automatically rerun by default).

`-a A_THRESH` - The area threshold percentage for determining differentiation. Default: `50`.

`-k KS2P_VAL` - The KS2P-value threshold percentage for determining differentiation. Default: `1`.

`-t RESR_ROOT` - The resources root containing `apps_list.json` and the tests. No need to change this if using the jar or if you don't move the `res/` directory. Default: `res/`.

`-r RSLT_ROOT` - The results root containing the output logs and info. Default: `test_results/`.

`-l LOG_LEVEL` - The level of logs and above that should be printed to console (all levels will be saved to logs on disk regardless of the level printed to console). Choose from `wtf`, `error`, `warn`, `info`, or `debug`. Default: none of these, only `UI` logs will be printed to the console.

`-h` - Print the help message.

`-v` - Print the version number.

**Tests**

| App Name        | Test Name (`-n` arg) | Port Name             | Test Name (`-n` arg) |
|-----------------|----------------------|-----------------------|----------------------|
| Apple Music     | `applemusic`         | 80 HTTP small         | `port80s`            |
| Dailymotion     | `dailymotion`        | 81 HTTP small         | `port81s`            |
| Deezer          | `deezer`             | 465 SMTPS small       | `port465s`           |
| Disney+         | `disneyplus`         | 853 DoT small         | `port853s`           |
| Facebook Video  | `facebookvideo`      | 993 IMAPS small       | `port993s`           |
| Google Meet     | `meet`               | 995 POP3S small       | `port995s`           |
| Hulu            | `hulu`               | 1194 OpenVPN small    | `port1194s`          |
| Microsoft Teams | `teams`              | 1701 L2TP small       | `port1701s`          |
| NBC Sports      | `nbcsports`          | 5061 SIPS small       | `port5061s`          |
| Netflix         | `netflix`            | 6881 BitTorrent small | `port6881s`          |
| Molotov TV      | `molotovtv`          | 8080 SpeedTest small  | `port8080s`          |
| myCANAL         | `mycanal`            | 8443 SpeedTest small  | `port8443s`          |
| OCS             | `ocs`                | 80 HTTPS large        | `port80l`            |
| Prime Video     | `amazon`             | 81 HTTP large         | `port81l`            |
| Salto           | `salto`              | 465 SMTPS large       | `port465l`           |
| SFR Play        | `sfrplay`            | 853 DoT large         | `port853l`           |
| Skype           | `skype`              | 993 IMAPS large       | `port993l`           |
| Spotify         | `spotify`            | 995 POP3S large       | `port995l`           |
| Twitch          | `twitch`             | 1194 OpenVPN large    | `port1194l`          |
| Vimeo           | `vimeo`              | 1701 L2TP large       | `port1701l`          |
| Webex           | `webex`              | 5061 SIPS large       | `port5061l`          |
| WhatsApp        | `whatsapp`           | 6881 BitTorrent large | `port6881l`          |
| YouTube         | `youtube`            | 8080 SpeedTest large  | `port8080l`          |
| Zoom            | `zoom`               | 8443 SpeedTest large  | `port8443l`          |

Note: Small port tests are 10 MB, while large port tests are 50 MB.

### Output

Output is contained in `RSLT_ROOT`:

* `info.txt` - This file contains the user's random ID and the current history count.
* `logs/` - This directory contains the logs that would be printed to the Android logs (think of Android's Log class).
  * Log files are the in the format `logs_[randomID]_[historyCount]_[SUCCESS | FAILURE].txt`.
  * `SUCCESS` means a result was reached, and an error did not occur. `FAILURE` means something went wrong with that test.
* `ui/` - This directory contains the text that a user running the Android client would see on his/her screen.
  * Log files are in the format `ui_[randomID]_[historyCount]_[SUCCESS | FAILURE].txt`.
  * `SUCCESS` means a result was reached, and an error did not occur. `FAILURE` means something went wrong with that test.
