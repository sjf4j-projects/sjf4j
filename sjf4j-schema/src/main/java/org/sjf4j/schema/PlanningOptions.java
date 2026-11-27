package org.sjf4j.schema;

/** Immutable schema planning options. Zero disables a resource limit. */
public final class PlanningOptions {
    public static final PlanningOptions DEFAULT = builder().build();

    private final int maxSchemas;
    private final int maxSchemaDepth;

    private PlanningOptions(Builder builder) {
        this.maxSchemas = builder.maxSchemas;
        this.maxSchemaDepth = builder.maxSchemaDepth;
    }

    /** Maximum newly constructed SchemaPlans per operation, including boolean and empty plans. Cached plans do not count again. */
    public int getMaxSchemas() {
        return maxSchemas;
    }

    /** Maximum number of active schema construction frames, including the root. */
    public int getMaxSchemaDepth() {
        return maxSchemaDepth;
    }

    boolean hasLimits() {
        return maxSchemas != 0 || maxSchemaDepth != 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxSchemas;
        private int maxSchemaDepth;

        private Builder() {
        }

        public Builder maxSchemas(int maxSchemas) {
            if (maxSchemas < 0) throw new IllegalArgumentException("maxSchemas must be non-negative");
            this.maxSchemas = maxSchemas;
            return this;
        }

        public Builder maxSchemaDepth(int maxSchemaDepth) {
            if (maxSchemaDepth < 0) throw new IllegalArgumentException("maxSchemaDepth must be non-negative");
            this.maxSchemaDepth = maxSchemaDepth;
            return this;
        }

        public PlanningOptions build() {
            return new PlanningOptions(this);
        }
    }
}
