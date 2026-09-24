package org.sjf4j.processor.binding;

import org.sjf4j.processor.property.PropertyAccess;

import java.util.Objects;

/**
 * One compiled POJO property for a read or write binding.
 */
final class BindingProperty {

    private final String name;
    private final PropertyAccess access;
    private final BindingValue value;

    BindingProperty(
            String name,
            PropertyAccess access,
            BindingValue value) {

        this.name = Objects.requireNonNull(name, "name");
        this.access = Objects.requireNonNull(access, "access");
        this.value = Objects.requireNonNull(value, "value");
    }

    String name() {
        return name;
    }

    PropertyAccess access() {
        return access;
    }

    BindingValue value() {
        return value;
    }
}
