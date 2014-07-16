package com.ess.jloader.packer;

import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class InvalidJarException extends RuntimeException {

    public InvalidJarException() {
    }

    public InvalidJarException(String message) {
        super(message);
    }

    public InvalidJarException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidJarException(Throwable cause) {
        super(cause);
    }
}
