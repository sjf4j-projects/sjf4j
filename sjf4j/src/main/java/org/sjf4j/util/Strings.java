package org.sjf4j.util;

public class Strings {

    public static String requireNonEmpty(String string, String message) {
        if (string == null)
            throw new NullPointerException(message);
        if (string.trim().length() == 0)
            throw new IllegalArgumentException(message);
        return string;
    }

    public static String truncate(String str) {
        return truncate(str, 43, "...");
    }

    public static String truncate(String str, int maxLen) {
        return truncate(str, maxLen, "...");
    }

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

    public static String truncateMiddle(String str) {
        return truncateMiddle(str, 43, "...");
    }

    public static String truncateMiddle(String str, int maxLen) {
        return truncateMiddle(str, maxLen, "...");
    }

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
