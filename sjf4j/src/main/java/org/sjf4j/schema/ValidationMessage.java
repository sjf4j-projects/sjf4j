package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.Objects;

/**
 * Single validation message produced during schema evaluation.
 * <p>
 * Captures severity, message code, instance path, keyword path, schema
 * resource, source keyword, and detail. Public path accessors expose JSON
 * Pointer strings by default.
 */
public class ValidationMessage {

    /**
     * Message severity level.
     * <p>
     * ERROR contributes to invalid result; WARN/INFO/DEBUG are diagnostic only.
     */
    public enum Severity { ERROR, WARN, INFO, DEBUG }

    private final Severity severity;
    private final String code;
    private final PathSegment instancePs;
    private final PathSegment keywordPs;
    private final URI schemaUri;
    private final String keyword;
    private final String message;

    /**
     * Creates a validation message entry with instance and keyword paths.
     */
    public ValidationMessage(Severity severity, String code, PathSegment instancePs, PathSegment keywordPs,
                             URI schemaUri, String keyword, String message) {
        Objects.requireNonNull(severity, "severity");
        this.code = Objects.requireNonNull(code, "code");
        this.severity = severity;
        this.instancePs = instancePs;
        this.keywordPs = keywordPs;
        this.schemaUri = schemaUri;
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
     * Returns stable coarse-grained message code.
     */
    public String getCode() { return code; }
    /**
     * Returns the instance path segment chain.
     * <p>
     * May be {@code null} when the caller requested fail-fast validation and no
     * path materialization was needed yet.
     */
    public PathSegment getInstancePs() { return instancePs; }

    /**
     * Returns the keyword path segment chain.
     * <p>
     * May be {@code null} for ad-hoc messages that are not tied to a concrete
     * schema keyword location.
     */
    public PathSegment getKeywordPs() { return keywordPs; }

    /**
     * Returns the schema resource URI that owns {@link #getKeywordPs()}.
     */
    public URI getSchemaUri() { return schemaUri; }

    /**
     * Returns the schema resource URI as a display string.
     */
    public String getSchemaUriText() { return SchemaUtil.displaySchemaUri(schemaUri); }

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
     * <p>
     * This string form is intended for human-readable diagnostics rather than as
     * a stable machine-facing serialization format.
     * The default external form uses JSON Pointer strings and renders keyword
     * location inline before the failing instance location.
     */
    @Override
    public String toString() {
        return SchemaUtil.formatValidationLine(severity, code, message, instancePs, keywordPs, schemaUri, keyword);
    }

}
