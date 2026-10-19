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
    private final int creatorIndex;
    private final int creatorCount;
    private final int pendingIndex;

    BindingProperty(
            String name,
            String[] aliases,
            PropertyAccess access,
            BindingValue value) {

        this.name = Asserts.notNull(name, "name");
        this.aliases = Asserts.notNull(aliases, "aliases");
        this.access = Asserts.notNull(access, "access");
        this.value = Asserts.notNull(value, "value");
        this.creatorIndex = -1;
        this.creatorCount = 0;
        this.pendingIndex = -1;
    }

    BindingProperty(String name, String[] aliases, BindingValue value,
                    int creatorIndex, int creatorCount) {
        this.name = Asserts.notNull(name, "name");
        this.aliases = Asserts.notNull(aliases, "aliases");
        this.access = null;
        this.value = Asserts.notNull(value, "value");
        this.creatorIndex = creatorIndex;
        this.creatorCount = creatorCount;
        this.pendingIndex = -1;
    }

    /** A writable property that must wait until the creator returns. */
    BindingProperty(String name, String[] aliases, PropertyAccess access,
                    BindingValue value, int pendingIndex) {
        this.name = Asserts.notNull(name, "name");
        this.aliases = Asserts.notNull(aliases, "aliases");
        this.access = Asserts.notNull(access, "access");
        this.value = Asserts.notNull(value, "value");
        this.creatorIndex = -1;
        this.creatorCount = 0;
        this.pendingIndex = pendingIndex;
    }

    int pendingIndex() {
        return pendingIndex;
    }

    String name() {
        return name;
    }

    String[] aliases() {
        return aliases;
    }

    int creatorIndex() {
        return creatorIndex;
    }

    int creatorCount() {
        return creatorCount;
    }

    PropertyAccess access() {
        return access;
    }

    BindingValue value() {
        return value;
    }
}
