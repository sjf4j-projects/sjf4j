package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.binding.CompiledName;

final class Jackson2PreparedName extends CompiledName {
    final SerializedString serializedName;

    Jackson2PreparedName(String name) {
        super(name);
        this.serializedName = new SerializedString(name);
    }
}
