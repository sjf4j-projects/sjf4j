package org.sjf4j.schema;


import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.path.PathSegment;

/**
 * Boolean schema representation ({@code true}/{@code false}).
 * <p>
 * {@code true} accepts any instance; {@code false} rejects every instance.
 */
@NodeValue
public final class BooleanSchema implements JsonSchema {

    public static final BooleanSchema TRUE = new BooleanSchema(true);
    public static final BooleanSchema FALSE = new BooleanSchema(false);

    private final boolean booleanValue;
    private final PathSegment keywordPs;
    private BooleanSchema(boolean booleanValue) {
        this(booleanValue, null);
    }
    private BooleanSchema(boolean booleanValue, PathSegment keywordPs) {
        this.booleanValue = booleanValue;
        this.keywordPs = keywordPs;
    }

    /**
     * Encodes BooleanSchema to raw boolean.
     */
    @ValueToRaw
    public boolean booleanValue() {
        return booleanValue;
    }

    /**
     * Decodes raw boolean to shared BooleanSchema instance.
     */
    @RawToValue
    public static BooleanSchema of(boolean booleanValue) {
        return booleanValue ? TRUE : FALSE;
    }

    /**
     * Returns a boolean schema preserving keyword location when needed.
     * <p>
     * Shared singleton instances are reused whenever keyword location does not
     * matter. A dedicated {@code false} instance is created for non-root
     * keyword paths so diagnostics can point at the failing schema position.
     */
    static BooleanSchema of(boolean booleanValue, PathSegment keywordPs) {
        if (booleanValue || keywordPs == null || keywordPs == PathSegment.Root.INSTANCE) return of(booleanValue);
        return new BooleanSchema(false, keywordPs);
    }

    /**
     * Compiles this boolean schema into a trivial boolean-only plan.
     * <p>
     * External registry context is ignored because boolean schemas contain no
     * references or nested resources.
     */
    @Override
    public SchemaPlan createPlan(SchemaRegistry registry) {
        return SchemaPlan.of(PathSegment.Root.INSTANCE, this);
    }

}
