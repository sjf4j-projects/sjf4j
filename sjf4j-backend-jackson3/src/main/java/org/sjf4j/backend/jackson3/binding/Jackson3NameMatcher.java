package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.binding.NameMatcher;
import org.sjf4j.node.PropertyInfo;

import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.sym.BinaryNameMatcher;
import tools.jackson.core.sym.PropertyNameMatcher;

import java.util.Arrays;

/** Prepared Jackson 3 property-name metadata. */
final class Jackson3NameMatcher extends NameMatcher {

    final PropertyNameMatcher matcher;
    final SerializableString[] serializedNames;

    Jackson3NameMatcher(PropertyInfo[] writableProperties) {
        super(writableProperties);

        String[] names = new String[writableProperties.length];
        serializedNames = new SerializableString[writableProperties.length];

        for (int i = 0; i < writableProperties.length; i++) {
            String name = writableProperties[i].name;
            names[i] = name;
            serializedNames[i] = new SerializedString(name);
        }

        matcher = BinaryNameMatcher.construct(Arrays.asList(names));
    }
}
