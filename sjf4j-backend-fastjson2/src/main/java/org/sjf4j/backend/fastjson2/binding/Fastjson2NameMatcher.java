
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
        this(writableProperties, null);
    }

    public Fastjson2NameMatcher(String... names) {
        this(null, names);
    }

    private Fastjson2NameMatcher(PropertyInfo[] writableProperties, String[] names) {
        super(writableProperties, names);

        int nameCount = size();
        if (writableProperties != null) {
            for (PropertyInfo property : writableProperties) {
                nameCount += property.alias.length;
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
            if (writableProperties != null) {
                for (String alias : writableProperties[i].alias) {
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
