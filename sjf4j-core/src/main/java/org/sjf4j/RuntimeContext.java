package org.sjf4j;

import org.sjf4j.binding.NodeBinder;
import org.sjf4j.binding.simple.SimpleNodeBinder;
import org.sjf4j.node.Types;
import org.sjf4j.util.Asserts;

import java.util.Map;

/**
 * Shared runtime streaming context assembled by {@code Sjf4j.Builder}.
 */
public final class RuntimeContext {
    public final boolean includeNulls;
    private final Class<?>[] valueFormatTypes;
    private final String[] valueFormats;

    // Empty
    private static final Class<?>[] EMPTY_VALUE_TYPES = new Class<?>[0];
    private static final String[] EMPTY_VALUE_FORMATS = new String[0];

    public static final RuntimeContext EMPTY = new RuntimeContext(true);


    public RuntimeContext(boolean includeNulls) {
        this.valueFormatTypes = EMPTY_VALUE_TYPES;
        this.valueFormats = EMPTY_VALUE_FORMATS;
        this.includeNulls = includeNulls;
    }

    public RuntimeContext(Map<Class<?>, String> defaultValueFormats) {
        this(defaultValueFormats, true);
    }

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

    public String defaultValueFormat(Class<?> valueType) {
        if (valueType == null) return null;
        for (int i = 0; i < valueFormatTypes.length; i++) {
            if (valueFormatTypes[i] == valueType) {
                return valueFormats[i];
            }
        }
        return null;
    }

    public void copyDefaultValueFormatsTo(Map<Class<?>, String> target) {
        Asserts.notNull(target, "target");
        for (int i = 0; i < valueFormatTypes.length; i++) {
            target.put(valueFormatTypes[i], valueFormats[i]);
        }
    }


}
