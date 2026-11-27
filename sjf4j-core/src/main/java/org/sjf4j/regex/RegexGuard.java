package org.sjf4j.regex;

/**
 * A policy that checks regular expressions before compilation.
 * A rejection must throw an exception rather than return a match result.
 */
@FunctionalInterface
public interface RegexGuard {

    /** A policy that performs no checks. */
    RegexGuard NONE = (expression, flags) -> {};

    /**
     * Checks the expression and flags before compilation.
     * Flags use the bit values defined by {@link java.util.regex.Pattern}.
     */
    void check(String expression, int flags);
}
