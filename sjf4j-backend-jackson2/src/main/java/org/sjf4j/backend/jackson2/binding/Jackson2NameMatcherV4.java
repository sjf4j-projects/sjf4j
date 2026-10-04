package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeRegistry;

import java.util.HashMap;
import java.util.Map;

/** Prepared Jackson 2 property-name metadata for V4 readers. */
final class Jackson2NameMatcherV4 implements NameMatcher {

    private static final ClassValue<Jackson2NameMatcherV4> MATCHERS =
            new ClassValue<Jackson2NameMatcherV4>() {
                @Override
                protected Jackson2NameMatcherV4 computeValue(Class<?> type) {
                    return new Jackson2NameMatcherV4(TypeRegistry.requireRegisteredPojoInfo(type));
                }
            };

    final String[] names;
    final SerializableString[] serializedNames;
    private final Map<String, Integer> indexes;

    private Jackson2NameMatcherV4(PojoInfo pojoInfo) {
        int size = pojoInfo.properties.size();
        names = new String[size];
        serializedNames = new SerializableString[size];
        indexes = new HashMap<>(Math.max(4, names.length * 2));
        int i = 0;
        for (String name : pojoInfo.properties.keySet()) {
            names[i] = name;
            serializedNames[i] = new SerializedString(name);
            indexes.put(name, i);
            i++;
        }
    }

    static Jackson2NameMatcherV4 get(Class<?> type) {
        return MATCHERS.get(type);
    }

    @Override
    public String name(int index) {
        return names[index];
    }

    @Override
    public int match(String name) {
        Integer index = indexes.get(name);
        return index == null ? UNKNOWN : index;
    }
}
