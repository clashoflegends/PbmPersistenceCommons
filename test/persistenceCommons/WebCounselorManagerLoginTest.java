package persistenceCommons;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class WebCounselorManagerLoginTest {
    private static final String TOKEN = "a".repeat(40);
    private static final String HASH = "7|" + "b".repeat(64);
    private static final String CHALLENGE = "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n"
            + "<title>One moment, please...</title>\n</head>\n<body>"
            + "Please wait while your request is being verified...</body>\n</html>";

    @Test
    void acceptsCurrentAndLegacyResponsesWithoutRestrictingOrdinaryAccountNames() throws Exception {
        for (String value : new String[]{TOKEN, HASH, "NONE"}) {
            boolean tokenResponse = value.equals(TOKEN);
            for (String ending : new String[]{"", "\n", "\r\n", "\n \n"}) {
                var parsed = WebCounselorManager.parseLoginResponse(value + ending, tokenResponse);
                assertEquals(value, parsed.value());
                assertEquals("", parsed.login());
            }
            for (String name : new String[]{"sample.player", "O'Brien", "Ren\u00e9 Example", "user+alias@example.org"}) {
                var parsed = WebCounselorManager.parseLoginResponse(" " + value + " \r\n" + name + "\r\n", tokenResponse);
                assertEquals(value, parsed.value());
                assertEquals(name, parsed.login());
            }
        }
    }

    @Test
    void rejectsMalformedRepliesBeforeAnyIdentityCanBeCaptured() {
        for (boolean tokenResponse : new boolean[]{true, false}) {
            String value = tokenResponse ? TOKEN : HASH;
            String[] invalid = {null, "", CHALLENGE, "error\nplausible.login", value + "\n<html>",
                value + "\nname\nextra", value + "\nname\t", value + "\nname\r",
                value + "\nname" + (char) 0, value + "\nname" + (char) 0x2028,
                value + "\nname" + (char) 0x2029, "{\"error\":\"blocked\"}",
                tokenResponse ? HASH : TOKEN};
            for (String body : invalid) {
                PersistenceException ex = assertThrows(PersistenceException.class,
                        () -> WebCounselorManager.parseLoginResponse(body, tokenResponse));
                assertEquals("The website returned an unexpected response. Please try again later.", ex.getMessage());
            }
        }
        for (String value : new String[]{"-1|" + "b".repeat(64), "2147483648|" + "b".repeat(64),
                "7|xyz", "7|" + "b".repeat(63), "none"}) {
            assertThrows(PersistenceException.class, () -> WebCounselorManager.parseLoginResponse(value + "\nname", false));
        }
        assertThrows(PersistenceException.class, () -> WebCounselorManager.parseLoginResponse("a".repeat(39), true));
    }

    @Test
    void httpAndRecoveryPreserveRealSettingsInAnIsolatedProcess(@TempDir Path directory) throws Exception {
        // The production path saves properties.config. A separate working directory prevents any
        // test (including a regression) from reading or overwriting the developer's configuration.
        Files.writeString(directory.resolve("properties.config"), "playerLogin=previous.player\n"
                + "playerToken=" + TOKEN + "\nunrelated=keep-me\n", StandardCharsets.ISO_8859_1);
        String classPath = Arrays.stream(System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path")).split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(entry -> Path.of(entry).toAbsolutePath().toString()).collect(Collectors.joining(File.pathSeparator));
        Path output = directory.resolve("probe.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classPath, Probe.class.getName()).directory(directory.toFile())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Isolated login probe timed out");
            assertEquals(0, process.exitValue(), () -> {
                try { return Files.readString(output); } catch (Exception ex) { return "Probe failed"; }
            });
        } finally {
            process.destroyForcibly();
        }
    }

    public static class Probe {
        public static void main(String[] args) throws Exception {
            System.clearProperty("jpackage.app-version");
            AtomicReference<String> body = new AtomicReference<>(CHALLENGE);
            AtomicInteger status = new AtomicInteger(200);
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try (InputStream request = exchange.getRequestBody()) { request.readAllBytes(); }
                byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                exchange.sendResponseHeaders(status.get(), response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            try {
                SettingsManager settings = SettingsManager.getInstance();
                settings.setConfig("SiteUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/%s.php");
                settings.saveToFile();
                WebCounselorManager web = WebCounselorManager.getInstance();
                Path config = Path.of("properties.config");
                byte[] before = Files.readAllBytes(config);
                assertThrows(PersistenceException.class, () -> web.fetchPlayerToken("sample.player", "dummy-password"));
                assertThrows(PersistenceException.class, () -> web.fetchOrdersHash(1, 7, 123));
                assertArrayEquals(before, Files.readAllBytes(config));
                assertEquals("previous.player", settings.getConfig("playerLogin", ""));
                assertEquals("Please wait while your request is being verified...", web.getLastResponseString());

                body.set(TOKEN + "\ncanonical.player");
                assertEquals(TOKEN, web.fetchPlayerToken("sample.player", "dummy-password"));
                assertEquals("canonical.player", readConfig().getProperty("playerLogin"));
                body.set(HASH + "\r\nupdated.player");
                assertEquals(HASH, web.fetchOrdersHash(1, 7, 123));
                assertEquals("updated.player", readConfig().getProperty("playerLogin"));
                before = Files.readAllBytes(config);
                for (String reply : new String[]{HASH, "NONE", "NONE\r\n"}) {
                    body.set(reply);
                    web.fetchOrdersHash(1, 7, 123);
                    assertArrayEquals(before, Files.readAllBytes(config));
                }
                body.set(TOKEN);
                assertEquals(TOKEN, web.fetchPlayerToken("sample.player", "dummy-password"));
                assertArrayEquals(before, Files.readAllBytes(config));
                status.set(401);
                body.set("Unauthorized\nwrong.player");
                assertNull(web.fetchPlayerToken("sample.player", "dummy-password"));
                assertNull(web.fetchOrdersHash(1, 7, 123));
                assertArrayEquals(before, Files.readAllBytes(config));

                web.clearInvalidPlayerLogin();
                assertArrayEquals(before, Files.readAllBytes(config));
                settings.setConfigAndSaveToFile("playerLogin", CHALLENGE);
                Properties expected = readConfig();
                expected.setProperty("playerLogin", "");
                web.clearInvalidPlayerLogin();
                assertEquals(expected, readConfig());
                assertEquals(TOKEN, settings.getConfig("playerToken", ""));
                assertEquals("", settings.getConfig("playerLogin", ""));
                status.set(200);
                body.set("NONE\nrestored.player");
                assertEquals("NONE", web.fetchOrdersHash(1, 7, 123));
                assertEquals("restored.player", readConfig().getProperty("playerLogin"));
            } finally {
                server.stop(0);
            }
        }

        private static Properties readConfig() throws Exception {
            Properties result = new Properties();
            try (InputStream input = Files.newInputStream(Path.of("properties.config"))) { result.load(input); }
            return result;
        }
    }
}
