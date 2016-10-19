/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package persistenceCommons;

import java.io.Serializable;
import java.util.Locale;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
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
    private BundleManager bundleManager;
    private boolean worldBuilder = false;
    private boolean portrait = false;
    private boolean tableColumnAdjust = true;
    private boolean radialMenu = false;
    private Properties newGameProp;
    private final SortedMap<String, String> properties;

    private SettingsManager() {
        this.properties = new TreeMap<String, String>();
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

    public boolean isDebug() {
        return SettingsManager.instance.debug;
    }

    public void setDebug(boolean debug) {
        if (debug) {
            log.info("Entrando no modo de DEBUG: " + debug);
        }
        SettingsManager.instance.debug = debug;
    }

    public String getSaveDir() {
        if (SettingsManager.instance.debug) {
            return SysProperties.getProps("saveDirDebug");
        } else {
            return SysProperties.getProps("saveDir");
        }
    }

    public BundleManager getBundleManager() {
        //para forcar o portugues, por hora.
        if (SettingsManager.getInstance().locale == null) {
            SettingsManager.getInstance().locale = new Locale("en");
            Locale.setDefault(locale);
            //log.fatal("Definindo local default as PT");
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
            newGameProp = SysProperties.getInstance().loadPropertiesFile("nations.config");
        }
        return newGameProp;
    }

    public String getProperties(String key, String defaultValue) {
        if (properties.containsKey(key)) {
            return properties.get(key);
        } else {
            return SysProperties.getProps(key, defaultValue);
        }
    }

    public String getProperties(String key) {
        if (properties.containsKey(key)) {
            return properties.get(key);
        } else {
            return SysProperties.getProps(key);
        }
    }

    public boolean isProperties(String key, String comparisonValue, String defaultValue) {
        return getProperties(key, defaultValue).equalsIgnoreCase(comparisonValue);
    }
    public boolean isProperties(String key) {
        return getProperties(key, "-").equalsIgnoreCase("-");
    }
}
