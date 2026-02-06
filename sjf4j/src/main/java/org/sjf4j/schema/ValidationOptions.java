package org.sjf4j.schema;


public final class ValidationOptions {

    private final boolean failFast;
    private final boolean strictFormat;

    private ValidationOptions(Builder builder) {
        this.strictFormat = builder.strictFormats;
        this.failFast = builder.failFast;
    }

    public boolean isStrictFormat() {return strictFormat;}
    public boolean isFailFast() {return failFast;}

    public static final ValidationOptions DEFAULT = new Builder().build();
    public static final ValidationOptions FAIL_FAST = new Builder().failFast(true).build();


    // ---------------------------
    // Builder
    // ---------------------------
    public static class Builder {
        private boolean strictFormats = false;
        private boolean failFast = false;

        public Builder strictFormats(boolean strictFormats) {
            this.strictFormats = strictFormats;
            return this;
        }
        public Builder failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        public ValidationOptions build() {
            return new ValidationOptions(this);
        }
    }

}
