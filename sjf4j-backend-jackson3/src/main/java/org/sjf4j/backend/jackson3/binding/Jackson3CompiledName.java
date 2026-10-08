package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.binding.CompiledName;
import tools.jackson.core.io.SerializedString;

/** Compiled Jackson 3 property name. */
public final class Jackson3CompiledName extends CompiledName {

    final SerializedString serializedName;

    public Jackson3CompiledName(String name) {
        super(name);
        serializedName = new SerializedString(name);
    }
}
