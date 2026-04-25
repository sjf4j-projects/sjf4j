package org.sjf4j.facade;

import org.sjf4j.facade.simple.SimpleNodeFacade;
import org.sjf4j.node.ValueFormatMapping;

import java.util.Objects;

/**
 * Shared runtime streaming context assembled by {@code Sjf4j.Builder}.
 */
public final class StreamingContext {
    public static final StreamingContext EMPTY = new StreamingContext(ValueFormatMapping.EMPTY, StreamingMode.AUTO, true);

    public final ValueFormatMapping valueFormatMapping;
    public final StreamingMode streamingMode;
    public final NodeFacade nodeFacade;
    public final boolean includeNulls;

    public StreamingContext(ValueFormatMapping valueFormatMapping) {
        this(valueFormatMapping, StreamingMode.AUTO, true);
    }

    public StreamingContext(StreamingMode streamingMode) {
        this(ValueFormatMapping.EMPTY, streamingMode, true);
    }

    public StreamingContext(ValueFormatMapping valueFormatMapping,
                            StreamingMode streamingMode,
                            boolean includeNulls) {
        this.valueFormatMapping = Objects.requireNonNull(valueFormatMapping, "valueFormatMapping");
        this.streamingMode = Objects.requireNonNull(streamingMode, "streamingMode");
        this.nodeFacade = new SimpleNodeFacade(this.valueFormatMapping);
        this.includeNulls = includeNulls;
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
