package eu.h2020.helios_social.core.messaging;

import java.security.GeneralSecurityException;

/**
 * This is an exception class that is used to encapsulate exceptions that take place
 * in HELIOS Messaging implementation.
 */
public class HeliosMessagingException extends GeneralSecurityException {
    private String msg;

    HeliosMessagingException(String msg, Exception e) {
        this.msg = msg;
        if (e != null) {
            e.printStackTrace();
        }
    }
}

