package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;

import java.net.URI;

/**
 * JSON Schema annotation attached to an instance location.
 *
 * <p>Unlike a validation error, an annotation has no severity and carries the
 * keyword's original value (which may itself be a JSON object, array, or null).
 * Schema and instance locations identify its source in basic validation output.
 */
public final class ValidationAnnotation {
    private final String keyword;
    private final Object value;
    private final PathSegment instancePs;
    private final PathSegment keywordPs;
    private final URI schemaUri;

    public ValidationAnnotation(String keyword, Object value, PathSegment instancePs,
                                PathSegment keywordPs, URI schemaUri) {
        this.keyword = Asserts.notNull(keyword, "keyword");
        this.value = value;
        this.instancePs = Asserts.notNull(instancePs, "instancePs");
        this.keywordPs = Asserts.notNull(keywordPs, "keywordPs");
        this.schemaUri = schemaUri;
    }

    public String getKeyword() {
        return keyword;
    }

    /** Returns the original annotation value without a conversion to text. */
    public Object getValue() {
        return value;
    }

    public PathSegment getInstancePs() {
        return instancePs;
    }

    public PathSegment getKeywordPs() {
        return keywordPs;
    }

    public URI getSchemaUri() {
        return schemaUri;
    }
}
