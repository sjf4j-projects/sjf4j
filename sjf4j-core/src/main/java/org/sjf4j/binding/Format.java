package org.sjf4j.binding;

import java.util.Objects;

/**
 * Stable identifier for a streaming binding format.
 *
 * <p>Built-in formats use short lowercase identifiers. Third-party formats
 * should use a namespaced identifier such as {@code com.example:toml}.</p>
 */
public final class Format {

    public static final Format JSON = new Format("json");
    public static final Format YAML = new Format("yaml");

    private final String id;

    private Format(String id) {
        this.id = id;
    }

    /**
     * Creates a format identified by {@code id}.
     */
    public static Format of(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isEmpty() || !id.equals(id.trim())) {
            throw new IllegalArgumentException("format id must not be empty or contain leading/trailing whitespace");
        }
        return new Format(id);
    }

    /**
     * Returns this format's stable identifier.
     */
    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Format && id.equals(((Format) other).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }

}
