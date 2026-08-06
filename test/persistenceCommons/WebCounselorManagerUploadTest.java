package persistenceCommons;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import modelWeb.PartidaJogadorWebInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The last leg of the happy path: submitting the saved orders file to the site.
 * <p>
 * It runs against a throwaway HTTP server on localhost - never the real site - reached by pointing
 * the {@code SiteUrl} config at it. Two things are pinned here:
 * <ul>
 *   <li><b>What goes on the wire.</b> Every field {@code CounselorUploadTurn.php} authenticates and
 *       records must be present, and the {@code Content-Type} must carry <i>only</i> a boundary
 *       parameter: on 2026-07-27 the host began rejecting (403, from Apache, before PHP ran) any
 *       multipart request whose Content-Type had an extra parameter, and httpmime always appends
 *       {@code charset}. That outage broke every upload for every player.</li>
 *   <li><b>How each answer is read.</b> The site says "no" in several ways, and the client must tell
 *       a recoverable bad token from a closed game from an on-behalf refusal, because each drives a
 *       different message to the player.</li>
 * </ul>
 */
class WebCounselorManagerUploadTest {

    private static HttpServer server;
    private static final AtomicInteger responseStatus = new AtomicInteger(202);
    private static final AtomicReference<String> responseBody = new AtomicReference<>("OK");
    private static final AtomicReference<String> lastBody = new AtomicReference<>("");
    private static final AtomicReference<String> lastContentType = new AtomicReference<>("");

    @BeforeAll
    static void startStubSite() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", WebCounselorManagerUploadTest::handle);
        server.start();
        SettingsManager.getInstance().setConfig("SiteUrl",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/%s.php");
        SettingsManager.getInstance().setConfig("playerToken", "TOKEN-FOR-TEST");
    }

    @AfterAll
    static void stopStubSite() {
        if (server != null) {
            server.stop(0);
        }
        // Leave no override behind: another test in the same JVM must not talk to 127.0.0.1.
        SettingsManager.getInstance().setConfig("SiteUrl", "");
    }

    @BeforeEach
    void resetStub() {
        responseStatus.set(WebCounselorManager.OK);
        responseBody.set("OK");
        lastBody.set("");
        lastContentType.set("");
    }

    private static void handle(HttpExchange exchange) throws IOException {
        lastContentType.set(String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")));
        try (InputStream in = exchange.getRequestBody()) {
            lastBody.set(new String(in.readAllBytes(), StandardCharsets.ISO_8859_1));
        }
        byte[] out = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatus.get(), out.length);
        exchange.getResponseBody().write(out);
        exchange.close();
    }

    @Test
    void acceptedUploadReportsSuccess() throws Exception {
        assertEquals(WebCounselorManager.OK,
                WebCounselorManager.getInstance().doSendViaPost(uploadInfo()));
    }

    @Test
    void sendsEverythingTheSiteAuthenticatesAndRecords() throws Exception {
        PartidaJogadorWebInfo info = uploadInfo();
        WebCounselorManager.getInstance().doSendViaPost(info);

        String body = lastBody.get();
        assertTrue(body.contains("name=\"userfile\""), "the orders file itself is missing");
        assertTrue(body.contains(info.getAttachment().getName()), "the orders file name is missing");
        assertTrue(body.contains("name=\"pPlayerToken\"") && body.contains("TOKEN-FOR-TEST"),
                "the per-player upload credential is missing");
        assertTrue(body.contains("name=\"pEgfToken\"") && body.contains("987654"),
                "the per-EGF token is missing");
        assertTrue(body.contains("name=\"pPartida\"") && body.contains("892"), "game id");
        assertTrue(body.contains("name=\"pTurno\"") && body.contains("13"), "turn");
        assertTrue(body.contains("name=\"pJogadorLogin\"") && body.contains("alexandre"), "player login");
        assertTrue(body.contains("name=\"pOrdersHash\"") && body.contains("abc123hash"),
                "the orders hash the sent-indicator compares against is missing");
    }

