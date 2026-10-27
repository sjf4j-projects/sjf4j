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
    private final String constantString;
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
        this.constantString = null;
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
        this.constantString = null;
        this.value = Asserts.notNull(value, "value");
        this.creatorIndex = creatorIndex;
        this.creatorCount = creatorCount;
        this.pendingIndex = -1;
    }

    /** A settable property that must wait until the creator returns. */
    BindingProperty(String name, String[] aliases, PropertyAccess access,
                    BindingValue value, int pendingIndex) {
        this.name = Asserts.notNull(name, "name");
        this.aliases = Asserts.notNull(aliases, "aliases");
        this.access = Asserts.notNull(access, "access");
        this.constantString = null;
        this.value = Asserts.notNull(value, "value");
        this.creatorIndex = -1;
        this.creatorCount = 0;
        this.pendingIndex = pendingIndex;
    }

    /** Inaccessible compile-time String constant on a mapped OneOf subtype. */
    BindingProperty(String name, String constantString, BindingValue value) {
        this.name = Asserts.notNull(name, "name");
        this.aliases = new String[0];
        this.access = null;
        this.constantString = Asserts.notNull(constantString, "constantString");
        this.value = Asserts.notNull(value, "value");
        this.creatorIndex = -1;
        this.creatorCount = 0;
        this.pendingIndex = -1;
    }

    String constantString() {
        return constantString;
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
