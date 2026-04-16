package org.sjf4j.util;

import org.sjf4j.exception.JsonException;

/**
 * String utility helpers.
 */
public class Strings {

    /**
     * Converts camel/pascal case text to snake_case.
     */
    public static String toSnakeCase(String str) {
        if (str == null || str.isEmpty()) return str;

        int prefix = 0;
        while (prefix < str.length() && str.charAt(prefix) == '_') {
            prefix++;
        }
        if (prefix == str.length()) return str;

        boolean changed = false;
        for (int i = prefix; i < str.length(); i++) {
            if (Character.isUpperCase(str.charAt(i))) {
                changed = true;
                break;
            }
        }
        if (!changed) return str;

        StringBuilder sb = new StringBuilder(str.length() + 8);
        for (int i = 0; i < prefix; i++) {
            sb.append('_');
        }

        for (int i = prefix; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > prefix) {
                    char prev = str.charAt(i - 1);
                    boolean prevLowerOrDigit = Character.isLowerCase(prev) || Character.isDigit(prev);
                    boolean prevUpper = Character.isUpperCase(prev);
                    boolean nextLower = i + 1 < str.length() && Character.isLowerCase(str.charAt(i + 1));
                    if (prevLowerOrDigit || (prevUpper && nextLower)) {
                        sb.append('_');
                    }
                }
                sb.append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Returns the string when non-null and non-empty after trim.
     */
    public static String requireNonEmpty(String string, String message) {
        if (string == null)
            throw new JsonException(message);
        if (string.trim().isEmpty())
            throw new JsonException(message);
        return string;
    }

    /**
     * Truncates to default length using trailing ellipsis.
     */
    public static String truncate(String str) {
        return truncate(str, 43, "...");
    }

    /**
     * Truncates to max length using trailing ellipsis.
     */
    public static String truncate(String str, int maxLen) {
        return truncate(str, maxLen, "...");
    }

    /**
     * Truncates to max length using a custom trailing marker.
     */
    public static String truncate(String str, int maxLen, String ellipsis) {
        if (str == null) return null;
        if (maxLen <= 0) return "";
        if (str.length() <= maxLen) return str;

        if (ellipsis == null) ellipsis = "";
        if (ellipsis.length() >= maxLen) {
            return ellipsis.substring(0, maxLen);
        }

        return str.substring(0, maxLen - ellipsis.length()) + ellipsis;
    }

    /**
     * Truncates in the middle to max length.
     */
    public static String truncateMiddle(String str, int maxLen) {
        return truncateMiddle(str, maxLen, "...");
    }

    /**
     * Truncates in the middle using a custom marker.
     */
    public static String truncateMiddle(String str, int maxLen, String ellipsis) {
        if (str == null) return null;
        if (str.length() <= maxLen) return str;
        if (ellipsis == null) ellipsis = "...";
        int keep = maxLen - ellipsis.length();
        int startLen = keep / 2;
        int endLen = keep - startLen;
        return str.substring(0, startLen) + ellipsis + str.substring(str.length() - endLen);
    }


}
