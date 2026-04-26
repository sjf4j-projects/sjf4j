package org.sjf4j.facade;

import org.sjf4j.facade.simple.SimpleNodeFacade;
import org.sjf4j.node.Types;

import java.util.Map;
import java.util.Objects;

/**
 * Shared runtime streaming context assembled by {@code Sjf4j.Builder}.
 */
public final class StreamingContext {
    public final StreamingMode streamingMode;
    public final NodeFacade nodeFacade;
    public final boolean includeNulls;
    private final Class<?>[] valueFormatTypes;
    private final String[] valueFormats;

    // Empty
    private static final Class<?>[] EMPTY_VALUE_TYPES = new Class<?>[0];
    private static final String[] EMPTY_VALUE_FORMATS = new String[0];
    public static final StreamingContext EMPTY = new StreamingContext(StreamingMode.AUTO, true);


    public StreamingContext(StreamingMode streamingMode) {
        this(streamingMode, true);
    }

    public StreamingContext(StreamingMode streamingMode, boolean includeNulls) {
        this.valueFormatTypes = EMPTY_VALUE_TYPES;
        this.valueFormats = EMPTY_VALUE_FORMATS;
        this.streamingMode = Objects.requireNonNull(streamingMode, "streamingMode");
        this.includeNulls = includeNulls;
        this.nodeFacade = new SimpleNodeFacade(this);
    }

    public StreamingContext(Map<Class<?>, String> defaultValueFormats) {
        this(defaultValueFormats, StreamingMode.AUTO, true);
    }

    public StreamingContext(Map<Class<?>, String> defaultValueFormats,
                             StreamingMode streamingMode,
                             boolean includeNulls) {
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
        this.streamingMode = Objects.requireNonNull(streamingMode, "streamingMode");
        this.includeNulls = includeNulls;
        this.nodeFacade = new SimpleNodeFacade(this);
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


    public enum StreamingMode {
        /**
         * Lets the facade choose its preferred runtime mode.
         */
        AUTO,

        /**
         * Uses the shared SJF4J streaming reader/writer adapters.
         */
        SHARED_IO,

        /**
         * Uses a backend-specific streaming implementation when the facade has one.
         */
        EXCLUSIVE_IO,

        /**
         * Uses the backend's module/plugin binding path instead of shared streaming IO.
         */
        PLUGIN_MODULE
    }

}
