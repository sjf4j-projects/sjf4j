
package org.sjf4j.binding;

import org.sjf4j.meta.PropertyInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Prepared member-name matcher for runtime and compiled binding. A match
 * returns its property index; unknown names and object end use distinct
 * negative sentinels.
 */
public class NameMatcher {

    /** A member was consumed, but no declared name or alias matched. */
    public static final int UNKNOWN = -1;
    /** The enclosing object ended; there is no pending member value. */
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
     * Prepares static names and optional index-aligned aliases.
     * The two arrays must have the same length when aliases are provided.
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

    /** Returns the number of declared names, excluding aliases. */
    public final int size() {
        return names.length;
    }

    /** Returns the primary name at a declared index. */
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

    /**
     * Matches a member name or alias by lookup, returning {@link #UNKNOWN}
     * if it is not declared.
     */
    public int fallback(String name) {
        Integer index = fallback.get(name);
        return index != null ? index : UNKNOWN;
    }


}
