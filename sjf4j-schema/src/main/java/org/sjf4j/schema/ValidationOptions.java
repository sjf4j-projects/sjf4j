package org.sjf4j.schema;


/**
 * Options controlling schema validation behavior.
 * <p>
 * These options affect error collection strategy and keyword-level strictness,
 * but do not change the compiled schema dialect or vocabulary decisions made by
 * {@link SchemaPlanner}.
 */
public final class ValidationOptions {

    private final boolean failFast;
    private final boolean strictFormat;

    private ValidationOptions(Builder builder) {
        this.strictFormat = builder.strictFormats;
        this.failFast = builder.failFast;
    }

    /**
     * Returns true when {@code format} validators are enforced as assertions.
     * <p>
     * When false, format checks still remain assertions for schemas compiled with
     * the draft 2020-12 {@code format-assertion} vocabulary. The builder method
     * uses the plural name {@link Builder#strictFormats(boolean)} for historical
     * API consistency.
     */
    public boolean isStrictFormat() {return strictFormat;}
    /**
     * Returns true when validation stops at first non-ignored error.
     */
    public boolean isFailFast() {return failFast;}

    public static final ValidationOptions DEFAULT = new Builder().build();
    public static final ValidationOptions FAILFAST = new Builder().failFast(true).build();
    public static final ValidationOptions FAILFAST_STRICT = new Builder().failFast(true).strictFormats(true).build();


    // ---------------------------
    // Builder
    // ---------------------------
    public static class Builder {
        private boolean strictFormats = false;
        private boolean failFast = false;

        /**
         * Enables or disables caller-requested strict {@code format} assertions.
         */
        public Builder strictFormats(boolean strictFormats) {
            this.strictFormats = strictFormats;
            return this;
        }
        /**
         * Sets fail-fast behavior.
         * <p>
         * In fail-fast mode only the last error message is retained.
         */
        public Builder failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        /**
         * Builds immutable validation options.
         */
        public ValidationOptions build() {
            return new ValidationOptions(this);
        }
    }

}
