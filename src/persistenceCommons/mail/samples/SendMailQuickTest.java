/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package persistenceCommons.mail.samples;

/**
 *
 * @author John
 */
import java.io.Serializable;

public class SendMailQuickTest implements Serializable {

    public static void main(String[] args) {
        SendMailSimple msg = new SendMailSimple();
        msg.send();
    }

}
