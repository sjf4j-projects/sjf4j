package org.sjf4j.exception;

/**
 * Exception for JSON patch application errors.
 */
public class PatchException extends NodeException {

    public PatchException(String message) {
        super(message);
    }

    public PatchException(String message, Throwable cause) {
        super(message, cause);
    }

    public PatchException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public PatchException(Throwable cause) {
        super(cause);
    }

}
