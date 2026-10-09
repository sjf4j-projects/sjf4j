package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.binding.NameMatcher;
import org.sjf4j.node.PropertyInfo;

import tools.jackson.core.sym.BinaryNameMatcher;
import tools.jackson.core.sym.PropertyNameMatcher;

import java.util.Arrays;

/** Prepared Jackson 3 property-name metadata. */
final class Jackson3NameMatcher extends NameMatcher {

    final PropertyNameMatcher matcher;

    Jackson3NameMatcher(PropertyInfo[] properties) {
        this(properties, null);
    }

    Jackson3NameMatcher(String... names) {
        this(null, names);
    }

    private Jackson3NameMatcher(PropertyInfo[] properties, String[] names) {
        super(properties, names);

        String[] propertyNames = new String[size()];
        for (int i = 0; i < size(); i++) {
            propertyNames[i] = name(i);
        }
        matcher = BinaryNameMatcher.construct(Arrays.asList(propertyNames));
    }
}