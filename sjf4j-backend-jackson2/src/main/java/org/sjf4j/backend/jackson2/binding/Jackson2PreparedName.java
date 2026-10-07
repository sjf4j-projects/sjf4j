package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.binding.CompiledName;

public class Jackson2PreparedName implements CompiledName {
    final SerializedString serializedName;

    public Jackson2PreparedName(String name) {
        this.serializedName = new SerializedString(name);
    }
}
