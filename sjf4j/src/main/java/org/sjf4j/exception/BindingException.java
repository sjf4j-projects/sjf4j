package org.sjf4j.exception;

import org.sjf4j.path.PathSegment;
import org.sjf4j.path.Paths;

public class BindingException extends JsonException {

    private final PathSegment ps;

    public BindingException(String message, PathSegment ps) {
        super(message);
        this.ps = ps;
    }

    public BindingException(String message, PathSegment ps, Throwable cause) {
        super(message, cause, false, false);
        this.ps = ps;
    }

    public BindingException(Throwable cause, PathSegment ps) {
        super(cause);
        this.ps = ps;
    }

    @Override
    public String getMessage() {
        if (ps == null) return super.getMessage();
        return super.getMessage() + ", at path '" + Paths.inspectRooted(ps) + "'";
    }

    public PathSegment getPathSegment() {
        return ps;
    }

    public boolean hasPathSegment() {
        return ps != null;
    }

}
