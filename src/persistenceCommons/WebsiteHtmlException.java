package persistenceCommons;

/** Unexpected HTML from an endpoint requiring a token/hash string; contains only sanitized text. */
public final class WebsiteHtmlException extends PersistenceException {
    private static final long serialVersionUID = 1L;
    private final String websiteMessage;
    private final int statusCode;

    WebsiteHtmlException(int statusCode, String sanitizedMessage) {
        super("Unexpected website HTML (HTTP " + statusCode + "): "
                + (sanitizedMessage.isEmpty() ? "No readable message." : sanitizedMessage));
        this.websiteMessage = sanitizedMessage;
        this.statusCode = statusCode;
    }

    public String getWebsiteMessage() { return websiteMessage; }
    public int getStatusCode() { return statusCode; }
}
