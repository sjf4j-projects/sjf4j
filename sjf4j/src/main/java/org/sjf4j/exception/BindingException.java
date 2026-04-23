package org.sjf4j.exception;

import org.sjf4j.path.PathSegment;
import org.sjf4j.path.Paths;

/**
 * Exception for binding/streaming errors with optional path context.
 */
public class BindingException extends JsonException {

    private final PathSegment ps;

    /**
     * Creates a binding exception without path context.
     */
    public BindingException(String message) {
        super(message);
        this.ps = null;
    }

    /**
     * Creates a binding exception without path context and cause.
     */
    public BindingException(String message, Throwable cause) {
        super(message, cause, false, false);
        this.ps = null;
    }

    /**
     * Creates a binding exception from cause without path context.
     */
    public BindingException(Throwable cause) {
        super(cause);
        this.ps = null;
    }

    /**
     * Creates a binding exception with path context.
     */
    public BindingException(String message, PathSegment ps) {
        super(message);
        this.ps = ps;
    }

    /**
     * Creates a binding exception with path context and cause.
     */
    public BindingException(String message, PathSegment ps, Throwable cause) {
        super(message, cause, false, false);
        this.ps = ps;
    }

    /**
     * Creates a binding exception from cause with path context.
     */
    public BindingException(Throwable cause, PathSegment ps) {
        super(cause);
        this.ps = ps;
    }

    /**
     * Returns message text with path suffix when available.
     */
    @Override
    public String getMessage() {
        if (ps == null) return super.getMessage();
        return super.getMessage() + ", at path '" + Paths.rootedPathExpr(ps) + "'";
    }

    /**
     * Returns the bound path segment.
     */
    public PathSegment getPathSegment() {
        return ps;
    }

    /**
     * Returns true when path context is present.
     */
    public boolean hasPathSegment() {
        return ps != null;
    }

}
