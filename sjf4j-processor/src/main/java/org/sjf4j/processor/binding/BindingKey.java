package org.sjf4j.processor.binding;

import java.util.Objects;

/** Cache identity within a single compiled binder interface. */
final class BindingKey {

    private final BindingPlan.Direction direction;
    private final String type;
    private final String format;
    private final String pattern;
    private final boolean inlineOneOf;

    BindingKey(BindingPlan.Direction direction, String type, String format,
               String pattern, boolean inlineOneOf) {
        this.direction = direction;
        this.type = type;
        this.format = format;
        this.pattern = pattern;
        this.inlineOneOf = inlineOneOf;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BindingKey)) return false;
        BindingKey key = (BindingKey) other;
        return direction == key.direction && inlineOneOf == key.inlineOneOf
                && type.equals(key.type)
                && Objects.equals(format, key.format)
                && Objects.equals(pattern, key.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(direction, type, format, pattern, inlineOneOf);
    }
}
