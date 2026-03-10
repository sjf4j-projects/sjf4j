package org.sjf4j.schema;

import com.ibm.icu.text.BreakIterator;
import org.sjf4j.exception.SchemaException;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * String length helpers for JSON Schema length keywords.
 * <p>
 * Uses grapheme-cluster counting when ICU4J is available; otherwise falls back
 * to Unicode code-point counting.
 */
public class EvaluateUtil {

    /// Length of Unicode grapheme clusters

    /**
     * Returns the length of the given string.
     *
     * <p>If ICU4J is available on the classpath, the length is evaluated
     * using Unicode grapheme clusters (UAX #29). Otherwise, the length
     * is evaluated in Unicode code points.</p>
     */
    public static int stringIcuLength(String s) {
        if (s == null || s.isEmpty()) return 0;
        return COUNTER.count(s);
    }

    @FunctionalInterface
    private interface Counter {
        /**
         * Counts visible text units in the given string.
         */
        int count(String s);
    }

    private static final Counter COUNTER = init();
    /**
     * Initializes the best available string length counter.
     */
    private static Counter init() {
        try {
            Class.forName("com.ibm.icu.text.BreakIterator");
            return IcuCounter::count;
        } catch (ClassNotFoundException e) {
            // fallback: code point
            return s -> s.codePointCount(0, s.length());
        }
    }

    // ICU
    private static final class IcuCounter {

        private static final ThreadLocal<BreakIterator> TL =
                ThreadLocal.withInitial(() -> BreakIterator.getCharacterInstance(Locale.ROOT));

        /**
         * Counts grapheme clusters using ICU BreakIterator.
         */
        static int count(String s) {
            BreakIterator it = TL.get();
            it.setText(s);

            int count = 0;
            int i = it.first();
            for (int j = it.next();
                 j != BreakIterator.DONE;
                 i = j, j = it.next()) {
                count++;
            }
            return count;
        }

        private IcuCounter() {}
    }

    /// Regex Pattern

    private static final Pattern UNICODE_PROPERTY_PATTERN = Pattern.compile("\\\\([pP])\\{([^}]+)}");

    public static Pattern compileRegexPattern(String pattern, String keyword) {
        Objects.requireNonNull(pattern, "pattern is null");
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException ignore) {
            String normalized = normalizeUnicodeProperties(pattern);
            try {
                return Pattern.compile(normalized);
            } catch (PatternSyntaxException e) {
                throw new SchemaException("Invalid regex for keyword '" + keyword + "': " + pattern, e);
            }
        }
    }

    public static String normalizeUnicodeProperties(String pattern) {
        Matcher matcher = UNICODE_PROPERTY_PATTERN.matcher(pattern);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String pOrP = matcher.group(1);
            String prop = matcher.group(2);
            String normalized = normalizeUnicodePropertyName(prop);
            String replacement = "\\" + pOrP + "{" + normalized + "}";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String normalizeUnicodePropertyName(String prop) {
        if (prop == null || prop.isEmpty()) return prop;
        if (prop.length() <= 3) return prop;
        switch (prop) {
            case "Letter": return "L";
            case "Lowercase_Letter": return "Ll";
            case "Uppercase_Letter": return "Lu";
            case "Titlecase_Letter": return "Lt";
            case "Modifier_Letter": return "Lm";
            case "Other_Letter": return "Lo";
            case "Number": return "N";
            case "Decimal_Number": return "Nd";
            case "Letter_Number": return "Nl";
            case "Other_Number": return "No";
            case "Mark": return "M";
            case "Nonspacing_Mark": return "Mn";
            case "Spacing_Mark": return "Mc";
            case "Enclosing_Mark": return "Me";
            case "Separator": return "Z";
            case "Space_Separator": return "Zs";
            case "Line_Separator": return "Zl";
            case "Paragraph_Separator": return "Zp";
            case "Punctuation": return "P";
            case "Connector_Punctuation": return "Pc";
            case "Dash_Punctuation": return "Pd";
            case "Open_Punctuation": return "Ps";
            case "Close_Punctuation": return "Pe";
            case "Initial_Punctuation": return "Pi";
            case "Final_Punctuation": return "Pf";
            case "Other_Punctuation": return "Po";
            case "Symbol": return "S";
            case "Math_Symbol": return "Sm";
            case "Currency_Symbol": return "Sc";
            case "Modifier_Symbol": return "Sk";
            case "Other_Symbol": return "So";
            case "Other": return "C";
            case "Control": return "Cc";
            case "Format": return "Cf";
            case "Private_Use": return "Co";
            case "Surrogate": return "Cs";
            case "Unassigned": return "Cn";
            default:
                return prop;
        }
    }


}
