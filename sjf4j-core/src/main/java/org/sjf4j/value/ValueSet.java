package org.sjf4j.value;

import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;
import org.sjf4j.exception.BindingException;

import java.util.Arrays;

/**
 * Immutable per-value-type codec selection, grouped by the JSON shape of the
 * declared raw type. The first entry in each group is the read fallback.
 * Formats are unique; no additional format index or duplicate codec array
 * is retained.
 */
public final class ValueSet {
    public final ValueInfo defaultInfo;
    public final ValueInfo[] stringInfos;
    public final ValueInfo[] numberInfos;
    public final ValueInfo[] booleanInfos;
    public final ValueInfo[] objectInfos;
    public final ValueInfo[] arrayInfos;
    public final boolean multipleJsonTypes;

    public ValueSet(ValueInfo info) {
        this(info,
                info.rawJsonType == JsonType.STRING ? new ValueInfo[]{info} : null,
                info.rawJsonType == JsonType.NUMBER ? new ValueInfo[]{info} : null,
                info.rawJsonType == JsonType.BOOLEAN ? new ValueInfo[]{info} : null,
                info.rawJsonType == JsonType.OBJECT ? new ValueInfo[]{info} : null,
                info.rawJsonType == JsonType.ARRAY ? new ValueInfo[]{info} : null);
    }

    private ValueSet(ValueInfo defaultInfo, ValueInfo[] stringInfos, ValueInfo[] numberInfos,
                     ValueInfo[] booleanInfos, ValueInfo[] objectInfos, ValueInfo[] arrayInfos) {
        this.defaultInfo = defaultInfo;
        this.stringInfos = stringInfos;
        this.numberInfos = numberInfos;
        this.booleanInfos = booleanInfos;
        this.objectInfos = objectInfos;
        this.arrayInfos = arrayInfos;
        int shapes = (stringInfos == null ? 0 : 1) + (numberInfos == null ? 0 : 1)
                + (booleanInfos == null ? 0 : 1) + (objectInfos == null ? 0 : 1)
                + (arrayInfos == null ? 0 : 1);
        this.multipleJsonTypes = shapes > 1;
    }

    private static ValueInfo[] appended(ValueInfo[] previous, ValueInfo info) {
        if (previous == null) return new ValueInfo[]{info};
        ValueInfo[] result = Arrays.copyOf(previous, previous.length + 1);
        result[previous.length] = info;
        return result;
    }

    /** Returns a new snapshot with one registered format. */
    public ValueSet append(ValueInfo info, boolean forceDefault) {
        if (findFormat(info.valueFormat) != null) {
            throw new BindingException("valueCodec already registered for type '"
                    + info.valueClazz.getName() + "' and valueFormat '" + info.valueFormat + "'");
        }
        ValueInfo[] strings = stringInfos, numbers = numberInfos, booleans = booleanInfos;
        ValueInfo[] objects = objectInfos, arrays = arrayInfos;
        switch (info.rawJsonType) {
            case STRING: strings = appended(strings, info); break;
            case NUMBER: numbers = appended(numbers, info); break;
            case BOOLEAN: booleans = appended(booleans, info); break;
            case OBJECT: objects = appended(objects, info); break;
            case ARRAY: arrays = appended(arrays, info); break;
            default:
                throw new BindingException("unsupported ValueCodec raw type: " + info.rawClazz.getName());
        }
        return new ValueSet(forceDefault || defaultInfo == null ? info : defaultInfo,
                strings, numbers, booleans, objects, arrays);
    }

    /** Rebinds inherited registrations to a concrete runtime subtype. */
    public ValueSet forRuntimeClass(Class<?> runtimeClass) {
        ValueSet resolved = null;
        for (ValueInfo info : orderedInfos()) {
            ValueInfo adapted = new ValueInfo(runtimeClass, info);
            resolved = resolved == null ? new ValueSet(adapted)
                    : resolved.append(adapted, info == defaultInfo);
        }
        return resolved;
    }

    private ValueInfo[] orderedInfos() {
        int total = length(stringInfos) + length(numberInfos) + length(booleanInfos)
                + length(objectInfos) + length(arrayInfos);
        ValueInfo[] result = new ValueInfo[total];
        int i = 0;
        for (ValueInfo[] group : new ValueInfo[][]{
                stringInfos, numberInfos, booleanInfos, objectInfos, arrayInfos}) {
            if (group != null) {
                System.arraycopy(group, 0, result, i, group.length);
                i += group.length;
            }
        }
        return result;
    }

    private static int length(ValueInfo[] values) {
        return values == null ? 0 : values.length;
    }

    private static ValueInfo matching(ValueInfo[] group, String format) {
        if (group == null) return null;
        for (ValueInfo info : group) {
            if (info.valueFormat.equals(format)) return info;
        }
        return null;
    }

    /** Finds a named format without a secondary index. */
    public ValueInfo findFormat(String format) {
        if (format == null) return defaultInfo;
        ValueInfo info = matching(stringInfos, format);
        if (info == null) info = matching(numberInfos, format);
        if (info == null) info = matching(booleanInfos, format);
        if (info == null) info = matching(objectInfos, format);
        if (info == null) info = matching(arrayInfos, format);
        return info;
    }

    public ValueInfo requireFormat(String format) {
        ValueInfo info = findFormat(format);
        if (info == null) {
            throw new BindingException("no ValueCodec registered for type '"
                    + defaultInfo.valueClazz.getName() + "' with format '" + format + "'");
        }
        return info;
    }

    public ValueInfo forWrite(String format) {
        return requireFormat(format);
    }

    /**
     * Selects the preferred matching input format; otherwise uses the first
     * codec in the input JSON-type group. Null has no shape and uses the
     * preferred/default codec.
     */
    public ValueInfo selectForRead(JsonType jsonType, String preferredFormat) {
        if (jsonType == JsonType.NULL) return requireFormat(preferredFormat);
        ValueInfo[] group;
        switch (jsonType) {
            case STRING: group = stringInfos; break;
            case NUMBER:
            case INTEGER: group = numberInfos; break;
            case BOOLEAN: group = booleanInfos; break;
            case OBJECT: group = objectInfos; break;
            case ARRAY: group = arrayInfos; break;
            default:
                throw new BindingException("unsupported input JSON type '" + jsonType + "'");
        }
        if (group == null) {
            throw new BindingException("no ValueCodec for type '" + defaultInfo.valueClazz.getName()
                    + "' and input JSON type '" + jsonType + "'");
        }
        if (preferredFormat != null) {
            ValueInfo preferred = matching(group, preferredFormat);
            if (preferred != null) return preferred;
        }
        return group[0];
    }
}
