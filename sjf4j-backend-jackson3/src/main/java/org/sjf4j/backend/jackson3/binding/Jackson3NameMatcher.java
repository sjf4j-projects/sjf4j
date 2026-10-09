package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.binding.NameMatcher;
import org.sjf4j.node.PropertyInfo;

import tools.jackson.core.sym.BinaryNameMatcher;
import tools.jackson.core.sym.PropertyNameMatcher;

import java.util.Arrays;

/** Prepared Jackson 3 property-name metadata. */
public final class Jackson3NameMatcher extends NameMatcher {

    final PropertyNameMatcher matcher;

    Jackson3NameMatcher(PropertyInfo[] properties) {
        this(properties, null, null);
    }

    public Jackson3NameMatcher(String... names) {
        this(null, names, null);
    }

    public Jackson3NameMatcher(String[] names, String[][] aliases) {
        this(null, names, aliases);
    }

    private Jackson3NameMatcher(PropertyInfo[] properties, String[] names, String[][] aliases) {
        super(properties, names, aliases);

        String[] propertyNames = new String[size()];
        for (int i = 0; i < size(); i++) {
            propertyNames[i] = name(i);
        }
        matcher = BinaryNameMatcher.construct(Arrays.asList(propertyNames));
    }
}