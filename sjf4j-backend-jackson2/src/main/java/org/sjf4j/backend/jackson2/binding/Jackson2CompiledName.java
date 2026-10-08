package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.binding.CompiledName;

final class Jackson2CompiledName extends CompiledName {
    final SerializedString serializedName;

    Jackson2CompiledName(String name) {
        super(name);
        this.serializedName = new SerializedString(name);
    }
}
