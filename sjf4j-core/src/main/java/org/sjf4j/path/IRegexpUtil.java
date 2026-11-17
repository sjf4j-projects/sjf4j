package org.sjf4j.path;

import org.sjf4j.exception.NodeException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * I-Regexp matching (RFC 9485) backed by the JDK regular expression engine.
 *
 * <p>RFC-compatible patterns use I-Regexp semantics: an unescaped dot matches
 * any Unicode code point except CR and LF, while unescaped '^' and '$' are
 * literal characters. Java regular expression extensions are also accepted;
 * this is not a strict RFC 9485 syntax checker.</p>
 *
 * <p>Compiled patterns are cached in a bounded LRU map. As with other
 * backtracking engines, untrusted patterns may cause excessive CPU usage.</p>
 */
public final class IRegexpUtil {
    private static final int CACHE_LIMIT = 256;

    private static final Map<String, Pattern> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Pattern>(CACHE_LIMIT, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
                    return size() > CACHE_LIMIT;
                }
            });

    private IRegexpUtil() {
    }

    /** Returns true when the entire input matches the expression. */
    public static boolean match(String pattern, String input) {
        return input != null && compile(pattern).matcher(input).matches();
    }

    /** Returns true when any substring of the input matches the expression. */
    public static boolean search(String pattern, String input) {
        return input != null && compile(pattern).matcher(input).find();
    }

    private static Pattern compile(String pattern) {
        if (pattern == null) throw new NodeException("I-Regexp pattern must not be null");
        Pattern compiled = CACHE.get(pattern);
        if (compiled != null) return compiled;

        try {
            compiled = Pattern.compile(toJavaPattern(pattern));
        } catch (PatternSyntaxException e) {
            throw new NodeException("invalid regular expression '" + pattern + "'", e);
        }

        synchronized (CACHE) {
            Pattern cached = CACHE.get(pattern);
            if (cached != null) return cached;
            CACHE.put(pattern, compiled);
        }
        return compiled;
    }

    /**
     * Converts only the constructs where I-Regexp and Java Pattern disagree.
     * Escaped characters, quoted spans, and character classes must retain
     * their meaning; blind String.replace calls would break those contexts.
     */
    private static String toJavaPattern(String pattern) {
        StringBuilder out = new StringBuilder(pattern.length() + 12);
        int classDepth = 0;
        boolean inQuote = false;
        boolean nestedClass = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\' && i + 1 < pattern.length()) {
                char next = pattern.charAt(++i);
                if (next == 'Q' && !inQuote) inQuote = true;
                else if (next == 'E' && inQuote) inQuote = false;
                out.append(c).append(next);
                continue;
            }
            if (inQuote) {
                out.append(c);
                continue;
            }
            if (c == '[') {
                if (classDepth == 0) nestedClass = hasNestedClass(pattern, i);
                classDepth++;
                out.append(c);
            } else if (c == ']' && classDepth > 0) {
                classDepth--;
                out.append(c);
            } else if (classDepth == 0) {
                if (c == '.') out.append("[^\\r\\n]");
                else if (c == '^' || c == '$') out.append('\\').append(c);
                else out.append(c);
            } else if (c == '&' && !nestedClass) {
                // A pair of literal '&' characters is a Java class intersection.
                // For RFC classes, escape ampersands (including consecutive ones).
                out.append("\\&");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** A nested character class is a Java-only extension (typically [a-z&&[^aeiou]]). */
    private static boolean hasNestedClass(String pattern, int opening) {
        boolean inQuote = false;
        for (int i = opening + 1; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\' && i + 1 < pattern.length()) {
                char next = pattern.charAt(++i);
                if (next == 'Q' && !inQuote) inQuote = true;
                else if (next == 'E' && inQuote) inQuote = false;
                continue;
            }
            if (inQuote) continue;
            if (c == '[') return true;
            if (c == ']') return false;
        }
        return false;
    }
}
