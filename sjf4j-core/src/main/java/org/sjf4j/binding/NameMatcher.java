package org.sjf4j.binding;


import org.sjf4j.node.PropertyInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Prepared member-name matcher.
 *
 * <p>A successful match returns a non-negative index. The canonical
 * member name for that index can be obtained through
 * {@link #name(int)}.</p>
 *
 * <p>Backend implementations may attach native matching metadata to an
 * implementation of this interface. This allows
 * {@link StreamingReader#nextNameMatch(NameMatcher)} to match directly against the
 * underlying input without first materializing the member name as a
 * {@link String}.</p>
 */
public class NameMatcher {

    /**
     * Indicates that a member name did not match any known name.
     */
    public static final int UNKNOWN = -1;

    /**
     * Indicates that object traversal reached the object end.
     */
    public static final int OBJECT_END = -2;

    protected final PropertyInfo[] writableProperties;
    private final Map<String, Integer> fallback;

    public NameMatcher(PropertyInfo[] writableProperties) {
        this.writableProperties = writableProperties;
        Map<String, Integer> fallback = new HashMap<>(Math.max(16, (int) (writableProperties.length / 0.75f) + 1));
        for (int i = 0; i < writableProperties.length; i++) {
            fallback.put(writableProperties[i].name, i);
            for (String alias : writableProperties[i].alias) {
                fallback.put(alias, i);
            }
        }
        this.fallback = fallback;
    }

    public final int size() {
        return writableProperties.length;
    }

    public final String name(int index) {
        return writableProperties[index].name;
    }

    public final PropertyInfo property(int index) {
        return writableProperties[index];
    }

    public int fallback(String name) {
        Integer index = fallback.get(name);
        return index != null ? index : UNKNOWN;
    }

}
