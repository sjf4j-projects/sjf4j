
package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.util.Fnv;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.node.PropertyInfo;

import java.util.Arrays;

/** Prepared Fastjson2 property-name matcher. */
public final class Fastjson2NameMatcher extends NameMatcher {

    static final int HASH_COLLISION = -3;

    private final long[] hashes;
    private final int[] indexes;
    private final int mask;
    private final boolean hashSafe;

    Fastjson2NameMatcher(PropertyInfo[] writableProperties) {
        this(writableProperties, null, null);
    }

    public Fastjson2NameMatcher(String... names) {
        this(null, names, null);
    }

    public Fastjson2NameMatcher(String[] names, String[][] aliases) {
        this(null, names, aliases);
    }

    private Fastjson2NameMatcher(PropertyInfo[] writableProperties, String[] names, String[][] aliases) {
        super(writableProperties, names, aliases);

        int nameCount = size();
        if (writableProperties != null) {
            for (PropertyInfo property : writableProperties) {
                nameCount += property.alias.length;
            }
        }

        if (writableProperties == null && aliases != null) {
            for (String[] propertyAliases : aliases) {
                if (propertyAliases != null) nameCount += propertyAliases.length;
            }
        }

        int capacity = 1;
        while (capacity < Math.max(4, nameCount * 2)) {
            capacity <<= 1;
        }

        this.hashes = new long[capacity];
        this.indexes = new int[capacity];
        Arrays.fill(this.indexes, UNKNOWN);
        this.mask = capacity - 1;

        boolean safe = true;
        for (int i = 0; i < size(); i++) {
            safe &= add(name(i), i);
            String[] propertyAliases = writableProperties != null
                    ? writableProperties[i].alias
                    : aliases == null ? null : aliases[i];
            if (propertyAliases != null) {
                for (String alias : propertyAliases) {
                    safe &= add(alias, i);
                }
            }
        }

        this.hashSafe = safe;
    }

    private boolean add(String name, int index) {
        long hash = Fnv.hashCode64(name);
        int slot = Long.hashCode(hash) & mask;

        while (indexes[slot] != UNKNOWN) {
            if (hashes[slot] == hash) {
                if (indexes[slot] != index) {
                    indexes[slot] = HASH_COLLISION;
                    return false;
                }
                return true;
            }
            slot = (slot + 1) & mask;
        }

        hashes[slot] = hash;
        indexes[slot] = index;
        return true;
    }

    boolean hashSafe() {
        return hashSafe;
    }

    int matchHash(long hash) {
        int slot = Long.hashCode(hash) & mask;

        while (indexes[slot] != UNKNOWN) {
            if (hashes[slot] == hash) {
                return indexes[slot];
            }
            slot = (slot + 1) & mask;
        }

        return UNKNOWN;
    }


}
