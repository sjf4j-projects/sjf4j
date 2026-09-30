package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.binding.NameMatcher;

import java.util.HashMap;
import java.util.Map;

/** Prepared Jackson 2 property-name metadata for V3 readers and generated code. */
public final class Jackson2NameMatcherV3 implements NameMatcher {

    final String[] names;
    final SerializedString[] serializedNames;
    private final Map<String, Integer> fallbackLookup;

    private Jackson2NameMatcherV3(String... sourceNames) {
        if (sourceNames == null) {
            throw new NullPointerException("names");
        }
        names = sourceNames.clone();
        serializedNames = new SerializedString[names.length];
        fallbackLookup = new HashMap<String, Integer>(Math.max(4, names.length * 2));
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            if (name == null) {
                throw new NullPointerException("names[" + i + "]");
            }
            serializedNames[i] = new SerializedString(name);
            fallbackLookup.put(name, i);
        }
    }

    /** Creates matcher metadata in property-index order. */
    public static Jackson2NameMatcherV3 of(String... names) {
        return new Jackson2NameMatcherV3(names);
    }

    @Override
    public String name(int index) {
        return names[index];
    }

    @Override
    public int match(String name) {
        Integer index = fallbackLookup.get(name);
        return index == null ? UNKNOWN_FIELD : index;
    }
}
