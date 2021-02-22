/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package persistenceCommons;

import java.io.IOException;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author gurgel
 */
public final class SettingsManager implements Serializable {

    private static final Log log = LogFactory.getLog(SettingsManager.class);
    private static SettingsManager instance;
    private String configurationMode = "Client";
    private Locale locale;
    private boolean debug = false;
    private boolean rules = false;
    private BundleManager bundleManager;
    private boolean worldBuilder = false;
    private boolean portrait = false;
    private boolean tableColumnAdjust = true;
    private boolean radialMenu = false;
    private Properties newGameProp;
    private final SysProperties propertiesConfig;

    private SettingsManager() {
        this.propertiesConfig = new SysProperties();
    }

    public synchronized static SettingsManager getInstance() {
        if (SettingsManager.instance == null) {
            SettingsManager.instance = new SettingsManager();
        }
        return SettingsManager.instance;
    }

    public String getConfigurationMode() {
        return (SettingsManager.instance.configurationMode);
    }

    public void setConfigurationMode(String modoAcesso) {
        log.debug("Alterando modo de acesso: " + modoAcesso);
        SettingsManager.instance.configurationMode = modoAcesso;
    }

    public boolean isAutoSaveActions() {
        return isConfig("AutoSaveActions", "1", "1");
    }

    public boolean isDebug() {
        return SettingsManager.instance.debug;
    }

    public void setDebug(boolean debug) {
        if (debug) {
            log.info("Entrando no modo de DEBUG: " + debug);
        }
        SettingsManager.instance.debug = debug;
    }

    public boolean isRules() {
        return SettingsManager.instance.rules;
    }

    public void setRules(boolean rules) {
        log.info("Changing status of RULES: " + rules);
        SettingsManager.instance.rules = rules;
    }

    public String getSaveDir() {
        return SettingsManager.getInstance().getConfig("saveDir");
    }

    public String getSaveLogDir() {
        return SettingsManager.getInstance().getConfig("saveLogDir", getSaveDir());
    }

    public String getSaveStatsDir() {
        return SettingsManager.getInstance().getConfig("saveStatsDir");
    }

    public BundleManager getBundleManager() {
        //para forcar o ingles, por hora.
        if (SettingsManager.getInstance().locale == null) {
            SettingsManager.getInstance().locale = new Locale("en");
            Locale.setDefault(locale);
            //log.fatal("Definindo local default as EN");
        }
        //log.fatal(SettingsManager.getInstance().locale);
        if (SettingsManager.getInstance().bundleManager == null) {
            SettingsManager.getInstance().bundleManager = new BundleManager();
        }
        return SettingsManager.getInstance().bundleManager;
    }

    public void setLanguage(String lng) {
        if (lng.equalsIgnoreCase("pt")) {
            SettingsManager.getInstance().locale = new Locale("pt");
        } else if (lng.equalsIgnoreCase("it")) {
            SettingsManager.getInstance().locale = new Locale("it");
        } else if (lng.equalsIgnoreCase("es")) {
            SettingsManager.getInstance().locale = new Locale("es");
        } else {
            SettingsManager.getInstance().locale = new Locale("en");
        }

        Locale.setDefault(SettingsManager.getInstance().locale);
//        log.info("Novo local:" + SettingsManager.getInstance().locale);
    }

    public void setWorldBuilder(boolean worldBuilder) {
        this.worldBuilder = worldBuilder;
    }

    /**
     * @return the worldBuilder
     */
    public boolean isWorldBuilder() {
        return worldBuilder;
    }

    /**
     * @return the radialMenu
     */
    public boolean isRadialMenu() {
        return radialMenu;
    }

    /**
     * @param radialMenu the radialMenu to set
     */
    public void setRadialMenu(boolean radialMenu) {
        this.radialMenu = radialMenu;
    }

    /**
     * @return the portrait
     */
    public boolean isPortrait() {
        return portrait;
    }

    /**
     * @param portrait the portrait to set
     */
    public void setPortrait(boolean portrait) {
        this.portrait = portrait;
    }

    public boolean isTableColumnAdjust() {
        return tableColumnAdjust;
    }

    public void setTableColumnAdjust(boolean tableColumnAdjust) {
        this.tableColumnAdjust = tableColumnAdjust;
    }

    public Properties getNewGameProperties() {
        if (newGameProp == null) {
            //of not set, then read from file
            try {
                newGameProp = propertiesConfig.doLoadPropertiesFile("nations.config");
            } catch (IOException ex) {
                throw new UnsupportedOperationException("expecting a new game file = nations.config", ex);
            }
        }
        return newGameProp;
    }

    public void setNewGameProperties(Properties props) {
        newGameProp = props;
    }

    /*
     SysProperties encapsulation begins
     - SortedMap<String, String> listProp();
     - String GetProp(key) returns current value;
     - boolean SetProp(key,value) returns true if value is accepted;
     - boolean restoreProp() reloads from file;
     - boolean saveProps() saves to file;
     */
    public String getConfig(String key) {
        return propertiesConfig.getProps(key);
    }

    public int getConfigAsInt(String key) {
        return SysApoio.parseInt(propertiesConfig.getProps(key));
    }

    public int getConfigAsInt(String key, String defaultValue) {
        return SysApoio.parseInt(propertiesConfig.getProps(key, defaultValue));
    }

    public String getConfig(String key, String defaultValue) {
        return propertiesConfig.getProps(key, defaultValue);
    }

    public void setConfig(String key, String value) {
        propertiesConfig.setProp(key, value);
    }

    public void setConfigAndSaveToFile(String key, String value) {
        setConfig(key, value);
        saveToFile();
    }

    public void saveToFile() {
        propertiesConfig.doSavePropertiesToFile();
    }

    /**
     * Checks if the value stored in the key matches comparisonValue. If the key does not exists, then it compares defaultValue to comparisonValue instead. All
     * comparisons are case insensitive
     *
     * @param key
     * @param comparisonValue
     * @param defaultValue
     * @return
     */
    public boolean isConfig(String key, String comparisonValue, String defaultValue) {
        return getConfig(key, defaultValue).equalsIgnoreCase(comparisonValue);
    }

    /**
     * Checks if the key exists in the properties.
     *
     * @param key
     * @return
     */
    public boolean isKeyExist(String key) {
        return propertiesConfig.isSet(key);
    }

    public boolean doConfigRestore(String key) {
        return propertiesConfig.doConfigRestore();
    }

    public boolean doConfigSave(String key) {
        return propertiesConfig.doSavePropertiesToFile();
    }

    public Enumeration<?> listConfigs() {
        return propertiesConfig.listKeys();
    }

    /*
     SysProperties encapsulation ends
     */
}
