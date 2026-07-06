/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package persistenceCommons;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import modelWeb.PartidaJogadorWebInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

/**
 *
 * @author GM Team
 */
public class WebCounselorManager {

    private static final Log log = LogFactory.getLog(WebCounselorManager.class);
    private static WebCounselorManager instance;
    private static final String siteUrl = "http://clashlegends.com/PbmSite/%s.php";
    public static final int OK = 202;
    public static final int ERROR_GAMECLOSED = 403;
    public static final int ERROR_TURN = 406;
    public static final int ERROR_BADPLAYERTOKEN = 401;
    // On-behalf / stand-by gate rejection (Phase-2 #1 + #4). The Site rejects an on-behalf or shadow
    // submission with a 400/401/403 that carries an actionable, human-readable body (e.g. "This player does
    // not accept orders submitted on their behalf." / "...does not accept stand-by orders." / "A valid EGF
    // token is required..."). The client cannot pre-validate the owner's ds_onbehalf / fl_accept_shadow, so
    // rejection is a NORMAL outcome; the caller surfaces getLastResponseString() verbatim. Returned ONLY for
    // on-behalf or shadow submissions, so a normal (self) submit keeps its existing localized messages.
    public static final int ERROR_SERVERMSG = 460;
    // HTTP timeouts. A bare DefaultHttpClient has NONE (connect + read default to 0 = infinite), so a hung
    // site would spin the busy overlay forever and a dead host would wait on the OS TCP timeout (~21s on
    // Windows). Bound both. The UPLOAD read timeout is generous because the upload POST blocks on the Site's
    // synchronous receipt email (KI-020: ~7-16s inside the request); the token / orders-hash fetches are quick.
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int UPLOAD_READ_TIMEOUT_MS = 60_000;
    private static final int FETCH_READ_TIMEOUT_MS = 15_000;

    /** A DefaultHttpClient with bounded connect + socket (read) timeouts, so a down/hung site fails cleanly. */
    private static HttpClient newHttpClient(int readTimeoutMs) {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, CONNECT_TIMEOUT_MS);
        HttpConnectionParams.setSoTimeout(params, readTimeoutMs);
        return new DefaultHttpClient(params);
    }
    public static final int ERROR_UNKOWN = 0;
    private int lastStatusCode;
    private String lastResponseString;

    private WebCounselorManager() {
    }

    public synchronized static WebCounselorManager getInstance() {
        if (instance == null) {
            log.debug("Criou instancia do WebManager.");
            instance = new WebCounselorManager();
        }
        return instance;
    }

