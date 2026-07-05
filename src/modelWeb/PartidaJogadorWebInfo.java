package modelWeb;

import java.io.File;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author GM Team 
 */
public class PartidaJogadorWebInfo {

    private File attachment;
    private String orders;
    private int gameId, gameTurn;
    private String gameNm;
    private int playerId;
    private String playerLogin;
    private String playerEmail;
    private int cdToken;
    private String ordersHash;
    // Stand-by / SHADOW set (Phase-2 #4): per-submission flag, only ever true for an on-behalf submission
    // the sender explicitly marked as stand-by. Drives the pShadow POST part; the EGF itself is unchanged.
    private boolean shadow;

    public boolean isShadow() {
        return shadow;
    }

    public void setShadow(boolean shadow) {
        this.shadow = shadow;
    }

    public File getAttachment() {
        return attachment;
    }

    public String getOrdersHash() {
        return ordersHash;
    }

    public void setOrdersHash(String ordersHash) {
        this.ordersHash = ordersHash;
    }

    public void setAttachment(File attachment) {
        this.attachment = attachment;
    }

    public String getOrders() {
        return orders;
    }

    public void setOrders(String orders) {
        this.orders = orders;
    }

    public int getGameId() {
        return gameId;
    }

    public void setGameId(int gameId) {
        this.gameId = gameId;
    }

    public String getGameNm() {
        return gameNm;
    }

    public void setGameNm(String gameNm) {
        this.gameNm = gameNm;
    }

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public int getGameTurn() {
        return gameTurn;
    }

    public void setGameTurn(int gameTurn) {
        this.gameTurn = gameTurn;
    }

    public String getPlayerEmail() {
        return playerEmail;
    }

    public void setPlayerEmail(String playerEmail) {
        this.playerEmail = playerEmail;
    }

    public String getPlayerLogin() {
        return playerLogin;
    }

    public void setPlayerLogin(String playerLogin) {
        this.playerLogin = playerLogin;
    }

    public int getCdToken() {
        return cdToken;
    }

    public void setCdToken(int cdToken) {
        this.cdToken = cdToken;
    }
}
