package org.sjf4j.exception;

/**
 * Exception for JSON path parsing, evaluation, and mutation errors.
 */
public class PathException extends JsonException {

    public PathException(String message) {
        super(message);
    }

    public PathException(String message, Throwable cause) {
        super(message, cause);
    }

    public PathException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public PathException(Throwable cause) {
        super(cause);
    }

}
