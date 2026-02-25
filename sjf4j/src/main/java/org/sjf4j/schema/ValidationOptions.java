package org.sjf4j.schema;


/**
 * Options controlling schema validation behavior.
 */
public final class ValidationOptions {

    private final boolean failFast;
    private final boolean strictFormat;

    private ValidationOptions(Builder builder) {
        this.strictFormat = builder.strictFormats;
        this.failFast = builder.failFast;
    }

    /**
     * Returns true when format validators run in strict mode.
     */
    public boolean isStrictFormat() {return strictFormat;}
    /**
     * Returns true when validation stops at first error.
     */
    public boolean isFailFast() {return failFast;}

    public static final ValidationOptions DEFAULT = new Builder().build();
    public static final ValidationOptions FAIL_FAST = new Builder().failFast(true).build();


    // ---------------------------
    // Builder
    // ---------------------------
    public static class Builder {
        private boolean strictFormats = false;
        private boolean failFast = false;

        /**
         * Sets strict format validation.
         */
        public Builder strictFormats(boolean strictFormats) {
            this.strictFormats = strictFormats;
            return this;
        }
        /**
         * Sets fail-fast behavior.
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
