package com.ess.jloader.packer;

import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class InvalidClassException extends RuntimeException {

    public InvalidClassException() {
    }

    public InvalidClassException(String message) {
        super(message);
    }
}