//    public int doSendViaPost(File attachment, Partida partida, String textBody) throws PersistenceException {
    public int doSendViaPost(PartidaJogadorWebInfo info) throws PersistenceException {
        try {
            HttpClient client = newHttpClient(UPLOAD_READ_TIMEOUT_MS);

            MultipartEntity entity = new MultipartEntity();
            // o primeiro parametro eh o nome do "campo" onde se espera enviar o arquivo. Deve
            // ser igual ao determinado no PHP. O segundo eh o arquivo local
            entity.addPart("userfile", new FileBody(info.getAttachment()));
            // Per-player identity token (jogador.cd_token), delivered via the site token page / fetch
            // endpoint and stored in properties.config. This is the sole upload credential from 2026-07
            // on (the old shared pToken is no longer sent); the upload flow guarantees it is set before
            // we reach here. The Site authenticates the sender against it.
            final String playerToken = SettingsManager.getInstance().getConfig("playerToken", "");
            if (!playerToken.isEmpty()) {
                entity.addPart("pPlayerToken", new StringBody(playerToken));
            }
            // Only send pEgfToken when populated — omitted from EGF during old-Counselor
            // transition period. Remove guard after jpackage distribution.
            if (info.getCdToken() > 0) {
                entity.addPart("pEgfToken", new StringBody(info.getCdToken() + ""));
            }
            entity.addPart("pPartida", new StringBody(info.getGameId() + ""));
            entity.addPart("pTurno", new StringBody(info.getGameTurn() + ""));
            entity.addPart("pJogador", new StringBody(info.getPlayerId() + ""));
            entity.addPart("pJogadorLogin", new StringBody(info.getPlayerLogin()));
            entity.addPart("pJavaVersion", new StringBody(SysApoio.getVersionJava()));
            entity.addPart("pOsVersion", new StringBody(SysApoio.getVersionOs()));
            entity.addPart("pCounselorVersion", new StringBody(SysApoio.getVersionClash("version_counselor")));
            entity.addPart("pCommonsVersion", new StringBody(SysApoio.getVersionClash("version_commons")));
            entity.addPart("pScreenSize", new StringBody(SysApoio.getScreenSize()));
            entity.addPart("pOsScale", new StringBody(SysApoio.getOsScale()));
            entity.addPart("pMapZoom", new StringBody(SettingsManager.getInstance().getConfig("MapZoom", "1.0")));
            entity.addPart("pTheme", new StringBody(SettingsManager.getInstance().getConfig("LookAndFeelTheme", "FlatLight")));
            entity.addPart("pStartupMs", new StringBody(SysApoio.getStartupTimeMs() + ""));
            entity.addPart("pLoadMode", new StringBody(SysApoio.getLoadMode()));
            entity.addPart("pDistro", new StringBody(SysApoio.getDistro()));
            entity.addPart("pPortraitStatus", new StringBody(getbooleanStatus(SettingsManager.getInstance().isPortrait())));
            entity.addPart("pMapTiles", new StringBody(SettingsManager.getInstance().getConfig("MapTiles", "2b")));
            entity.addPart("pLanguage", new StringBody(SettingsManager.getInstance().getConfig("language", "??")));
            entity.addPart("pOrdersAutoSave", new StringBody(getbooleanStatus(SettingsManager.getInstance().isAutoSaveActions())));
            // Hash of the canonical order set, so the site can later answer "are these orders the
            // ones it holds?" (the order-sync status indicator). Client computes it; site stores it
            // verbatim. Empty when the caller did not set one (keeps old behaviour intact).
            entity.addPart("pOrdersHash", new StringBody(info.getOrdersHash() == null ? "" : info.getOrdersHash()));
            // Stand-by / SHADOW set (Phase-2 #4): only sent when the sender explicitly chose stand-by for an
            // on-behalf submission. Absent = a normal submission (byte-identical to old clients).
            if (info.isShadow()) {
                entity.addPart("pShadow", new StringBody("1"));
            }
            if (SettingsManager.getInstance().getConfig("SendOrderReceiptRequest", "1").equals("1")) {
                entity.addPart("pTextBody", new StringBody(info.getOrders()));
                entity.addPart("pPartidaName", new StringBody(info.getGameNm()));
                entity.addPart("pJogadorEmail", new StringBody(info.getPlayerEmail()));
            }

            // aqui define a URL
            final String uploadPage = SettingsManager.getInstance().getConfig("UploadPage", "CounselorUploadTurn");
            long start = System.currentTimeMillis();

            HttpPost post = new HttpPost(getUrl(uploadPage));
            post.setEntity(entity);

            // faz a chamada......
            //String response = client.execute(post, new BasicResponseHandler());
            HttpResponse response = client.execute(post);
            long end = System.currentTimeMillis();
            if (SettingsManager.getInstance().isConfig("DebugWebpostTime", "1", "0")) {
                log.info("Executing request: " + post.getRequestLine());
                log.info("Round trip response time from server = " + (end - start) + " milliseconds");
            }

            setLastStatusCode(response.getStatusLine().getStatusCode());
            setLastResponseString(responseToString(response));
            // Always log the outcome of a rejected upload at a visible level so counselor.log records WHY a
            // submission failed (status + reason + the site's message body), for every path below.
            if (response.getStatusLine().getStatusCode() != OK) {
                log.warn(String.format("Upload rejected by %s: HTTP %d %s | body=%s",
                        uploadPage, response.getStatusLine().getStatusCode(),
                        response.getStatusLine().getReasonPhrase(), getLastResponseString()));
            }
            //process responses
            switch (response.getStatusLine().getStatusCode()) {
                case OK:
                    return OK;
                case ERROR_GAMECLOSED:
                    log.debug(getLastResponseString());
                    // For an on-behalf or stand-by submission a 403 is the gate ("...does not accept orders
                    // submitted on their behalf." / "...teammates... may submit." / "...does not accept
                    // stand-by orders.") — surface it verbatim rather than the generic "game closed" message.
                    // Only a plain self-submit keeps ERROR_GAMECLOSED (the genuine game-closed case).
                    if (info.isShadow() || info.isOnBehalf()) {
                        return ERROR_SERVERMSG;
                    }
                    return ERROR_GAMECLOSED;
                case ERROR_TURN:
                    log.debug(getLastResponseString());
                    List<String> ret = new ArrayList<>();
                    ret.addAll(Arrays.asList(SysApoio.stringToArray(getLastResponseString())));
                    setLastResponseString(ret.get(0));
                    return ERROR_TURN;
                case ERROR_BADPLAYERTOKEN: // 401: distinguish a recoverable bad player token from other 401s (e.g. bad EGF token)
                    // Contract: keyed on the substring "player token" in CounselorUploadTurn.php's 401 body. Keep in sync.
                    if (getLastResponseString() != null && getLastResponseString().toLowerCase().contains("player token")) {
                        return ERROR_BADPLAYERTOKEN;
                    }
                    // An on-behalf/stand-by submission also 401s on a bad/absent EGF token ("A valid EGF token
                    // is required...") — surface that body instead of the generic error.
                    if (info.isShadow() || info.isOnBehalf()) {
                        return ERROR_SERVERMSG;
                    }
                    // other 401 -> fall through to generic error logging
                default:
                    // An on-behalf/stand-by submission rejected with 400 ("Stand-by orders can only be
                    // submitted on another player's behalf." / "Invalid upload.") carries an actionable body —
                    // surface it. Plain self 400s stay generic. (403 and 401 rejects are handled above.)
                    if ((info.isShadow() || info.isOnBehalf()) && response.getStatusLine().getStatusCode() == 400) {
                        return ERROR_SERVERMSG;
                    }
                    log.error(response.getProtocolVersion());
                    log.error(response.getStatusLine().getStatusCode());
                    log.error(response.getStatusLine().getReasonPhrase());
                    log.error(getLastResponseString());
                    break;
            }
        } catch (URISyntaxException ex) {
            throw new PersistenceException("Can't connect to site (http://clashlegends.com/PbmSite/): " + ex.toString());
        } catch (IOException ex) {
            throw new PersistenceException("Can't read remote file to write. Failed to send to website.");
        }
        return ERROR_UNKOWN;
    }

    /**
     * Fetches the player's per-player token (jogador.cd_token) from the site by login + password.
     * Returns the token on success (HTTP 200), null on bad credentials / rate limit (4xx), and
     * throws on a network/IO error. Used by the first-upload token setup dialog. The password is
     * sent once and never stored. See PbmSite/CounselorGetToken.php.
     */
    public String fetchPlayerToken(String login, String password) throws PersistenceException {
        try {
            HttpClient client = newHttpClient(FETCH_READ_TIMEOUT_MS);
            MultipartEntity entity = new MultipartEntity();
            entity.addPart("pLogin", new StringBody(login));
            entity.addPart("pSenha", new StringBody(password));
            entity.addPart("pWithLogin", new StringBody("1")); // ask the site to append our login (2nd line)
            final String page = SettingsManager.getInstance().getConfig("GetTokenPage", "CounselorGetToken");
            HttpPost post = new HttpPost(getUrl(page));
            post.setEntity(entity);
            HttpResponse response = client.execute(post);
            int code = response.getStatusLine().getStatusCode();
            setLastStatusCode(code);
            String body = responseToString(response);
            setLastResponseString(body);
            if (code == 200) {
                return captureLoginAndStripBody(body);
            }
            log.warn("Token fetch failed (HTTP " + code + ")");
            return null; // 401 bad creds / 429 rate limited - caller shows a friendly message
        } catch (URISyntaxException ex) {
            throw new PersistenceException("Can't connect to site (http://clashlegends.com/PbmSite/): " + ex.toString());
        } catch (IOException ex) {
            throw new PersistenceException("Failed to fetch token from website.");
        }
    }

    /**
     * Fetches the orders hash the site currently holds for this game/turn/nation, so the Counselor
     * can tell whether the loaded order set matches what was sent. Authenticated by the per-player
     * token (must be saved) + the per-EGF token (scopes the nation). Returns the raw response body
     * ("<turno>|<hash>", or "NONE" when nothing is stored) on HTTP 200, null on 4xx, throws on IO.
     * See PbmSite/CounselorGetOrdersHash.php.
     */
    public String fetchOrdersHash(int gameId, int gameTurn, int egfToken) throws PersistenceException {
        final String playerToken = SettingsManager.getInstance().getConfig("playerToken", "");
        if (playerToken.isEmpty()) {
            return null; // no token => can't authenticate; caller renders the "no token" state
        }
        try {
            HttpClient client = newHttpClient(FETCH_READ_TIMEOUT_MS);
            MultipartEntity entity = new MultipartEntity();
            entity.addPart("pPlayerToken", new StringBody(playerToken));
            entity.addPart("pPartida", new StringBody(gameId + ""));
            entity.addPart("pTurno", new StringBody(gameTurn + ""));
            entity.addPart("pEgfToken", new StringBody(egfToken + ""));
            entity.addPart("pWithLogin", new StringBody("1")); // also refresh our login (appended 2nd line)
            final String page = SettingsManager.getInstance().getConfig("GetOrdersHashPage", "CounselorGetOrdersHash");
            HttpPost post = new HttpPost(getUrl(page));
            post.setEntity(entity);
            HttpResponse response = client.execute(post);
            int code = response.getStatusLine().getStatusCode();
            setLastStatusCode(code);
            String body = responseToString(response);
            setLastResponseString(body);
            if (code == 200) {
                return captureLoginAndStripBody(body);
            }
            log.warn("Orders-hash fetch failed (HTTP " + code + ")");
            return null;
        } catch (URISyntaxException ex) {
            throw new PersistenceException("Can't connect to site (http://clashlegends.com/PbmSite/): " + ex.toString());
        } catch (IOException ex) {
            throw new PersistenceException("Failed to fetch the orders hash from website.");
        }
    }

    /**
     * When we send pWithLogin=1, the token/orders-hash endpoints append this machine's player login on a
     * SECOND line so the Counselor learns who it is (the SENDER, for on-behalf attribution). We store it in
     * config and return ONLY the first line, byte-identical to the legacy body, so existing callers/parsers
     * (TokenSetupDialog, OrdersHashService) are unaffected. Old sites echo no second line -> plain no-op.
     */
    private String captureLoginAndStripBody(String body) {
        if (body == null) {
            return null;
        }
        final int nl = body.indexOf('\n');
        if (nl < 0) {
            return body.trim();
        }
        final String first = body.substring(0, nl).trim();
        final String login = body.substring(nl + 1).trim();
        if (!login.isEmpty()
                && !login.equals(SettingsManager.getInstance().getConfig("playerLogin", ""))) {
            SettingsManager.getInstance().setConfigAndSaveToFile("playerLogin", login);
        }
        return first;
    }

    private URI getUrl(String webpage) throws URISyntaxException {
        return new URI(String.format(siteUrl, webpage));
    }

    private String responseToString(HttpResponse response) throws ParseException, IOException {
        return EntityUtils.toString(response.getEntity());
    }

    public int getLastStatusCode() {
        return lastStatusCode;
    }

    private void setLastStatusCode(int lastStatusCode) {
        this.lastStatusCode = lastStatusCode;
    }

    public String getLastResponseString() {
        return lastResponseString;
    }

    private void setLastResponseString(String lastResponseString) {
        this.lastResponseString = lastResponseString;
    }

    private String getbooleanStatus(boolean portrait) {
        if (portrait) {
            return "ON";
        } else {
            return "OFF";
        }
    }
}
