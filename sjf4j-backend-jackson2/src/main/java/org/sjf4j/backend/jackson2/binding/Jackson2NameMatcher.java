
package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.node.PropertyInfo;

/** Prepared Jackson 2 property-name metadata. */
public final class Jackson2NameMatcher extends NameMatcher {

    final SerializableString[] serializedNames;

    Jackson2NameMatcher(PropertyInfo[] writableProperties) {
        this(writableProperties, null, null);
    }

    public Jackson2NameMatcher(String... names) {
        this(null, names, null);
    }

    public Jackson2NameMatcher(String[] names, String[][] aliases) {
        this(null, names, aliases);
    }

    private Jackson2NameMatcher(PropertyInfo[] writableProperties, String[] names, String[][] aliases) {
        super(writableProperties, names, aliases);
        serializedNames = new SerializableString[size()];
        for (int i = 0; i < size(); i++) {
            serializedNames[i] = new SerializedString(name(i));
        }
    }

}
