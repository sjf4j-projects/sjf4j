package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.binding.PreparedName;

public class Jackson2PreparedName implements PreparedName {
    final SerializedString serializedName;

    public Jackson2PreparedName(String name) {
        this.serializedName = new SerializedString(name);
    }
}
