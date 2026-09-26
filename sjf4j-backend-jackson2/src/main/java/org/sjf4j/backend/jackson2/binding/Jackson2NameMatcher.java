package org.sjf4j.backend.jackson2.binding;

import org.sjf4j.binding.StreamingReader;

import java.util.HashMap;
import java.util.Map;

/** Prepared Jackson 2 property-name matcher. */
final class Jackson2NameMatcher implements StreamingReader.NameMatcher {

    private final String[] names;
    private final Map<String, Integer> indexes;

    Jackson2NameMatcher(String[] names) {
        this.names = names.clone();
        this.indexes = new HashMap<String, Integer>(Math.max(4, names.length * 2));

        for (int i = 0; i < names.length; i++) {
            indexes.put(names[i], i);
        }
    }

    @Override
    public String name(int index) {
        return names[index];
    }

    @Override
    public int match(String name) {
        Integer index = indexes.get(name);
        return index != null ? index : UNKNOWN;
    }

    int size() {
        return names.length;
    }
}
