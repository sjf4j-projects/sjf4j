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
    private final String valueFormat;
    private final String valuePattern;
    private final String[] aliases;

    Property(String name, PropertyAccess read, PropertyAccess write,
             String valueFormat, String valuePattern) {
        this(name, read, write, valueFormat, valuePattern, new String[0]);
    }

    Property(String name, PropertyAccess read, PropertyAccess write,
             String valueFormat, String valuePattern, String[] aliases) {
        this.name = Asserts.notNull(name, "name");
        this.read = read;
        this.write = write;
        this.valueFormat = valueFormat;
        this.valuePattern = valuePattern;
        this.aliases = Asserts.notNull(aliases, "aliases");
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
        return valueFormat != null || valuePattern != null;
    }

    public String valueFormat() {
        return valueFormat;
    }

    public String valuePattern() {
        return valuePattern;
    }

    /** Input aliases collected across all members of the property family. */
    public String[] aliases() {
        return aliases;
    }

    public boolean gettable() {
        return read != null;
    }

    public boolean settable() {
        return write != null;
    }
}
