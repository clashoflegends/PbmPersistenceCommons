/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package persistenceCommons.mail;

import java.io.Serializable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Walter
 */
public class SmtpException extends Exception implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = -7627358693363287945L;
    private static Log log = LogFactory.getLog(SmtpException.class);

    public SmtpException() {
    }

    public SmtpException(Throwable cause) {
        super("SMTP layer error.", cause);
        log.error("Erro na camada de SMTP Persistencia", cause);
    }

    public SmtpException(String message, Throwable cause) {
        super(message, cause);
        log.error(message, cause);
    }

    public SmtpException(String message) {
        super(message);
        log.error(message);
    }
}
