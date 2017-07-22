/*
 * SysProperties.java
 *
 * Created on 28 de Marco de 2007, 13:02
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package persistenceCommons;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Properties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author gurgel
 */
final class SysProperties implements Serializable {

    private static final Log log = LogFactory.getLog(SysProperties.class);
    private Properties props;
    private final String propArqName = "properties.config";
    private final String comentario = "Counselor config file\n"
            + "filtro.default=0|1 -> All|Own\n"
            + "SortAllCombos=0|1 -> Off|On\n"
            + "FogOfWarType=0|1 -> Off|On\n"
            + "maximizeWindowOnStart = 0|1 -> normal|maximize\n"
            + "minimizeMapOnStart = 0|1 -> view|hide\n"
            + "saveDir =/folder/folder/, default folder to save orders (use / as opposed to \\).\n"
            + "loadDir =/folder/folder/, default folder to load results and orders (use / as opposed to \\).\n"
            + "autoLoad =/folders/file file name that you want for the Results to be loaded every time (use / as opposed to \\).\n"
            + "autoLoadActions =/folders/file file name for the Action that you want to be loaded every time (use / as opposed to \\).\n"
            //            + "doneDir = server side use, no effect on the Counselor\n"
            //            + "local = server side use, no effect on the Counselor\n"
            //            + "database = server side use, no effect on the Counselor\n"
            + "language = en/pt/it, to define the GUI language.\n"
            + "splitSize = 300, define the size of the left side screen split.\n"
            + "TableColumnAdjust = 0|1 -> Yes|No, adjust columns during \"Load Action\" of changing filters.\n"
            + "TableActionColumnAdjust = 0|1 -> Yes|No, adjust Action columns during \"Load Action\" or changing filters.\n"
            + "CopyActionsPopUp = 0|1 -> Yes|No, displays a popup when copying Actions to clipboard.\n"
            + "CopyActionsOrder = 0|1 -> Displays the list of actions per character.\n"
            + "HexTagStyle = 0-3, changes the Hex Tag Style.\n"
            + "HexTagFrame = 0|1, changes the Hex Tag Border Style (0 or 1).\n"
            + "KeepPopupOpen = 0|1 -> Yes|No, open multiple hex's info popups.\n"
            + "MyEmail=user@domain, define your email address.\n"
            + "OverrideElimination=0|1 -> allows you to send actions past elimination.\n"
            + "SendOrderConfirmationPopUp=0|1 - Show pop-up message with confirmation or not.\n"
            + "SendOrderReceiptRequest=0|1 - Request site to send a confirmation receipt or not.\n"
            + "ShowArmyMovPath=0|1|2 -> allows you to see all possible movement paths for an army(1) or navy(2). (0) disable it.\n"
            + "MapTiles = 2b | 2d | 2a | 3d, changes the basic hex terrain tiles.\n"
            + "AutoMoveNextAction = 0|1, changes the behavior entering actions. If =1, then move to next available slot.\n"
            + "mail.smtp.server=smtp.myserver.com, smtp server name to be used.\n"
            + "mail.smpt.port=25, smtp port to be used. Only port 25 is supported right now.\n"
            + "mail.smtp.user=myuser, username for smtp authentication.\n"
            + "mail.smtp.passwd=my password, password for smtp authentication.\n"
            + "drawPcPath=1\n"
            + "LoadActionsBehavior=clean | append -> clear all orders before loading new orders file or just append to existing.\n"
            + "LoadActionsOtherNations=deny | allow -> allows or forbids loading actions from other nations files.\n"
            + "LookAndFeelTheme=0 | Metal | Nimbus -> forces Counselor to a specific theme, these two look better on high resolution screens."
            + "LookAndFeelFontSize= 0-72 -> select the system default font size.\n"
            + "TableRowAdjust = 0|1 -> Yes|No, adjust row heights to the content size, important if using larger fonts.\n"
            + "ActionListHeight = 107-999 -> defines the size for the action list window under the character's tab, to look better on high resolution screens."
            + "MoveTagTransparency = 0-10 | defines how trasnparent the distance disks are when ploted on map. 0 means no trasnparency, 10 means fully transparent."
            + "\n";

    /**
     * Creates a new instance of SysProperties
     */
    protected SysProperties() {
        props = new Properties();
        doConfigRestore();
    }

    private String getPropArq() {
        return propArqName;
    }

    private Properties getProps() {
        return props;
    }

    protected String getProps(String key) {
        return getProps().getProperty(key);
    }

    protected String getProps(String key, String defaultValue) {
        return getProps().getProperty(key, defaultValue);
    }

    protected void setProp(String key, String value) {
        getProps().setProperty(key, value);
    }

    protected boolean isSet(String key) {
        return !getProps().getProperty(key, "SBRIFTS").equals("SBRIFTS");
    }

    /**
     * para ler um arquivo de propriedades
     *
     */
    private boolean doLoadFromFile() {
        try {
            props = doLoadPropertiesFile(getPropArq());
            return true;
        } catch (IOException e) {
            //file not found, must create new with default values
            return false;
        }
    }

    protected Properties doLoadPropertiesFile(String fileName) throws IOException {
        Properties properties = new Properties();
        InputStream propIn = new FileInputStream(new File(fileName));
        properties.load(propIn);
        return properties;
    }

    /**
     * if file not found, then create a new one
     *
     * @return
     */
    protected boolean doConfigRestore() {
        if (!this.doLoadFromFile()) {
            this.doCreateFile();
        }
        return true;
    }

    protected Enumeration<?> listKeys() {
        return getProps().keys();
    }

    protected boolean doCreateFile() {
        log.info("Creating new config file");
        // create new file with default values
        this.setPropDefault();
        return doSavePropertiesToFile();
    }

    protected boolean doSavePropertiesToFile() {
        log.debug("Saving properties configs to file");
        try {
            OutputStream propFile = new FileOutputStream(new File(getPropArq()));
            getProps().store(propFile, comentario);
            return true;
        } catch (IOException e) {
            log.error("Error while creating new config file.");
            return false;
        }
    }

    private void setPropDefault() {
        getProps().setProperty("filtro.default", "0");
        getProps().setProperty("TableColumnAdjust", "0");
        getProps().setProperty("maximizeWindowOnStart", "1");
        getProps().setProperty("minimizeMapOnStart", "0");
        getProps().setProperty("saveDir", "/clash/");
        getProps().setProperty("loadDir", "/clash/");
        getProps().setProperty("language", "en");
        getProps().setProperty("splitSize", "660");
        getProps().setProperty("CopyActionsPopUp", "1");
        getProps().setProperty("CopyActionsOrder", "1");
        getProps().setProperty("MyEmail", "none");
        getProps().setProperty("KeepPopupOpen", "0");
        getProps().setProperty("HexTagStyle", "0");
        getProps().setProperty("HexTagFrame", "0");
        getProps().setProperty("ShowArmyMovPath", "1");
        getProps().setProperty("MapTiles", "2b");
        getProps().setProperty("KeepPopupOpen", "0");
        getProps().setProperty("AutoMoveNextAction", "1");
        getProps().setProperty("SendOrderConfirmationPopUp", "1");
        getProps().setProperty("SendOrderReceiptRequest", "1");
        getProps().setProperty("LookAndFeelTheme", "0");
        getProps().setProperty("MoveTagTransparency", "2");
        getProps().setProperty("ActionListHeight", "107");
        getProps().setProperty("TableRowAdjust", "1");
        getProps().setProperty("TableActionColumnAdjust", "0");
        getProps().setProperty("LoadActionsOtherNations", "allow");
        getProps().setProperty("LoadActionsBehavior", "append");
    }
}
