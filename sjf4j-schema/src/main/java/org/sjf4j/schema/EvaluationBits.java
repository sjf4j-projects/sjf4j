package org.sjf4j.schema;

import java.util.BitSet;

/**
 * Evaluated-location annotations optimized for the common <=64 member case.
 * Overflow stores indices relative to 64 and is allocated only when needed.
 */
final class EvaluationBits {
    private long mask;
    private BitSet overflow;

    boolean get(int index) {
        return index < 64
                ? (mask & (1L << index)) != 0
                : overflow != null && overflow.get(index - 64);
    }

    void set(int index) {
        if (index < 64) {
            mask |= 1L << index;
        } else {
            if (overflow == null) overflow = new BitSet();
            overflow.set(index - 64);
        }
    }

    void set(int from, int to) {
        if (to <= from) return;
        if (from < 64) {
            int end = Math.min(64, to);
            long lo = -1L << from;
            long hi = end == 64 ? -1L : (1L << end) - 1;
            mask |= lo & hi;
        }
        if (to > 64) {
            if (overflow == null) overflow = new BitSet();
            overflow.set(Math.max(from, 64) - 64, to - 64);
        }
    }

    void or(EvaluationBits other) {
        mask |= other.mask;
        if (other.overflow != null) {
            if (overflow == null) overflow = new BitSet();
            overflow.or(other.overflow);
        }
    }
}
