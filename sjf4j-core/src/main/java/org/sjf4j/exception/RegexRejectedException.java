package org.sjf4j.exception;

/**
 * Indicates that a regular expression was rejected by a security policy or
 * that matching exceeded a safety limit.
 */
public class RegexRejectedException extends NodeException {

    public RegexRejectedException(String message) {
        super(message);
    }

    public RegexRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
