package org.sjf4j.schema;

import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;
import org.sjf4j.path.Paths;

import java.util.Objects;

public class ValidationMessage {

    public enum Severity { ERROR, WARN, INFO, DEBUG }

    private final Severity severity;
    private final PathSegment ps;
    private final String keyword;
    private final String message;

    public ValidationMessage(Severity severity, PathSegment ps, String keyword, String message) {
        Objects.requireNonNull(severity, "Severity is null");
        this.severity = severity;
        this.ps = ps;
        this.keyword = keyword;
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }
    public PathSegment getPs() { return ps; }
//    public JsonPointer getPath() {
//        return JsonPointer.fromLast(ps);
//    }
    public String getKeyword() {
        return keyword;
    }
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "[" + severity + "] Keyword '" + keyword + "' failed at path '" + Paths.rootedPointerExpr(ps) +
                "': " + message;
    }

}