    /** Regression guard for the 2026-07-27 outage: any extra Content-Type parameter is refused by the host. */
    @Test
    void contentTypeCarriesOnlyABoundary() throws Exception {
        WebCounselorManager.getInstance().doSendViaPost(uploadInfo());

        String contentType = lastContentType.get();
        assertTrue(contentType.startsWith("multipart/form-data; boundary="),
                "unexpected Content-Type: " + contentType);
        assertEquals(1, contentType.split(";").length - 1,
                "Content-Type must carry the boundary and nothing else, got: " + contentType);
        assertFalse(contentType.toLowerCase().contains("charset"),
                "charset in the Content-Type is what the host rejected: " + contentType);
    }

    @Test
    void aStandBySubmissionIsFlaggedAndANormalOneIsNot() throws Exception {
        WebCounselorManager.getInstance().doSendViaPost(uploadInfo());
        assertFalse(lastBody.get().contains("name=\"pShadow\""),
                "a normal submission must not be flagged as stand-by");

        PartidaJogadorWebInfo shadow = uploadInfo();
        shadow.setShadow(true);
        WebCounselorManager.getInstance().doSendViaPost(shadow);
        assertTrue(lastBody.get().contains("name=\"pShadow\""), "the stand-by flag did not reach the site");
    }

    @Test
    void aBadPlayerTokenIsReportedAsRecoverable() throws Exception {
        responseStatus.set(401);
        responseBody.set("Invalid player token.");

        assertEquals(WebCounselorManager.ERROR_BADPLAYERTOKEN,
                WebCounselorManager.getInstance().doSendViaPost(uploadInfo()),
                "a bad player token must be recoverable (clear it and re-prompt), not a generic failure");
    }

    @Test
    void aClosedGameIsReportedAsClosed() throws Exception {
        responseStatus.set(403);
        responseBody.set("Game is closed.");

        assertEquals(WebCounselorManager.ERROR_GAMECLOSED,
                WebCounselorManager.getInstance().doSendViaPost(uploadInfo()));
    }

    /** On-behalf and stand-by refusals carry a reason the player needs to read verbatim. */
    @Test
    void anOnBehalfRefusalSurfacesTheSitesOwnMessage() throws Exception {
        responseStatus.set(403);
        responseBody.set("This player does not accept orders submitted on their behalf.");
        PartidaJogadorWebInfo info = uploadInfo();
        info.setOnBehalf(true);

        assertEquals(WebCounselorManager.ERROR_SERVERMSG,
                WebCounselorManager.getInstance().doSendViaPost(info));
        assertEquals("This player does not accept orders submitted on their behalf.",
                WebCounselorManager.getInstance().getLastResponseString());
    }

    @Test
    void aWrongTurnIsReportedAsAWrongTurn() throws Exception {
        responseStatus.set(406);
        responseBody.set("Turn 12 is not the current turn");

        assertEquals(WebCounselorManager.ERROR_TURN,
                WebCounselorManager.getInstance().doSendViaPost(uploadInfo()));
    }

    private static PartidaJogadorWebInfo uploadInfo() throws IOException {
        File orders = File.createTempFile("orders_test", ".rc.egf");
        orders.deleteOnExit();
        Files.write(orders.toPath(), "not a real egf, the site only stores the bytes".getBytes(StandardCharsets.UTF_8));

        PartidaJogadorWebInfo info = new PartidaJogadorWebInfo();
        info.setAttachment(orders);
        info.setGameId(892);
        info.setGameTurn(13);
        info.setGameNm("Test Game");
        info.setPlayerId(477);
        info.setPlayerLogin("alexandre");
        info.setPlayerEmail("player@example.com");
        info.setCdToken(987654);
        info.setOrdersHash("abc123hash");
        info.setOrders("Order list body");
        return info;
    }
}
