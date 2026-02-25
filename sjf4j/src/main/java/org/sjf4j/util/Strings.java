package org.sjf4j.util;

/**
 * String utility helpers.
 */
public class Strings {

    /**
     * Returns the string when non-null and non-empty after trim.
     */
    public static String requireNonEmpty(String string, String message) {
        if (string == null)
            throw new NullPointerException(message);
        if (string.trim().isEmpty())
            throw new IllegalArgumentException(message);
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
     * Truncates in the middle with default ellipsis.
     */
    public static String truncateMiddle(String str) {
        return truncateMiddle(str, 43, "...");
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
