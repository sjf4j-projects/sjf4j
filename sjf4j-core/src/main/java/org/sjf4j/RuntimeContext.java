package org.sjf4j;

import org.sjf4j.util.Types;
import org.sjf4j.util.Asserts;

import java.util.Map;

/**
 * Immutable runtime settings assembled by {@link Sjf4j.Builder} or supplied
 * directly to node conversion. Default value formats are matched by exact
 * non-primitive class, rather than by assignability.
 */
public final class RuntimeContext {
    public final boolean includeNulls;
    private final Class<?>[] valueFormatTypes;
    private final String[] valueFormats;

    // Empty
    private static final Class<?>[] EMPTY_VALUE_TYPES = new Class<?>[0];
    private static final String[] EMPTY_VALUE_FORMATS = new String[0];

    /** Settings with no default value formats and null-valued properties included. */
    public static final RuntimeContext EMPTY = new RuntimeContext(true);


    /**
     * Creates settings without default value formats.
     *
     * @param includeNulls whether serialization includes null-valued object properties
     */
    public RuntimeContext(boolean includeNulls) {
        this.valueFormatTypes = EMPTY_VALUE_TYPES;
        this.valueFormats = EMPTY_VALUE_FORMATS;
        this.includeNulls = includeNulls;
    }

    /**
     * Creates settings with default formats and null-valued properties included.
     * The supplied map is copied, not retained.
     */
    public RuntimeContext(Map<Class<?>, String> defaultValueFormats) {
        this(defaultValueFormats, true);
    }

    /**
     * Creates settings with a copied map of default formats.
     * Primitive types must be registered using their boxed classes.
     *
     * @throws IllegalArgumentException if a default format uses a primitive type
     */
    public RuntimeContext(Map<Class<?>, String> defaultValueFormats, boolean includeNulls) {
        Asserts.notNull(defaultValueFormats, "defaultValueFormats");
        if (defaultValueFormats.isEmpty()) {
            this.valueFormatTypes = EMPTY_VALUE_TYPES;
            this.valueFormats = EMPTY_VALUE_FORMATS;
        } else {
            this.valueFormatTypes = new Class<?>[defaultValueFormats.size()];
            this.valueFormats = new String[defaultValueFormats.size()];
            int i = 0;
            for (Map.Entry<Class<?>, String> entry : defaultValueFormats.entrySet()) {
                Class<?> valueType = Asserts.notNull(entry.getKey(), "valueType");
                if (valueType.isPrimitive()) {
                    throw new IllegalArgumentException("defaultValueFormat does not support primitive type '"
                            + valueType.getName() + "'; use boxed type '" + Types.box(valueType).getName() + "'");
                }
                valueFormatTypes[i] = valueType;
                valueFormats[i] = Asserts.notNull(entry.getValue(), "valueFormat");
                i++;
            }
        }
        this.includeNulls = includeNulls;
    }

    /**
     * Returns the named format for the exact value class, or {@code null} if
     * none is configured (including when {@code valueType} is null).
     */
    public String defaultValueFormat(Class<?> valueType) {
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
    public void copyDefaultValueFormatsTo(Map<Class<?>, String> target) {
        Asserts.notNull(target, "target");
        for (int i = 0; i < valueFormatTypes.length; i++) {
            target.put(valueFormatTypes[i], valueFormats[i]);
        }
    }


}
