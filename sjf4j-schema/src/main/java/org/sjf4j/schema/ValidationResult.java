package org.sjf4j.schema;

import org.sjf4j.Nodes;
import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * Validation outcome with separate assertion errors and schema annotations.
 *
 * <p>The two lists are independent. Results built from a reusable
 * {@link ValidationContext} refer to its live lists until the next explicit
 * clear; other results may own their lists.
 */
public class ValidationResult {
    private final boolean valid;
    private final List<Message> errors;
    private final List<Message> annotations;

    /** Shared success result without errors or annotations. */
    public static final ValidationResult SUCCESS = new ValidationResult(true, null, null);

    /** Creates a result containing assertion errors and optional annotations. */
    public ValidationResult(boolean valid, List<Message> errors, List<Message> annotations) {
        this.valid = valid;
        this.errors = errors;
        this.annotations = annotations;
    }

    /** Creates a result without annotations. */
    public ValidationResult(boolean valid, List<Message> errors) {
        this(valid, errors, null);
    }

    public boolean isValid() {
        return valid;
    }

    /** Number of assertion errors, excluding annotations. */
    public int count() {
        return errors == null ? 0 : errors.size();
    }

    public List<Message> getErrors() {
        return errors == null ? Collections.emptyList() : errors;
    }

    public Message getLastError() {
        return errors == null || errors.isEmpty() ? null : errors.get(errors.size() - 1);
    }

    public List<Message> getAnnotations() {
        return annotations == null ? Collections.emptyList() : annotations;
    }

    @Override
    public String toString() {
        return Nodes.inspect(getErrors());
    }

    /**
     * One validation error or annotation with its associated locations.
     * Membership in the errors or annotations list determines its meaning.
     */
    public static final class Message {
        private final String keyword;
        private final String message;
        private final Object value;
        private final PathSegment instancePs;
        private final PathSegment keywordPs;
        private final URI schemaUri;

        public Message(String keyword, String message, Object value, PathSegment instancePs,
                       PathSegment keywordPs, URI schemaUri) {
            this.keyword = keyword;
            this.message = message;
            this.value = value;
            this.instancePs = instancePs;
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
        }

        public String getKeyword() { return keyword; }
        public String getMessage() { return message; }
        public Object getValue() { return value; }
        public PathSegment getInstancePs() { return instancePs; }
        public PathSegment getKeywordPs() { return keywordPs; }
        public URI getSchemaUri() { return schemaUri; }
        public String getSchemaUriText() { return SchemaUtil.displaySchemaUri(schemaUri); }

        @Override
        public String toString() {
            return SchemaUtil.formatValidationMessage(
                    message != null ? message : String.valueOf(value),
                    instancePs, keywordPs, schemaUri, keyword);
        }
    }
}
