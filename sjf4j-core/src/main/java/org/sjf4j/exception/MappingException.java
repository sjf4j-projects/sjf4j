package org.sjf4j.exception;

/**
 * Exception for mapper declaration, generation, and execution errors.
 */
public class MappingException extends JsonException {

    public MappingException(String message) {
        super(message);
    }

    public MappingException(String message, Throwable cause) {
        super(message, cause);
    }

    public MappingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public MappingException(Throwable cause) {
        super(cause);
    }

}
