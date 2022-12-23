/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package persistenceCommons;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author jmoura
 */
public class BundleManager implements Serializable {

    private static final Log log = LogFactory.getLog(BundleManager.class);
    private ResourceBundle bundleExtra;

    public BundleManager() {
    }

    private ResourceBundle getBundleExtra() {
        return bundleExtra;
    }

    private boolean isBundleExtra() {
        return this.bundleExtra != null;
    }

    public void setBundleExtra(String bundleName) {
        this.bundleExtra = java.util.ResourceBundle.getBundle(bundleName);
    }

    public String getStringNl(String label) {
        return getString(label) + "\n";
    }

    public String getString(String label) {
        if (isBundleExtra()) {
            try {
                return this.bundleExtra.getString(label);
            } catch (MissingResourceException e) {
            }
        }
        try {
            return ResourceBundle.getBundle("labels").getString(label);
        } catch (MissingResourceException e) {
        }
        try {
            return ResourceBundle.getBundle("mensagens").getString(label);
        } catch (MissingResourceException ex) {
        }
        try {
            return ResourceBundle.getBundle("rulesandhelpgot").getString(label);
        } catch (MissingResourceException ex) {
        }
        try {
            return ResourceBundle.getBundle("rulesandhelpothers").getString(label);
        } catch (MissingResourceException ex) {
        }
        //YYY: to deactivate the fatal stop when a string is not found
//                throw new UnsupportedOperationException("Missing string: " + label);
        log.fatal("Missing string Translation: " + label);
        return String.format("N/A (Missing Translation: %s)", label);
    }

    public String getString(String label, Locale locale) {
        try {
            return ResourceBundle.getBundle("labels", locale).getString(label);
        } catch (MissingResourceException e) {
        }
        try {
            return ResourceBundle.getBundle("mensagens", locale).getString(label);
        } catch (MissingResourceException ex) {
        }
        try {
            return ResourceBundle.getBundle("rulesandhelpgot", locale).getString(label);
        } catch (MissingResourceException ex) {
        }
        try {
            return ResourceBundle.getBundle("rulesandhelpothers", locale).getString(label);
        } catch (MissingResourceException ex) {
        }
        //YYY: to deactivate the fatal stop when a string is not found
//                throw new UnsupportedOperationException("Missing string: " + label);
        log.fatal("Missing Translation string: " + label);
        return String.format("N/A (Missing Translation: %s)", label);
    }

    public String format(String base, Object[] placeholders) {
        return MessageFormat.format(getString(base).replaceAll("'", "''"), placeholders);
    }

    public String format(String base, String placeholder) {
        return MessageFormat.format(getString(base).replaceAll("'", "''"), placeholder);
    }

    public String formatNl(String base, Object[] placeholders) {
        return this.format(base, placeholders) + "\n";
    }

    public String formatNl(String base, String placeholder) {
        return this.format(base, placeholder) + "\n";
    }

}
