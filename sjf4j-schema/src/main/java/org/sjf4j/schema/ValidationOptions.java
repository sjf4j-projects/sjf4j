package org.sjf4j.schema;

/**
 * Immutable options for runtime JSON Schema validation.
 *
 * <p>Annotation collection is an API placeholder and is not implemented yet.
 * Requesting it currently throws {@link UnsupportedOperationException}.
 */
public final class ValidationOptions {
    private final boolean failFast;
    private final boolean strictFormat;
    private final boolean collectAnnotations;

    private ValidationOptions(Builder builder) {
        this.failFast = builder.failFast;
        this.strictFormat = builder.strictFormat;
        this.collectAnnotations = builder.collectAnnotations;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public boolean isStrictFormat() {
        return strictFormat;
    }

    public boolean isCollectAnnotations() {
        return collectAnnotations;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean failFast;
        private boolean strictFormat;
        private boolean collectAnnotations;

        private Builder() {
        }

        public Builder failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        public Builder strictFormat(boolean strictFormat) {
            this.strictFormat = strictFormat;
            return this;
        }

        public Builder collectAnnotations(boolean collectAnnotations) {
            this.collectAnnotations = collectAnnotations;
            return this;
        }

        public ValidationOptions build() {
            return new ValidationOptions(this);
        }
    }
}
