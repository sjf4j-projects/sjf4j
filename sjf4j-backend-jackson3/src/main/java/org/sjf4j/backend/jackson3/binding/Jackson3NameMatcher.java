package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.binding.NameMatcher;
import org.sjf4j.node.PropertyInfo;
import tools.jackson.core.sym.BinaryNameMatcher;
import tools.jackson.core.sym.PropertyNameMatcher;

import java.util.Arrays;

/** Prepared Jackson 3 property-name metadata. */
final class Jackson3NameMatcher extends NameMatcher {

    final PropertyNameMatcher matcher;

    Jackson3NameMatcher(PropertyInfo[] writableProperties) {
        super(writableProperties);

        String[] names = new String[writableProperties.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = writableProperties[i].name;
        }
        matcher = BinaryNameMatcher.construct(Arrays.asList(names));
    }
}
