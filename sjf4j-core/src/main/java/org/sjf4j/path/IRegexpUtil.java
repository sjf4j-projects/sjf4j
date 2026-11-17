package org.sjf4j.path;

import org.sjf4j.exception.NodeException;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Java regular-expression matching for JSONPath {@code match()} and {@code search()}.
 *
 * <p>Patterns use JDK {@link Pattern} syntax and semantics rather than strict
 * RFC 9485 I-Regexp semantics. JDK regexes use backtracking, so matching
 * untrusted patterns may consume excessive CPU time.</p>
 */
public final class IRegexpUtil {
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
        if (pattern == null) throw new NodeException("regex pattern must not be null");
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new NodeException("invalid regular expression '" + pattern + "'", e);
        }
    }
}
