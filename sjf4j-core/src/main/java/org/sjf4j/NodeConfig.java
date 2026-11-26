package org.sjf4j;

import org.sjf4j.util.Types;
import org.sjf4j.util.Asserts;

import java.util.Map;

/**
 * Immutable node configuration assembled by {@link Sjf4j.Builder} or supplied
 * directly to node binding and conversion. Default value formats are matched by exact
 * non-primitive class, rather than by assignability.
 */
public final class NodeConfig {
    public final boolean includeNulls;
    /** Preserve decimal/exponent literals as BigDecimal for generic numeric reads. */
    public final boolean exactNumbers;
    private final Class<?>[] valueFormatTypes;
    private final String[] valueFormats;

    private static final Class<?>[] EMPTY_VALUE_TYPES = new Class<?>[0];
    private static final String[] EMPTY_VALUE_FORMATS = new String[0];

    /** Settings with no default value formats and null-valued members included. */
    public static final NodeConfig DEFAULT = new NodeConfig(true);

    /** Default value formats with null-valued object members omitted. */
    public static final NodeConfig OMIT_NULLS = new NodeConfig(false);

    /** Settings for exact generic numeric reads with null-valued members included. */
    public static final NodeConfig EXACT_NUMBERS = new NodeConfig(true, true);


    /**
     * Creates settings without default value formats.
     *
     * @param includeNulls whether serialization includes null-valued object members
     */
    public NodeConfig(boolean includeNulls) {
        this(includeNulls, false);
    }

    /**
     * Creates settings without default value formats.
     * @param includeNulls whether serialization includes null-valued object members
     * @param exactNumbers whether generic numeric reads preserve decimal precision
     */
    public NodeConfig(boolean includeNulls, boolean exactNumbers) {
        this.valueFormatTypes = EMPTY_VALUE_TYPES;
        this.valueFormats = EMPTY_VALUE_FORMATS;
        this.includeNulls = includeNulls;
        this.exactNumbers = exactNumbers;
    }

    /**
     * Creates settings with default formats and null-valued members included.
     * The supplied map is copied, not retained.
     */
    public NodeConfig(Map<Class<?>, String> valueFormats) {
        this(valueFormats, true);
    }

    /**
     * Creates settings with a copied map of default formats.
     * Primitive types must be registered using their boxed classes.
     *
     * @throws IllegalArgumentException if a default format uses a primitive type
     */
    public NodeConfig(Map<Class<?>, String> valueFormats, boolean includeNulls) {
        this(valueFormats, includeNulls, false);
    }

    /**
     * Creates settings with copied default formats and explicit read/write policies.
     * @param valueFormats formats keyed by exact boxed value type
     * @param includeNulls whether serialization includes null-valued object members
     * @param exactNumbers whether generic numeric reads preserve decimal precision
     */
    public NodeConfig(Map<Class<?>, String> valueFormats, boolean includeNulls, boolean exactNumbers) {
        Asserts.notNull(valueFormats, "valueFormats");
        if (valueFormats.isEmpty()) {
            this.valueFormatTypes = EMPTY_VALUE_TYPES;
            this.valueFormats = EMPTY_VALUE_FORMATS;
        } else {
            this.valueFormatTypes = new Class<?>[valueFormats.size()];
            this.valueFormats = new String[valueFormats.size()];
            int i = 0;
            for (Map.Entry<Class<?>, String> entry : valueFormats.entrySet()) {
                Class<?> valueType = Asserts.notNull(entry.getKey(), "valueType");
                if (valueType.isPrimitive()) {
                    throw new IllegalArgumentException("valueFormat does not support primitive type '"
                            + valueType.getName() + "'; use boxed type '" + Types.box(valueType).getName() + "'");
                }
                valueFormatTypes[i] = valueType;
                this.valueFormats[i] = Asserts.notNull(entry.getValue(), "valueFormat");
                i++;
            }
        }
        this.includeNulls = includeNulls;
        this.exactNumbers = exactNumbers;
    }

    /**
     * Returns the named format for the exact value class, or {@code null} if
     * none is configured (including when {@code valueType} is null).
     */
    public String valueFormat(Class<?> valueType) {
        if (valueType == null) return null;
        for (int i = 0; i < valueFormatTypes.length; i++) {
            if (valueFormatTypes[i] == valueType) {
                return valueFormats[i];
            }
        }
        return null;
    }

    /**
     * Copies default value formats into the target map, overwriting matching keys.
     */
    public void copyValueFormatsTo(Map<Class<?>, String> target) {
        Asserts.notNull(target, "target");
        for (int i = 0; i < valueFormatTypes.length; i++) {
            target.put(valueFormatTypes[i], valueFormats[i]);
        }
    }


}
