package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;
import org.sjf4j.path.Paths;

import java.util.Objects;

/**
 * Single validation message produced during schema evaluation.
 * <p>
 * Captures severity, instance path, keyword path, source keyword, and detail.
 * Public path accessors expose JSON Pointer strings by default.
 */
public class ValidationMessage {

    /**
     * Message severity level.
     * <p>
     * ERROR contributes to invalid result; WARN/INFO/DEBUG are diagnostic only.
     */
    public enum Severity { ERROR, WARN, INFO, DEBUG }

    private final Severity severity;
    private final PathSegment instancePs;
    private final PathSegment keywordPs;
    private final String keyword;
    private final String message;

    /**
     * Creates a validation message entry.
     */
    public ValidationMessage(Severity severity, PathSegment instancePs, String keyword, String message) {
        this(severity, instancePs, null, keyword, message);
    }

    /**
     * Creates a validation message entry with instance and keyword paths.
     */
    public ValidationMessage(Severity severity, PathSegment instancePs, PathSegment keywordPs,
                             String keyword, String message) {
        Objects.requireNonNull(severity, "severity");
        this.severity = severity;
        this.instancePs = instancePs;
        this.keywordPs = keywordPs;
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
     * Returns the instance path segment chain.
     */
    public PathSegment getInstancePs() { return instancePs; }

    /**
     * Returns the keyword path segment chain.
     */
    public PathSegment getKeywordPs() { return keywordPs; }

    /**
     * Returns the instance path as a JSON Pointer string.
     */
    public String getInstancePath() { return Paths.rootedPointerExpr(instancePs); }

    /**
     * Returns the keyword path as a JSON Pointer string.
     */
    public String getKeywordPath() { return Paths.rootedPointerExpr(keywordPs); }

    /**
     * @deprecated Use {@link #getInstancePs()} or {@link #getInstancePath()}.
     */
    @Deprecated
    public PathSegment getPs() { return instancePs; }
    /**
     * Returns the schema keyword that produced this message.
     * Keyword path pinpoints where this keyword lives in the schema.
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
     * The default external form uses JSON Pointer strings and renders keyword
     * location inline before the failing instance location.
     */
    @Override
    public String toString() {
        String keywordPath = getKeywordPath();
        return "[" + severity + "] Keyword '" + keyword + "'" +
                (keywordPs == null ? "" : " (" + keywordPath + ")") +
                " failed at instance '" + getInstancePath() + "': " + message;
    }

}
