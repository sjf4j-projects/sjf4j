package org.sjf4j.util;

import org.sjf4j.JsonException;


/**
 * Implement of I-Regexp
 * See: https://datatracker.ietf.org/doc/html/draft-ietf-jsonpath-iregexp-08
 */
public class IRegexpUtil {


    public static boolean match(String pattern, String input) {
        if (input == null) return false;
        if (input.length() != patternLength(pattern)) return false;
        return matchesAt(pattern, input, 0);
    }

    public static boolean search(String pattern, String input) {
        if (input == null) return false;

        int plen = patternLength(pattern);
        if (plen == 0) return true;
        if (input.length() < plen) return false;

        for (int i = 0; i <= input.length() - plen; i++) {
            if (matchesAt(pattern, input, i)) {
                return true;
            }
        }
        return false;
    }

    /// Private

    private static boolean matchesAt(String pattern, String input, int si) {
        int pi = 0;
        int start = si;

        while (pi < pattern.length()) {
            if (si >= input.length()) return false;

            char pc = pattern.charAt(pi);

            if (pc == '\\') {
                pi++;
                if (pi >= pattern.length()) return false;
                if (pattern.charAt(pi) != input.charAt(si)) return false;
                pi++; si++;
                continue;
            }

            if (pc == '.') {
                pi++; si++;
                continue;
            }

            if (pc == '[') {
                int end = findClassEnd(pattern, pi);
                if (!matchClass(pattern, pi + 1, end, input.charAt(si))) {
                    return false;
                }
                pi = end + 1;
                si++;
                continue;
            }

            if (pc != input.charAt(si)) return false;

            pi++; si++;
        }

        return true;
    }

    private static int findClassEnd(String p, int start) {
        for (int i = start + 1; i < p.length(); i++) {
            if (p.charAt(i) == ']') return i;
        }
        throw new JsonException("Unclosed character class '['");
    }

    private static boolean matchClass(String p, int from, int to, char ch) {
        for (int i = from; i < to; i++) {
            char c = p.charAt(i);
            if (c == '\\') {
                i++;
                if (i >= to) throw new JsonException("Invalid escape in []");
                if (p.charAt(i) == ch) return true;
            } else if (i + 2 < to && p.charAt(i + 1) == '-') {
                char lo = c;
                char hi = p.charAt(i + 2);
                if (ch >= lo && ch <= hi) return true;
                i += 2;
            } else if (c == ch) {
                return true;
            }
        }
        return false;
    }

    private static int patternLength(String pattern) {
        int len = 0;
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                i += 2;
                len++;
            } else if (c == '[') {
                i++;
                while (i < pattern.length() && pattern.charAt(i) != ']') {
                    if (pattern.charAt(i) == '\\') i++;
                    i++;
                }
                if (i < pattern.length() && pattern.charAt(i) == ']') i++;
                len++;
            } else {
                i++;
                len++;
            }
        }
        return len;
    }

}
