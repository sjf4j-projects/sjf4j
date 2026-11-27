package org.sjf4j.schema;

/** Immutable runtime validation options. Zero disables a resource limit. */
public final class ValidationOptions {
    private final boolean failFast;
    private final boolean strictFormat;
    private final boolean collectAnnotations;
    private final long maxEvaluations;
    private final int maxEvaluationDepth;
    private final int maxErrors;

    private ValidationOptions(Builder builder) {
        this.failFast = builder.failFast;
        this.strictFormat = builder.strictFormat;
        this.collectAnnotations = builder.collectAnnotations;
        this.maxEvaluations = builder.maxEvaluations;
        this.maxEvaluationDepth = builder.maxEvaluationDepth;
        this.maxErrors = builder.maxErrors;
    }

    /** Short-circuit subsequent checks after assertion failure. */
    public boolean isFailFast() {
        return failFast;
    }

    /** Enforce format assertions. */
    public boolean isStrictFormat() {
        return strictFormat;
    }

    /** Collect schema annotations. */
    public boolean isCollectAnnotations() {
        return collectAnnotations;
    }

    /** Maximum combined SchemaPlan and Evaluator invocations per evaluation; exhaustion aborts validation. */
    public long getMaxEvaluations() {
        return maxEvaluations;
    }

    /** Maximum active SchemaPlan evaluation frames, including the root; exhaustion aborts validation. */
    public int getMaxEvaluationDepth() {
        return maxEvaluationDepth;
    }

    /** Maximum retained errors; does not change validity. Terminal limit errors take priority. */
    public int getMaxErrors() {
        return maxErrors;
    }

    boolean hasLimits() {
        return maxEvaluations != 0 || maxEvaluationDepth != 0 || maxErrors != 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean failFast;
        private boolean strictFormat;
        private boolean collectAnnotations;
        private long maxEvaluations;
        private int maxEvaluationDepth;
        private int maxErrors;

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

        public Builder maxEvaluations(long maxEvaluations) {
            if (maxEvaluations < 0) throw new IllegalArgumentException("maxEvaluations must be non-negative");
            this.maxEvaluations = maxEvaluations;
            return this;
        }

        public Builder maxEvaluationDepth(int maxEvaluationDepth) {
            if (maxEvaluationDepth < 0) throw new IllegalArgumentException("maxEvaluationDepth must be non-negative");
            this.maxEvaluationDepth = maxEvaluationDepth;
            return this;
        }

        public Builder maxErrors(int maxErrors) {
            if (maxErrors < 0) throw new IllegalArgumentException("maxErrors must be non-negative");
            this.maxErrors = maxErrors;
            return this;
        }

        public ValidationOptions build() {
            return new ValidationOptions(this);
        }
    }
}
