package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.binding.CompiledName;
import tools.jackson.core.io.SerializedString;

/** Compiled Jackson 3 property name. */
public final class Jackson3CompiledName extends CompiledName {

    public final SerializedString serializedName;


    /**
     * Prepares a property name for repeated Jackson 3 writes.
     * @param name the property name
     */
    public Jackson3CompiledName(String name) {
        super(name);
        serializedName = new SerializedString(name);
    }
}
