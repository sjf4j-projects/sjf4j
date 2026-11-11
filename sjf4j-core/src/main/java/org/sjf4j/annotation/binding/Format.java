package org.sjf4j.annotation.binding;

/**
 * Structured data format used by a {@link CompiledBinder}.
 */
public enum Format {
    /** JSON binding through a selected JSON backend. */
    JSON,
    /** YAML binding through the SnakeYAML backend. */
    YAML
}
