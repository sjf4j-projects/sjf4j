package org.sjf4j.processor.property;

import org.sjf4j.util.Asserts;

import java.util.Objects;

/**
 * A resolved node-facing property of a Java object type.
 */
public final class Property {

    private final String name;
    private final PropertyAccess read;
    private final PropertyAccess write;

    Property(
            String name,
            PropertyAccess read,
            PropertyAccess write) {

        this.name = Asserts.notNull(name, "name");
        this.read = read;
        this.write = write;
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

    public boolean readable() {
        return read != null;
    }

    public boolean writable() {
        return write != null;
    }
}