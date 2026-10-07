package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.binding.CompiledName;
import tools.jackson.core.io.SerializedString;

public final class Jackson3Name implements CompiledName {
    final SerializedString serializedName;

    public Jackson3Name(String name) {
        this.serializedName = new SerializedString(name);
    }
}
