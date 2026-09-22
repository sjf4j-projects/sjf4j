package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.binding.PreparedName;
import tools.jackson.core.io.SerializedString;

public class Jackson3Name implements PreparedName {
    final SerializedString serializedName;

    public Jackson3Name(String name) {
        this.serializedName = new SerializedString(name);
    }
}
