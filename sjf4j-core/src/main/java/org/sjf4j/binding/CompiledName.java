package org.sjf4j.binding;

import org.sjf4j.util.Asserts;


public class CompiledName {

    private final String name;

    protected CompiledName(String name) {
        this.name = Asserts.notNull(name, "name");
    }

    public final String name() {
        return name;
    }
}