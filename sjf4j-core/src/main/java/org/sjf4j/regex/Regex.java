package org.sjf4j.regex;

/**
 * A compiled regular expression. Implementations must support concurrent
 * matching without sharing mutable match state.
 */
public interface Regex {

    /** Tests whether the entire input matches the expression. */
    boolean matches(String input);

    /** Tests whether any substring of the input matches the expression. */
    boolean find(String input);
}
