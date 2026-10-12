
package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.util.Fnv;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.node.PropertyInfo;

import java.util.Arrays;

/** Prepared Fastjson2 property-name matcher. */
public final class Fastjson2NameMatcher extends NameMatcher {

    static final int HASH_COLLISION = -3;

    private final long[] hashes;
    private final long[] expectedHashes;
    private final int[] rawPrefixes;
    private final long[] rawSuffixes;
    private final byte[] rawLengths;
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
        this.expectedHashes = new long[size()];
        this.rawPrefixes = new int[size()];
        this.rawSuffixes = new long[size()];
        this.rawLengths = new byte[size()];
        this.indexes = new int[capacity];
        Arrays.fill(this.indexes, UNKNOWN);
        this.mask = capacity - 1;

        boolean safe = true;
        for (int i = 0; i < size(); i++) {
            expectedHashes[i] = Fnv.hashCode64(name(i));
            prepareRawName(name(i), i);
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

    /**
     * Matches an expected unescaped ASCII name before computing its FNV hash.
     * A mismatch does not consume input; callers use the generic hash matcher.
     */
    boolean matchesExpectedRaw(JSONReader reader, int index) {
        if (index < 0 || index >= rawLengths.length) {
            return false;
        }
        int length = rawLengths[index];
        if (length == 0 || reader.getRawInt() != rawPrefixes[index]) {
            return false;
        }
        long suffix = rawSuffixes[index];
        switch (length) {
            case 2: return reader.nextIfName4Match2();
            case 3: return reader.nextIfName4Match3();
            case 4: return reader.nextIfName4Match4((byte) suffix);
            case 5: return reader.nextIfName4Match5((int) suffix);
            case 6: return reader.nextIfName4Match6((int) suffix);
            case 7: return reader.nextIfName4Match7((int) suffix);
            case 8: return reader.nextIfName4Match8((int) suffix, (byte) (suffix >>> 32));
            case 9: return reader.nextIfName4Match9(suffix);
            case 10: return reader.nextIfName4Match10(suffix);
            case 11: return reader.nextIfName4Match11(suffix);
            default: return false;
        }
    }

    private void prepareRawName(String name, int index) {
        int length = name.length();
        if (length < 2 || length > 11) return;
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c >= 0x7f || c == '"' || c == '\\') return;
        }
        String literal = "\"" + name + "\":";
        rawPrefixes[index] = (int) pack(literal, 0, 4);
        rawSuffixes[index] = pack(literal, 4, Math.min(8, literal.length() - 4));
        rawLengths[index] = (byte) length;
    }

    private static long pack(String str, int from, int count) {
        long value = 0;
        for (int i = 0; i < count; i++) {
            value |= (long) str.charAt(from + i) << (8 * i);
        }
        return value;
    }

    boolean matchesExpected(long hash, int index) {
        return index >= 0 && index < expectedHashes.length
                && expectedHashes[index] == hash;
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
