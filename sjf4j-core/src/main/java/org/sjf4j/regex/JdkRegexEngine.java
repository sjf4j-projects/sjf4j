package org.sjf4j.regex;

import java.util.regex.Pattern;

/**
 * Regular expression engine backed by the JDK's {@link Pattern}.
 * This engine does not perform additional risk checks; use
 * {@link #guardedBy(RegexGuard)} to apply a guard.
 */
public final class JdkRegexEngine implements RegexEngine {

    public static final JdkRegexEngine INSTANCE = new JdkRegexEngine();

    private JdkRegexEngine() {}

    @Override
    public Regex compile(String expression, int flags) {
        Pattern pattern = Pattern.compile(expression, flags);
        return new Regex() {
            @Override
            public boolean matches(String input) {
                return pattern.matcher(input).matches();
            }

            @Override
            public boolean find(String input) {
                return pattern.matcher(input).find();
            }
        };
    }
}
