package org.sjf4j.processor.binding;

import org.sjf4j.processor.property.PropertyAccess;
import org.sjf4j.util.Asserts;


/**
 * One compiled POJO property for a read or write binding.
 */
final class BindingProperty {

    private final String name;
    private final String[] aliases;
    private final PropertyAccess access;
    private final BindingValue value;

    BindingProperty(
            String name,
            String[] aliases,
            PropertyAccess access,
            BindingValue value) {

        this.name = Asserts.notNull(name, "name");
        this.aliases = Asserts.notNull(aliases, "aliases");
        this.access = Asserts.notNull(access, "access");
        this.value = Asserts.notNull(value, "value");
    }

    String name() {
        return name;
    }

    String[] aliases() {
        return aliases;
    }

    PropertyAccess access() {
        return access;
    }

    BindingValue value() {
        return value;
    }
}
