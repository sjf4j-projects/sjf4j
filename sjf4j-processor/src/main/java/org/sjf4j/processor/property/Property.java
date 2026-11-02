package org.sjf4j.processor.property;

import org.sjf4j.util.Asserts;

import java.util.Objects;

/**
 * A resolved node-facing property of a Java object type. Gettable and settable
 * indicate available getter/field and setter/field access, respectively.
 */
public final class Property {

    private final String name;
    private final PropertyAccess read;
    private final PropertyAccess write;
    private final boolean explicitCodec;

    Property(String name, PropertyAccess read, PropertyAccess write, boolean explicitCodec) {
        this.name = Asserts.notNull(name, "name");
        this.read = read;
        this.write = write;
        this.explicitCodec = explicitCodec;
    }

    public String name() {
        return name;
    }

    public PropertyAccess read() {
        return read;
    }

    public PropertyAccess write() {
        return write;
    }

    /** True when any member of the property family selects a named/patterned codec. */
    public boolean explicitCodec() {
        return explicitCodec;
    }

    public boolean gettable() {
        return read != null;
    }

    public boolean settable() {
        return write != null;
    }
}
