package org.sjf4j.regex;

import org.sjf4j.exception.RegexRejectedException;

/**
 * A compiled regular expression. Implementations must support concurrent
 * matching without sharing mutable match state.
 */
public interface Regex {

    /**
     * Tests whether the entire input matches the expression.
     * @throws RegexRejectedException if a matching safety limit is exceeded
     */
    boolean matches(String input);

    /**
     * Tests whether any substring of the input matches the expression.
     * @throws RegexRejectedException if a matching safety limit is exceeded
     */
    boolean find(String input);
}
