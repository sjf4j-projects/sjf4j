package org.sjf4j.schema;

import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;
import org.sjf4j.path.Paths;

import java.util.Objects;

/**
 * Single validation message produced during schema evaluation.
 * <p>
 * Captures severity, instance path, source keyword, and human-readable detail.
 */
public class ValidationMessage {

    /**
     * Message severity level.
     * <p>
     * ERROR contributes to invalid result; WARN/INFO/DEBUG are diagnostic only.
     */
    public enum Severity { ERROR, WARN, INFO, DEBUG }

    private final Severity severity;
    private final PathSegment ps;
    private final String keyword;
    private final String message;

    /**
     * Creates a validation message entry.
     */
    public ValidationMessage(Severity severity, PathSegment ps, String keyword, String message) {
        Objects.requireNonNull(severity, "severity");
        this.severity = severity;
        this.ps = ps;
        this.keyword = keyword;
        this.message = message;
    }

    /**
     * Returns message severity.
     */
    public Severity getSeverity() {
        return severity;
    }
    /**
     * Returns path segment where the message was emitted.
     * <p>
     * Can be {@code null} in fail-fast mode when root path tracking is skipped.
     */
    public PathSegment getPs() { return ps; }
//    public JsonPointer getPath() {
//        return JsonPointer.fromLast(ps);
//    }
    /**
     * Returns the schema keyword that produced this message.
     */
    public String getKeyword() {
        return keyword;
    }
    /**
     * Returns message text.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Formats message for logs and diagnostics.
     */
    @Override
    public String toString() {
        return "[" + severity + "] Keyword '" + keyword + "' failed at path '" + Paths.rootedPointerExpr(ps) +
                "': " + message;
    }

}
