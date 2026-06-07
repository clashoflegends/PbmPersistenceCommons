package persistenceCommons;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Fire-and-forget crash reporter. Posts uncaught/EGF-load failures to the Site so the GM sees them
 * without asking players for counselor.log. Crash-only by design (not a WARN+ firehose).
 *
 * On by default; opt out with properties.config key remoteLogging=false.
 *
 * Never blocks or throws into the caller: runs on a daemon thread with a short timeout and swallows
 * everything. Uses plain HttpURLConnection (not the Apache HttpClient mime stack).
 */
public class CrashReporter {

    private static final Log log = LogFactory.getLog(CrashReporter.class);
    private static final String SITE_URL = "http://clashlegends.com/PbmSite/%s.php";
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_TRACE = 8000;

    // Strips a user home folder name from paths so we don't leak usernames: C:\Users\John, /home/john, /Users/john
    private static final Pattern USER_PATH = Pattern.compile(
            "(?i)([A-Z]:\\\\Users\\\\|/home/|/Users/)[^\\\\/\\s:;,'\"]+");

    private CrashReporter() {
    }

    /**
     * Report a crash. Returns immediately; the POST happens on a background daemon thread.
     *
     * @param t the throwable that crashed/was caught
     * @param context short tag for where it happened, e.g. "uncaught" or "egf-open:game_88_3.rr.egf"
     */
    public static void report(final Throwable t, final String context) {
        try {
            if ("false".equalsIgnoreCase(SettingsManager.getInstance().getConfig("remoteLogging", "true"))) {
                return; // player opted out
            }
            final String detail = buildDetail(t, context);
            Thread th = new Thread(() -> post(detail), "crash-reporter");
            th.setDaemon(true);
            th.start();
        } catch (Throwable ignore) {
            // reporting must never make a crash worse
        }
    }

    private static String buildDetail(Throwable t, String context) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String trace = scrub(sw.toString());
        if (trace.length() > MAX_TRACE) {
            trace = trace.substring(0, MAX_TRACE) + "...[truncated]";
        }
        return String.format("context=%s | counselor=%s commons=%s | java=%s | os=%s%n%s",
                context,
                SysApoio.getVersionClash("version_counselor"),
                SysApoio.getVersionClash("version_commons"),
                SysApoio.getVersionJava(),
                SysApoio.getVersionOs(),
                trace);
    }

    private static String scrub(String s) {
        if (s == null) {
            return "";
        }
        // Keep the path prefix ($1), replace only the username segment.
        return USER_PATH.matcher(s).replaceAll("$1<user>");
    }

    private static void post(String detail) {
        HttpURLConnection conn = null;
        try {
            String page = SettingsManager.getInstance().getConfig("CrashLogPage", "CounselorLogCrash");
            String token = SettingsManager.getInstance().getConfig("counselorToken", "");
            URI uri = new URI(String.format(SITE_URL, page));

            StringBuilder body = new StringBuilder();
            field(body, "pToken", token);
            field(body, "pContext", detail);

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }
            int code = conn.getResponseCode();
            if (code != 202 && code != 200) {
                log.debug("Crash report not accepted, HTTP " + code);
            }
        } catch (Throwable ex) {
            log.debug("Crash report failed (ignored): " + ex);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void field(StringBuilder sb, String name, String value) {
        if (sb.length() > 0) {
            sb.append('&');
        }
        sb.append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
    }
}
