package org.sjf4j.regex;

import org.sjf4j.exception.RegexRejectedException;

/**
 * A policy that checks regular expressions before compilation.
 * A rejection must throw {@link RegexRejectedException}.
 */
@FunctionalInterface
public interface RegexGuard {

    /** A policy that performs no checks. */
    RegexGuard NONE = (expression, flags) -> {};

    /**
     * Checks the expression and flags before compilation.
     * Flags use the bit values defined by {@link java.util.regex.Pattern}.
     * @throws RegexRejectedException if the expression is rejected by this policy
     */
    void check(String expression, int flags);
}
