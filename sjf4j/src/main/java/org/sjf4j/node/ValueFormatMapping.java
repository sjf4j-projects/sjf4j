package org.sjf4j.node;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable instance-level default value-format mapping.
 */
public final class ValueFormatMapping {
    public static final ValueFormatMapping EMPTY = new ValueFormatMapping(new Class<?>[0], new String[0]);

    private final Class<?>[] valueTypes;
    private final String[] valueFormats;

    private ValueFormatMapping(Class<?>[] valueTypes, String[] valueFormats) {
        this.valueTypes = valueTypes == null ? new Class<?>[0] : valueTypes;
        this.valueFormats = valueFormats == null ? new String[0] : valueFormats;
    }

    public static ValueFormatMapping of(Map<Class<?>, String> formats) {
        if (formats == null || formats.isEmpty()) return EMPTY;
        Class<?>[] valueTypes = new Class<?>[formats.size()];
        String[] valueFormats = new String[formats.size()];
        int i = 0;
        for (Map.Entry<Class<?>, String> entry : formats.entrySet()) {
            Class<?> valueType = Objects.requireNonNull(entry.getKey(), "valueType");
            if (valueType.isPrimitive()) {
                throw new IllegalArgumentException("defaultValueFormat does not support primitive type '"
                        + valueType.getName() + "'; use boxed type '" + Types.box(valueType).getName() + "'");
            }
            valueTypes[i] = valueType;
            valueFormats[i] = Objects.requireNonNull(entry.getValue(), "valueFormat");
            i++;
        }
        return new ValueFormatMapping(valueTypes, valueFormats);
    }

    public boolean isEmpty() {
        return valueTypes.length == 0;
    }

    public Map<Class<?>, String> asMap() {
        LinkedHashMap<Class<?>, String> map = new LinkedHashMap<>(valueTypes.length);
        for (int i = 0; i < valueTypes.length; i++) {
            map.put(valueTypes[i], valueFormats[i]);
        }
        return map;
    }

    public String defaultValueFormat(Class<?> valueType) {
        if (valueType == null) return null;
        for (int i = 0; i < valueTypes.length; i++) {
            if (valueTypes[i] == valueType) {
                return valueFormats[i];
            }
        }
        return null;
    }

}
