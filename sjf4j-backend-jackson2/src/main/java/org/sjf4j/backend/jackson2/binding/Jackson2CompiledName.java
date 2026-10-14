package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.binding.CompiledName;

/** Prepared property name with Jackson 2's serialized string representation. */
public final class Jackson2CompiledName extends CompiledName {
    final SerializedString serializedName;

    /**
     * Prepares a property name for repeated Jackson 2 writes.
     * @param name the property name
     */
    public Jackson2CompiledName(String name) {
        super(name);
        this.serializedName = new SerializedString(name);
    }
}
