package org.sjf4j.regex;

import java.util.Objects;

/**
 * A pluggable regular expression compiler.
 * Implementations must support concurrent compilation and must not silently
 * fall back to another engine for unsupported expressions or flags.
 */
@FunctionalInterface
public interface RegexEngine {

    /** The default engine, currently using JDK regex without a guard. */
    RegexEngine DEFAULT = new JdkRegexEngine();

    /**
     * Compiles an expression with flags defined by {@link java.util.regex.Pattern}.
     * External engines must translate supported flags and reject unsupported ones.
     *
     * @throws IllegalArgumentException if the expression or flags are unsupported
     */
    Regex compile(String expression, int flags);

    /** Compiles an expression without additional flags. */
    default Regex compile(String expression) {
        return compile(expression, 0);
    }

    /**
     * Returns an engine that checks expressions with the given guard before
     * delegating to this engine. Neither this engine nor the guard is modified.
     */
    default RegexEngine guardedBy(RegexGuard guard) {
        Objects.requireNonNull(guard, "guard");
        return (expression, flags) -> {
            guard.check(expression, flags);
            return compile(expression, flags);
        };
    }
}
