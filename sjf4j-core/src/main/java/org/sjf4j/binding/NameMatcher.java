
package org.sjf4j.binding;

import org.sjf4j.node.PropertyInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Prepared member-name matcher for runtime and compiled binding.
 */
public class NameMatcher {

    public static final int UNKNOWN = -1;
    public static final int OBJECT_END = -2;

    // Null for compiled binding.
    protected final PropertyInfo[] writableProperties;

    private final String[] names;
    private final Map<String, Integer> fallback;

    /**
     * Runtime binding from property metadata.
     */
    public NameMatcher(PropertyInfo[] writableProperties) {
        this(writableProperties, null, null);
    }

    /**
     * Compiled binding from static property names.
     */
    public NameMatcher(String... names) {
        this(null, names, null);
    }

    /**
     * Shared initialization for backend subclasses.
     * Exactly one argument must be non-null.
     */
    public NameMatcher(String[] names, String[][] aliases) {
        this(null, names, aliases);
    }

    protected NameMatcher(PropertyInfo[] writableProperties, String[] compiledNames) {
        this(writableProperties, compiledNames, null);
    }

    protected NameMatcher(PropertyInfo[] writableProperties, String[] compiledNames,
                          String[][] compiledAliases) {
        if ((writableProperties == null) == (compiledNames == null)) {
            throw new IllegalArgumentException(
                    "Exactly one of writableProperties or compiledNames must be provided");
        }
        if (compiledAliases != null &&
                (compiledNames == null || compiledAliases.length != compiledNames.length)) {
            throw new IllegalArgumentException("Compiled aliases must align with names");
        }
        this.writableProperties = writableProperties;

        if (writableProperties != null) {
            names = new String[writableProperties.length];
            for (int i = 0; i < names.length; i++) {
                names[i] = Objects.requireNonNull(writableProperties[i].name, "property name");
            }
        } else {
            names = compiledNames.clone();
            for (String name : names) {
                Objects.requireNonNull(name, "property name");
            }
        }

        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            map.put(names[i], i);
            String[] aliases = writableProperties != null
                    ? writableProperties[i].alias
                    : compiledAliases == null ? null : compiledAliases[i];
            if (aliases != null) {
                for (String alias : aliases) {
                    map.put(alias, i);
                }
            }
        }
        this.fallback = map;
    }

    public final int size() {
        return names.length;
    }

    public final String name(int index) {
        return names[index];
    }

    /**
     * Available only for runtime-created matchers.
     */
    public final PropertyInfo property(int index) {
        if (writableProperties == null) {
            throw new IllegalStateException("Compiled NameMatcher has no PropertyInfo");
        }
        return writableProperties[index];
    }

    public int fallback(String name) {
        Integer index = fallback.get(name);
        return index != null ? index : UNKNOWN;
    }


}
