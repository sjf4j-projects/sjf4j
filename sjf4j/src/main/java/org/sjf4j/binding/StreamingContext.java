package org.sjf4j.binding;

import org.sjf4j.binding.simple.SimpleNodeBinding;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.node.Types;

import java.util.Map;
import java.util.Objects;

/**
 * Shared runtime streaming context assembled by {@code Sjf4j.Builder}.
 */
public final class StreamingContext {
    public final NodeBinding nodeBinding;
    public final boolean includeNulls;
    private final Class<?>[] valueFormatTypes;
    private final String[] valueFormats;

    // Empty
    private static final Class<?>[] EMPTY_VALUE_TYPES = new Class<?>[0];
    private static final String[] EMPTY_VALUE_FORMATS = new String[0];
    public static final StreamingContext EMPTY = new StreamingContext(true);


    public StreamingContext(boolean includeNulls) {
        this.valueFormatTypes = EMPTY_VALUE_TYPES;
        this.valueFormats = EMPTY_VALUE_FORMATS;
        this.includeNulls = includeNulls;
        this.nodeBinding = new SimpleNodeBinding(this);
    }

    public StreamingContext(Map<Class<?>, String> defaultValueFormats) {
        this(defaultValueFormats, true);
    }

    public StreamingContext(Map<Class<?>, String> defaultValueFormats, boolean includeNulls) {
        Objects.requireNonNull(defaultValueFormats, "defaultValueFormats");
        if (defaultValueFormats.isEmpty()) {
            this.valueFormatTypes = EMPTY_VALUE_TYPES;
            this.valueFormats = EMPTY_VALUE_FORMATS;
        } else {
            this.valueFormatTypes = new Class<?>[defaultValueFormats.size()];
            this.valueFormats = new String[defaultValueFormats.size()];
            int i = 0;
            for (Map.Entry<Class<?>, String> entry : defaultValueFormats.entrySet()) {
                Class<?> valueType = Objects.requireNonNull(entry.getKey(), "valueType");
                if (valueType.isPrimitive()) {
                    throw new IllegalArgumentException("defaultValueFormat does not support primitive type '"
                            + valueType.getName() + "'; use boxed type '" + Types.box(valueType).getName() + "'");
                }
                valueFormatTypes[i] = valueType;
                valueFormats[i] = Objects.requireNonNull(entry.getValue(), "valueFormat");
                i++;
            }
        }
        this.includeNulls = includeNulls;
        this.nodeBinding = new SimpleNodeBinding(this);
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
        Objects.requireNonNull(target, "target");
        for (int i = 0; i < valueFormatTypes.length; i++) {
            target.put(valueFormatTypes[i], valueFormats[i]);
        }
    }


}
