package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.node.PropertyInfo;

/** Prepared Jackson 2 property-name metadata. */
final class Jackson2NameMatcher extends NameMatcher {

    final SerializableString[] serializedNames;

    Jackson2NameMatcher(PropertyInfo[] writableProperties) {
        super(writableProperties);
        serializedNames = new SerializableString[writableProperties.length];
        for (int i = 0; i < writableProperties.length; i++) {
            serializedNames[i] = new SerializedString(writableProperties[i].name);
        }
    }
}
