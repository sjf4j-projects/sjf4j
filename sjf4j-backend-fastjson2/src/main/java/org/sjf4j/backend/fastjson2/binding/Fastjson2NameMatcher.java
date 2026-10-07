package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.util.Fnv;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Prepared Fastjson2 property-name matcher. */
final class Fastjson2NameMatcher implements StreamingReader.NameMatcher {

    private final String[] names;
    private final Map<String, Integer> indexesByName;

    private final long[] hashes;
    private final int[] indexes;
    private final int mask;
    private final boolean hashSafe;

    Fastjson2NameMatcher(String[] names) {
        this.names = names.clone();
        this.indexesByName = new HashMap<>(Math.max(4, names.length * 2));

        int capacity = 1;
        while (capacity < Math.max(4, names.length * 2)) {
            capacity <<= 1;
        }

        this.hashes = new long[capacity];
        this.indexes = new int[capacity];
        Arrays.fill(this.indexes, -1);
        this.mask = capacity - 1;

        boolean safe = true;

        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            indexesByName.put(name, i);

            long hash = Fnv.hashCode64(name);
            int slot = Long.hashCode(hash) & mask;

            while (indexes[slot] >= 0) {
                if (hashes[slot] == hash) {
                    safe = false;
                    break;
                }
                slot = (slot + 1) & mask;
            }

            if (indexes[slot] < 0) {
                hashes[slot] = hash;
                indexes[slot] = i;
            }
        }

        this.hashSafe = safe;
    }

    @Override
    public String name(int index) {
        return names[index];
    }

    @Override
    public int match(String name) {
        Integer index = indexesByName.get(name);
        return index != null ? index : UNKNOWN;
    }

    boolean hashSafe() {
        return hashSafe;
    }

    int matchHash(long hash) {
        int slot = Long.hashCode(hash) & mask;

        while (indexes[slot] >= 0) {
            if (hashes[slot] == hash) {
                return indexes[slot];
            }
            slot = (slot + 1) & mask;
        }

        return UNKNOWN;
    }
}
