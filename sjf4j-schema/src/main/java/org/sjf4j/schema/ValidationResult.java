package org.sjf4j.schema;

import org.sjf4j.Nodes;
import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;

import java.net.URI;
import java.util.ArrayList;
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
    private final List<Error> errors;
    private final List<Annotation> annotations;

    /** Shared success result without errors or annotations. */
    public static final ValidationResult SUCCESS = new ValidationResult(true, null, null);

    /** Creates a result containing assertion errors and optional annotations. */
    public ValidationResult(boolean valid, List<Error> errors, List<Annotation> annotations) {
        this.valid = valid;
        this.errors = errors;
        this.annotations = annotations;
    }

    /** Creates a result without annotations. */
    public ValidationResult(boolean valid, List<Error> errors) {
        this(valid, errors, null);
    }

    public boolean isValid() {
        return valid;
    }

    /** Number of assertion errors, excluding annotations. */
    public int count() {
        return errors == null ? 0 : errors.size();
    }

    public List<Error> getErrors() {
        return errors == null ? Collections.emptyList() : errors;
    }

    public Error getLastError() {
        return errors == null || errors.isEmpty() ? null : errors.get(errors.size() - 1);
    }

    public List<Annotation> getAnnotations() {
        return annotations == null ? Collections.emptyList() : annotations;
    }

    /**
     * Returns a combined result in parent-to-child order without mutating
     * either input or the shared SUCCESS singleton.
     */
    public ValidationResult mergePrevious(ValidationResult previous) {
        if (previous == null || (previous.count() == 0 && previous.getAnnotations().isEmpty())) return this;
        List<Error> combinedErrors = new ArrayList<>(previous.getErrors());
        combinedErrors.addAll(getErrors());
        List<Annotation> combinedAnnotations = null;
        if (!previous.getAnnotations().isEmpty() || !getAnnotations().isEmpty()) {
            combinedAnnotations = new ArrayList<>(previous.getAnnotations());
            combinedAnnotations.addAll(getAnnotations());
        }
        return new ValidationResult(valid && previous.valid, combinedErrors, combinedAnnotations);
    }

    @Override
    public String toString() {
        return Nodes.inspect(getErrors());
    }

    /** An assertion failure and its locations in the instance and schema. */
    public static final class Error {
        private final String keyword;
        private final String message;
        private final PathSegment instancePs;
        private final PathSegment keywordPs;
        private final URI schemaUri;

        public Error(String keyword, String message, PathSegment instancePs,
                     PathSegment keywordPs, URI schemaUri) {
            this.keyword = keyword;
            this.message = message;
            this.instancePs = instancePs;
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
        }

        public String getKeyword() { return keyword; }
        public String getMessage() { return message; }
        public PathSegment getInstancePs() { return instancePs; }
        public PathSegment getKeywordPs() { return keywordPs; }
        public URI getSchemaUri() { return schemaUri; }
        public String getSchemaUriText() { return SchemaUtil.displaySchemaUri(schemaUri); }

        @Override
        public String toString() {
            return SchemaUtil.formatValidationError(message, instancePs, keywordPs, schemaUri, keyword);
        }
    }

    /** An annotation value and its locations in the instance and schema. */
    public static final class Annotation {
        private final String keyword;
        private final Object value;
        private final PathSegment instancePs;
        private final PathSegment keywordPs;
        private final URI schemaUri;

        public Annotation(String keyword, Object value, PathSegment instancePs,
                          PathSegment keywordPs, URI schemaUri) {
            this.keyword = Asserts.notNull(keyword, "keyword");
            this.value = value;
            this.instancePs = Asserts.notNull(instancePs, "instancePs");
            this.keywordPs = Asserts.notNull(keywordPs, "keywordPs");
            this.schemaUri = schemaUri;
        }

        public String getKeyword() { return keyword; }
        public Object getValue() { return value; }
        public PathSegment getInstancePs() { return instancePs; }
        public PathSegment getKeywordPs() { return keywordPs; }
        public URI getSchemaUri() { return schemaUri; }
        public String getSchemaUriText() { return SchemaUtil.displaySchemaUri(schemaUri); }
    }
}
