package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.binding.StreamingReader;
import tools.jackson.core.sym.BinaryNameMatcher;
import tools.jackson.core.sym.PropertyNameMatcher;

import java.util.Arrays;

/** Prepared Jackson 3 property-name matcher. */
final class Jackson3NameMatcher implements StreamingReader.NameMatcher {

    private final String[] names;
    final PropertyNameMatcher matcher;

    Jackson3NameMatcher(String[] names) {
        this.names = names.clone();
        this.matcher = BinaryNameMatcher.construct(Arrays.asList(this.names));
    }

    @Override
    public String name(int index) {
        return names[index];
    }

    @Override
    public int match(String name) {
        int index = matcher.matchName(name);
        return index >= 0 ? index : UNKNOWN;
    }
}
