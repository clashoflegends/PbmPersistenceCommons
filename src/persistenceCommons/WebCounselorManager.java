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
import org.apache.http.util.EntityUtils;

/**
 *
 * @author jmoura
 */
public class WebCounselorManager {

    private static final Log log = LogFactory.getLog(WebCounselorManager.class);
    private static WebCounselorManager instance;
    private static final String siteUrl = "http://clashlegends.com/PbmSite/%s.php";
    public static final int OK = 202;
    public static final int ERROR_GAMECLOSED = 403;
    public static final int ERROR_TURN = 406;
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
            HttpClient client = new DefaultHttpClient();

            MultipartEntity entity = new MultipartEntity();
            // o primeiro parametro eh o nome do "campo" onde se espera enviar o arquivo. Deve
            // ser igual ao determinado no PHP. O segundo eh o arquivo local
            entity.addPart("userfile", new FileBody(info.getAttachment()));
            final String token = SettingsManager.getInstance().getConfig("counselorToken", "4vHZA0EfimmurFsLLXO6Aj9MXAmNk7fvB23b7x43");
            if (token.isEmpty()) {
                log.warn("counselorToken not set in properties.config — upload will be rejected by server.");
            }
            entity.addPart("pToken", new StringBody(token));
            entity.addPart("pEgfToken", new StringBody(info.getCdToken() + ""));
            entity.addPart("pPartida", new StringBody(info.getGameId() + ""));
            entity.addPart("pTurno", new StringBody(info.getGameTurn() + ""));
            entity.addPart("pJogador", new StringBody(info.getPlayerId() + ""));
            entity.addPart("pJogadorLogin", new StringBody(info.getPlayerLogin()));
            entity.addPart("pJavaVersion", new StringBody(SysApoio.getVersionJava()));
            entity.addPart("pOsVersion", new StringBody(SysApoio.getVersionOs()));
            entity.addPart("pCounselorVersion", new StringBody(SysApoio.getVersionClash("version_counselor")));
            entity.addPart("pCommonsVersion", new StringBody(SysApoio.getVersionClash("version_commons")));
            entity.addPart("pScreenSize", new StringBody(SysApoio.getScreenSize()));
            entity.addPart("pPortraitStatus", new StringBody(getbooleanStatus(SettingsManager.getInstance().isPortrait())));
            entity.addPart("pMapTiles", new StringBody(SettingsManager.getInstance().getConfig("MapTiles", "2b")));
            entity.addPart("pLanguage", new StringBody(SettingsManager.getInstance().getConfig("language", "??")));
            entity.addPart("pOrdersAutoSave", new StringBody(getbooleanStatus(SettingsManager.getInstance().isAutoSaveActions())));
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
            //process responses
            switch (response.getStatusLine().getStatusCode()) {
                case OK:
                    return OK;
                case ERROR_GAMECLOSED:
                    log.debug(getLastResponseString());
                    return ERROR_GAMECLOSED;
                case ERROR_TURN:
                    log.debug(getLastResponseString());
                    List<String> ret = new ArrayList<>();
                    ret.addAll(Arrays.asList(SysApoio.stringToArray(getLastResponseString())));
                    setLastResponseString(ret.get(0));
                    return ERROR_TURN;
                default:
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
