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
 * @author GM Team
 */
public class BundleManager implements Serializable {

    private static final Log log = LogFactory.getLog(BundleManager.class);
    private ResourceBundle bundleExtra;
    private String bundleExtraName; // remembered so the extra bundle can be resolved per-locale (email localization)

    public BundleManager() {
    }

    private ResourceBundle getBundleExtra() {
        return bundleExtra;
    }

    private boolean isBundleExtra() {
        return this.bundleExtra != null;
    }

    public void setBundleExtra(String bundleName) {
        this.bundleExtraName = bundleName;
        this.bundleExtra = java.util.ResourceBundle.getBundle(bundleName);
    }

    /**
     * Resolve a key from the "extra" bundle (e.g. messagesmailsender) for a SPECIFIC locale, so player
     * emails can be composed in each recipient's language rather than the one JVM-default bundle. Uses a
     * no-fallback control so an absent/'en' translation resolves to the base bundle (never to the server's
     * JVM-default locale, which the standard fallback would pick). Any miss falls back to getString(label).
     */
    public String getString(String label, Locale locale, boolean useExtra) {
        if (useExtra && bundleExtraName != null && locale != null) {
            try {
                return ResourceBundle.getBundle(bundleExtraName, locale,
                        ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES))
                        .getString(label);
            } catch (MissingResourceException e) {
                // fall through to the default resolution below
            }
        }
        return getString(label);
    }

    /** Locale-aware MessageFormat of an extra-bundle key (apostrophes auto-escaped, as in format()). */
    public String format(String base, Object[] placeholders, Locale locale) {
        return MessageFormat.format(getString(base, locale, true).replaceAll("'", "''"), placeholders);
    }

    public String formatNl(String base, Object[] placeholders, Locale locale) {
        return this.format(base, placeholders, locale) + "\n";
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
