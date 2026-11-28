package org.sjf4j.regex;

import org.sjf4j.exception.RegexRejectedException;

import java.util.regex.Pattern;

/**
 * A policy that checks regular expressions before compilation.
 * A rejection must throw {@link RegexRejectedException}.
 */
@FunctionalInterface
public interface RegexGuard {

    /** A policy that performs no checks. */
    RegexGuard NONE = (expression, flags) -> {};

    /**
     * A conservative, best-effort policy limiting expression length to 4096
     * UTF-16 code units, group nesting to 32 levels and rejecting obvious
     * nested unbounded quantifiers. This is not a full regex parser or a
     * guarantee against ReDoS; safe expressions may also be rejected.
     * Syntax validation remains the engine's responsibility.
     */
    RegexGuard BASIC = (expression, flags) -> {
        int length = expression.length();
        if (length > 4096) {
            throw new RegexRejectedException("Regex length exceeds 4096");
        }
        if ((flags & Pattern.LITERAL) != 0) return;

        boolean[] repeated = new boolean[33];
        int depth = 0;
        int classDepth = 0;
        boolean classFirst = false;
        boolean quoted = false;
        boolean closedRepeatedGroup = false;
        boolean comments = (flags & Pattern.COMMENTS) != 0;
        for (int i = 0; i < length; i++) {
            char c = expression.charAt(i);
            if (quoted) {
                if (c == '\\' && i + 1 < length && expression.charAt(i + 1) == 'E') {
                    quoted = false;
                    i++;
                }
                continue;
            }
            if (comments) {
                if (" \t\n\r\f\u000B".indexOf(c) >= 0) continue;
                if (c == '#') {
                    while (i + 1 < length && expression.charAt(i + 1) != '\n'
                            && expression.charAt(i + 1) != '\r') i++;
                    continue;
                }
            }
            if (c == '\\') {
                if (i + 1 < length) {
                    char escaped = expression.charAt(++i);
                    if (escaped == 'Q') quoted = true;
                    else if (escaped == 'c' && i + 1 < length) i++;
                }
                classFirst = false;
                closedRepeatedGroup = false;
                continue;
            }
            if (c == '[') {
                classDepth++;
                classFirst = true;
                closedRepeatedGroup = false;
                continue;
            }
            if (classDepth > 0) {
                if (c == ']' && !classFirst) classDepth--;
                if (c != '^' || !classFirst) classFirst = false;
                continue;
            }
            if (c == '(') {
                if (++depth > 32) {
                    throw new RegexRejectedException("Regex group nesting exceeds 32");
                }
                repeated[depth] = false;
            } else if (c == ')' && depth > 0) {
                closedRepeatedGroup = repeated[depth--];
                repeated[depth] |= closedRepeatedGroup;
                continue;
            } else {
                boolean unbounded = c == '*' || c == '+';
                if (c == '{') {
                    int end = i + 1;
                    while (end < length && expression.charAt(end) >= '0'
                            && expression.charAt(end) <= '9') end++;
                    if (end > i + 1 && end + 1 < length
                            && expression.charAt(end) == ',' && expression.charAt(end + 1) == '}') {
                        unbounded = true;
                        i = end + 1;
                    }
                }
                if (unbounded) {
                    if (closedRepeatedGroup) {
                        throw new RegexRejectedException("Regex contains nested unbounded quantifiers");
                    }
                    repeated[depth] = true;
                }
            }
            closedRepeatedGroup = false;
        }
    };

    /**
     * Checks the expression and flags before compilation.
     * Flags use the bit values defined by {@link java.util.regex.Pattern}.
     * @throws RegexRejectedException if the expression is rejected by this policy
     */
    void check(String expression, int flags);
}
