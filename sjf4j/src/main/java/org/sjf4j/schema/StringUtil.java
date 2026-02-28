package org.sjf4j.schema;

import com.ibm.icu.text.BreakIterator;

import java.util.Locale;

/**
 * String length helpers for JSON Schema length keywords.
 * <p>
 * Uses grapheme-cluster counting when ICU4J is available; otherwise falls back
 * to Unicode code-point counting.
 */
public class StringUtil {

    /// Length of Unicode grapheme clusters

    /**
     * Returns the length of the given string.
     *
     * <p>If ICU4J is available on the classpath, the length is evaluated
     * using Unicode grapheme clusters (UAX #29). Otherwise, the length
     * is evaluated in Unicode code points.</p>
     */
    public static int length(String s) {
        if (s == null || s.isEmpty()) return 0;
        return COUNTER.count(s);
    }

    @FunctionalInterface
    private interface Counter {
        /**
         * Counts visible text units in the given string.
         */
        int count(String s);
    }

    private static final Counter COUNTER = init();
    /**
     * Initializes the best available string length counter.
     */
    private static Counter init() {
        try {
            Class.forName("com.ibm.icu.text.BreakIterator");
            return IcuCounter::count;
        } catch (ClassNotFoundException e) {
            // fallback: code point
            return s -> s.codePointCount(0, s.length());
        }
    }


    /// ICU

    private static final class IcuCounter {

        private static final ThreadLocal<BreakIterator> TL =
                ThreadLocal.withInitial(() -> BreakIterator.getCharacterInstance(Locale.ROOT));

        /**
         * Counts grapheme clusters using ICU BreakIterator.
         */
        static int count(String s) {
            BreakIterator it = TL.get();
            it.setText(s);

            int count = 0;
            int i = it.first();
            for (int j = it.next();
                 j != BreakIterator.DONE;
                 i = j, j = it.next()) {
                count++;
            }
            return count;
        }

        private IcuCounter() {}
    }


}
