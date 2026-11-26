package org.sjf4j.util;

import java.util.Arrays;

/**
 * Lightweight, DNS-free validation and parsing of standard string formats.
 */
public final class Formats {

    private Formats() {}

    /**
     * Tests a strict dotted-decimal IPv4 literal (RFC 3986 dec-octet grammar).
     * Does not allocate an address buffer.
     */
    public static boolean isIpv4(String value) {
        return value != null && scanIpv4(value, 0, null, 0);
    }

    /**
     * Tests an IPv6 address literal, without brackets, scope IDs or CIDR suffixes.
     * Supports zero compression and an embedded IPv4 tail without allocating a buffer.
     */
    public static boolean isIpv6(String value) {
        return value != null && scanIpv6(value, null);
    }

    /**
     * Parses a strict IPv4 literal into four network-order bytes.
     *
     * @throws IllegalArgumentException if the input is not an IPv4 literal
     */
    public static byte[] parseIpv4(String value) {
        byte[] bytes = new byte[4];
        if (value == null || !scanIpv4(value, 0, bytes, 0)) {
            throw new IllegalArgumentException("Invalid IPv4 literal");
        }
        return bytes;
    }

    /**
     * Parses a strict IPv6 literal into sixteen network-order bytes, including
     * IPv4-mapped addresses, without resolving names or consulting interfaces.
     *
     * @throws IllegalArgumentException if the input is not an IPv6 literal
     */
    public static byte[] parseIpv6(String value) {
        byte[] bytes = new byte[16];
        if (value == null || !scanIpv6(value, bytes)) {
            throw new IllegalArgumentException("Invalid IPv6 literal");
        }
        return bytes;
    }

    private static boolean scanIpv4(String value, int start, byte[] bytes, int offset) {
        int len = value.length();
        for (int octet = 0; ; octet++) {
            if (start >= len) return false;
            int end = start;
            int number = 0;
            while (end < len) {
                char ch = value.charAt(end);
                if (ch < '0' || ch > '9') break;
                if (end - start == 3) return false;
                number = number * 10 + ch - '0';
                end++;
            }
            if (end == start || number > 255) return false;
            if (end - start > 1 && value.charAt(start) == '0') return false;
            if (bytes != null) bytes[offset + octet] = (byte) number;
            if (octet == 3) return end == len;
            if (end >= len || value.charAt(end) != '.') return false;
            start = end + 1;
        }
    }

    private static boolean scanIpv6(String value, byte[] bytes) {
        int len = value.length();
        if (len < 2 || len > 45) return false;
        int i = 0;
        int groups = 0;
        int compressed = -1;
        if (value.charAt(0) == ':') {
            if (value.charAt(1) != ':') return false;
            compressed = 0;
            i = 2;
        }
        while (i < len) {
            if (groups == 8) return false;
            int start = i;
            int number = 0;
            while (i < len) {
                int digit = hexDigit(value.charAt(i));
                if (digit < 0) break;
                if (i - start == 4) return false;
                number = (number << 4) | digit;
                i++;
            }
            if (i == start) return false;
            if (i < len && value.charAt(i) == '.') {
                // A dotted tail is legal only after a colon and occupies two groups.
                if (start == 0 || groups > 6 || !scanIpv4(value, start, bytes, groups * 2)) {
                    return false;
                }
                groups += 2;
                break;
            }
            if (bytes != null) {
                bytes[groups * 2] = (byte) (number >>> 8);
                bytes[groups * 2 + 1] = (byte) number;
            }
            groups++;
            if (i == len) break;
            if (value.charAt(i++) != ':') return false;
            if (i < len && value.charAt(i) == ':') {
                if (compressed >= 0) return false;
                compressed = groups;
                i++;
            } else if (i == len) {
                return false;
            }
        }
        if (compressed < 0) return groups == 8;
        // "::" must replace at least one group. Move the tail to the end.
        if (groups >= 8) return false;
        if (bytes != null) {
            int tail = (groups - compressed) * 2;
            int target = 16 - tail;
            System.arraycopy(bytes, compressed * 2, bytes, target, tail);
            Arrays.fill(bytes, compressed * 2, target, (byte) 0);
        }
        return true;
    }

    private static int hexDigit(char ch) {
        if (ch >= '0' && ch <= '9') return ch - '0';
        if (ch >= 'a' && ch <= 'f') return ch - 'a' + 10;
        if (ch >= 'A' && ch <= 'F') return ch - 'A' + 10;
        return -1;
    }
}
