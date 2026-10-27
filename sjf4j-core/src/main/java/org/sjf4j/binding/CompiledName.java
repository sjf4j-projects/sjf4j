package org.sjf4j.binding;

import org.sjf4j.util.Asserts;


/**
 * Reusable member name prepared for a streaming writer. Backend subclasses
 * may additionally cache the encoded form of the name.
 */
public class CompiledName {

    private final String name;

    /** Creates a prepared name from its non-null Java string. */
    public CompiledName(String name) {
        this.name = Asserts.notNull(name, "name");
    }

    /** Returns the original member name. */
    public final String name() {
        return name;
    }
}
