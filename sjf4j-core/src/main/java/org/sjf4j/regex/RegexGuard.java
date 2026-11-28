package org.sjf4j.regex;

import org.sjf4j.exception.RegexRejectedException;

import java.util.regex.Pattern;

/**
 * A policy that checks regular expressions before compilation.
 * A rejection must throw {@link RegexRejectedException}.
 */
@FunctionalInterface
public interface RegexGuard {

    /**
     * A conservative, best-effort policy for JDK-style regular expressions.
     *
     * <p>Checks:
     * <ul>
     *   <li>Expression length: at most 4096 UTF-16 code units.</li>
     *   <li>Group nesting: at most 32 levels.</li>
     *   <li>Nested unbounded quantifiers: reject a group containing {@code *},
     *       {@code +} or {@code {n,}} when the group itself has an unbounded
     *       quantifier (e.g. {@code (a+)+}, {@code (a*)*}, {@code (a+){2,}}).</li>
     *   <li>Repetition counts: reject a numeric bound above 10000 in
     *       {@code {n}}, {@code {n,m}} or {@code {n,}}.</li>
     *   <li>Adjacent unbounded repetitions of the same simple atom:
     *       ASCII letters/digits, dot, or the same predefined character
     *       class escape (e.g. {@code a*a+}, {@code .*.*}, {@code \d+\d+}).
     *       Lazy repetitions are included; a possessive first repetition
     *       is excluded.</li>
     * </ul>
     *
     * <p>Recognized syntax:
     * <ul>
     *   <li>Escaped characters, including control, hex, Unicode and numeric
     *       escapes, and braced Unicode property/name escapes.</li>
     *   <li>Quoted literals ({@code \Q...\E}).</li>
     *   <li>Character classes, including negation, initial literal
     *       {@code ]}, and nested classes/intersections.</li>
     *   <li>{@link Pattern#LITERAL}: apply only the length limit.</li>
     *   <li>{@link Pattern#COMMENTS}: ignore ASCII whitespace and
     *       unescaped comments ending at CR or LF.</li>
     * </ul>
     *
     * <p>Limitations: overlapping but different atoms, repetitions across
     * groups, bounded repetition complexity, ambiguous alternation and inline
     * flag changes are not analyzed. Nested possessive repetitions may still
     * be rejected conservatively.
     * This is not a full regex parser or a guarantee against ReDoS; safe
     * expressions may also be rejected.
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
        boolean classNegationAllowed = false;
        boolean quoted = false;
        boolean closedRepeatedGroup = false;
        boolean comments = (flags & Pattern.COMMENTS) != 0;
        int atom = -1;
        int lastUnboundedAtom = -1;
        boolean quantified = false;
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
                if (!quantified) lastUnboundedAtom = -1;
                atom = -1;
                quantified = false;
                if (i + 1 < length) {
                    char escaped = expression.charAt(++i);
                    if (escaped == 'Q') quoted = true;
                    else if (escaped == 'c' && i + 1 < length) i++;
                    else if ("xpPN".indexOf(escaped) >= 0 && i + 1 < length
                            && expression.charAt(i + 1) == '{') {
                        // Braced escape payloads are not repetition counts.
                        int end = expression.indexOf('}', i + 2);
                        if (end >= 0) i = end;
                    } else if (escaped == 'x' || escaped == 'u') {
                        // Do not interpret hex escape digits as independent atoms.
                        i = Math.min(length - 1, i + (escaped == 'x' ? 2 : 4));
                    } else if (escaped >= '0' && escaped <= '9') {
                        while (i + 1 < length && expression.charAt(i + 1) >= '0'
                                && expression.charAt(i + 1) <= '9') i++;
                    } else if (classDepth == 0 && "dDsSwWhHvV".indexOf(escaped) >= 0) {
                        atom = 128 + escaped;
                    }
                }
                if (atom == -1) lastUnboundedAtom = -1;
                classFirst = false;
                classNegationAllowed = false;
                closedRepeatedGroup = false;
                continue;
            }
            if (c == '[') {
                atom = lastUnboundedAtom = -1;
                quantified = false;
                classDepth++;
                classFirst = true;
                classNegationAllowed = true;
                closedRepeatedGroup = false;
                continue;
            }
            if (classDepth > 0) {
                if (c == '^' && classNegationAllowed) {
                    classNegationAllowed = false;
                    continue;
                }
                if (c == ']' && !classFirst) classDepth--;
                classFirst = false;
                classNegationAllowed = false;
                continue;
            }
            if (c == '(') {
                atom = lastUnboundedAtom = -1;
                quantified = false;
                if (++depth > 32) {
                    throw new RegexRejectedException("Regex group nesting exceeds 32");
                }
                repeated[depth] = false;
            } else if (c == ')' && depth > 0) {
                atom = lastUnboundedAtom = -1;
                quantified = false;
                closedRepeatedGroup = repeated[depth--];
                repeated[depth] |= closedRepeatedGroup;
                continue;
            } else {
                if (quantified && (c == '?' || c == '+')) {
                    // A possessive first repetition cannot backtrack into the next atom.
                    if (c == '+') lastUnboundedAtom = -1;
                    continue;
                }
                boolean unbounded = c == '*' || c == '+';
                boolean quantifier = unbounded || c == '?';
                if (c == '{') {
                    int end = i + 1;
                    int count = 0;
                    boolean digits = false;
                    boolean comma = false;
                    boolean exceeds = false;
                    for (; end < length; end++) {
                        char q = expression.charAt(end);
                        if (q >= '0' && q <= '9') {
                            digits = true;
                            // Saturate to avoid integer overflow on large counts.
                            count = Math.min(10001, count * 10 + q - '0');
                            exceeds |= count > 10000;
                        } else if (q == ',' && digits && !comma) {
                            comma = true;
                            digits = false;
                            count = 0;
                        } else if (comments && " \t\n\r\f\u000B".indexOf(q) >= 0) {
                            continue;
                        } else if (comments && q == '#') {
                            while (end + 1 < length && expression.charAt(end + 1) != '\n'
                                    && expression.charAt(end + 1) != '\r') end++;
                        } else {
                            break;
                        }
                    }
                    if (end < length && expression.charAt(end) == '}' && (digits || comma)) {
                        if (exceeds) {
                            throw new RegexRejectedException("Regex repetition count exceeds 10000");
                        }
                        quantifier = true;
                        unbounded = comma && !digits;
                        i = end;
                    }
                }
                if (quantifier) {
                    if (unbounded) {
                        if (closedRepeatedGroup) {
                            throw new RegexRejectedException("Regex contains nested unbounded quantifiers");
                        }
                        if (atom != -1 && atom == lastUnboundedAtom) {
                            throw new RegexRejectedException("Regex contains adjacent unbounded repetitions of the same atom");
                        }
                        repeated[depth] = true;
                    }
                    lastUnboundedAtom = unbounded ? atom : -1;
                    quantified = true;
                } else {
                    if (!quantified) lastUnboundedAtom = -1;
                    // Only compare identical, simple atoms; no character-set analysis.
                    atom = c == '.' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
                            || c >= '0' && c <= '9' ? c : -1;
                    if (atom == -1) lastUnboundedAtom = -1;
                    quantified = false;
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
