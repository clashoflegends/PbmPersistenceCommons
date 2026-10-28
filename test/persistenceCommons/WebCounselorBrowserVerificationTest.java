package persistenceCommons;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import modelWeb.PartidaJogadorWebInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WebCounselorBrowserVerificationTest {
    private static final String CHALLENGE = "<!DOCTYPE html><html><title>One moment, please...</title>"
            + "<body>Please wait while your request is being verified..."
            + "<script>var field='wsidchk';</script></body></html>";

    @Test
    void onlyRecognizesTheKnownBrowserChallenge() {
        assertTrue(WebCounselorManager.isBrowserVerification(CHALLENGE));
        assertTrue(WebCounselorManager.isBrowserVerification(CHALLENGE.toUpperCase(java.util.Locale.ROOT)));
        for (String body : new String[]{null, "", "Invalid player token", "wsidchk One moment, please...",
                "<html><title>Server error</title></html>", "<html>One moment, please...</html>",
                "<html>wsidchk</html>"}) {
            assertFalse(WebCounselorManager.isBrowserVerification(body), body);
        }
    }

    @Test
    void uploadResponsesPreserveCredentialsFilesAndExistingStatusContracts(@TempDir Path directory) throws Exception {
        // A child JVM keeps singleton settings and any future persistence regression away from real config.
        Files.writeString(directory.resolve("properties.config"), "playerLogin=sample.player\n"
                + "playerToken=" + "a".repeat(40) + "\nSendOrderReceiptRequest=0\n",
                StandardCharsets.ISO_8859_1);
        String classPath = Arrays.stream(System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path")).split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(entry -> Path.of(entry).toAbsolutePath().toString()).collect(Collectors.joining(File.pathSeparator));
        Path output = directory.resolve("probe.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-cp", classPath, Probe.class.getName()).directory(directory.toFile())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Isolated upload probe timed out");
            assertEquals(0, process.exitValue(), () -> {
                try { return Files.readString(output); } catch (Exception ex) { return "Probe failed"; }
            });
            assertFalse(Files.readString(output).contains("wsidchk"), "Challenge HTML must not reach diagnostics");
            assertFalse(Files.readString(output).contains("dummy-secret"), "Reflected passwords must not reach diagnostics");
        } finally {
            process.destroyForcibly();
        }
    }

    public static class Probe {
        public static void main(String[] args) throws Exception {
            System.clearProperty("jpackage.app-version");
            AtomicReference<String> body = new AtomicReference<>(CHALLENGE);
            AtomicInteger status = new AtomicInteger(200);
            AtomicInteger requests = new AtomicInteger();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(status.get(), response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            try {
                SettingsManager settings = SettingsManager.getInstance();
                settings.setConfig("SiteUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/%s.php");
                settings.saveToFile();
                Path config = Path.of("properties.config");
                byte[] before = Files.readAllBytes(config);
                Path orders = Path.of("orders-test.rc.egf");
                Files.writeString(orders, "synthetic local test attachment");
                byte[] ordersBefore = Files.readAllBytes(orders);
                PartidaJogadorWebInfo info = new PartidaJogadorWebInfo();
                info.setAttachment(orders.toFile());
                info.setGameId(1);
                info.setGameTurn(7);
                info.setPlayerId(1);
                info.setPlayerLogin("sample.player");
                WebCounselorManager web = WebCounselorManager.getInstance();
                for (int mode = 0; mode < 3; mode++) {
                    info.setOnBehalf(mode != 0);
                    info.setShadow(mode == 2);
                    for (int code : new int[]{200, 202, 401, 403, 406, 429, 503}) {
                        status.set(code);
                        // Even an embedded 'player token' must not turn verification into a credential reset.
                        body.set(CHALLENGE + "<!-- player token -->");
                        int count = requests.get();
                        assertEquals(WebCounselorManager.ERROR_BROWSER_VERIFICATION, web.doSendViaPost(info));
                        assertEquals(count + 1, requests.get(), "No automatic retry");
                        assertEquals("Please wait while your request is being verified...", web.getLastResponseString());
                        assertArrayEquals(before, Files.readAllBytes(config));
                        assertArrayEquals(ordersBefore, Files.readAllBytes(orders));
                    }
                    status.set(202); body.set("Accepted");
                    assertEquals(WebCounselorManager.OK, web.doSendViaPost(info));
                    body.set("<pre>File orders.rc.egf is valid, and was successfully uploaded. Total=1;</pre>");
                    var accepted = web.doSendViaPostResult(info);
                    assertEquals(WebCounselorManager.OK, accepted.code(), "Real site's HTML202 remains success");
                    assertTrue(accepted.htmlResponse());
                    assertEquals("File orders.rc.egf is valid, and was successfully uploaded. Total=1;", accepted.message());
                    status.set(401); body.set("Invalid player token");
                    assertEquals(WebCounselorManager.ERROR_BADPLAYERTOKEN, web.doSendViaPost(info));
                    status.set(401); body.set("A valid EGF token is required");
                    assertEquals(mode == 0 ? WebCounselorManager.ERROR_UNKOWN : WebCounselorManager.ERROR_SERVERMSG,
                            web.doSendViaPost(info));
                    status.set(403); body.set("Game closed");
                    assertEquals(mode == 0 ? WebCounselorManager.ERROR_GAMECLOSED : WebCounselorManager.ERROR_SERVERMSG,
                            web.doSendViaPost(info));
                    status.set(406); body.set("8");
                    assertEquals(WebCounselorManager.ERROR_TURN, web.doSendViaPost(info));
                    assertEquals("8", web.getLastResponseString());
                    status.set(400); body.set("Invalid upload");
                    assertEquals(mode == 0 ? WebCounselorManager.ERROR_UNKOWN : WebCounselorManager.ERROR_SERVERMSG,
                            web.doSendViaPost(info));
                }
                for (int code : new int[]{200, 401, 403, 429, 503}) {
                    status.set(code); body.set("<html><body><h1>Maintenance</h1><p>Try later &amp; wait.</p></body></html>");
                    var upload = web.doSendViaPostResult(info);
                    assertEquals(WebCounselorManager.ERROR_HTML_RESPONSE, upload.code());
                    assertEquals("Maintenance Try later & wait.", upload.message());
                    body.set("<p>Access blocked dummy-secret " + "a".repeat(40) + "</p>");
                    WebsiteHtmlException tokenFailure = assertThrows(WebsiteHtmlException.class,
                            () -> web.fetchPlayerToken("sample.player", "dummy-secret"));
                    assertEquals(code, tokenFailure.getStatusCode());
                    assertEquals("Access blocked [redacted] [redacted]", tokenFailure.getWebsiteMessage());
                    body.set("<p>Access blocked 987654 " + "a".repeat(40) + "</p>");
                    WebsiteHtmlException hashFailure = assertThrows(WebsiteHtmlException.class,
                            () -> web.fetchOrdersHash(1, 7, 987654));
                    assertEquals("Access blocked [redacted] [redacted]", hashFailure.getWebsiteMessage());
                    // A later request cannot replace the upload's per-request message.
                    assertEquals("Maintenance Try later & wait.", upload.message());
                    assertArrayEquals(before, Files.readAllBytes(config));
                }
                status.set(401); body.set("<p>" + "Z".repeat(70000) + "player token dummy-secret</p>");
                assertEquals(WebCounselorManager.ERROR_HTML_RESPONSE, web.doSendViaPost(info));
                assertEquals("", assertThrows(WebsiteHtmlException.class,
                        () -> web.fetchPlayerToken("sample.player", "dummy-secret")).getWebsiteMessage());
                assertArrayEquals(before, Files.readAllBytes(config));
                assertArrayEquals(ordersBefore, Files.readAllBytes(orders));
            } finally {
                server.stop(0);
            }
        }
    }
}
