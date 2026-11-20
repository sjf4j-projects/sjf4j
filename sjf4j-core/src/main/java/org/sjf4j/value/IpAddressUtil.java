package org.sjf4j.value;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

/** Parses numeric IP literals without hostname resolution. */
final class IpAddressUtil {
    private IpAddressUtil() {}

    static InetAddress parseLiteral(String raw) {
        if (raw.isEmpty() || raw.length() > 256) throw invalid();
        boolean bracketed = raw.charAt(0) == '[';
        String text = raw;
        if (bracketed) {
            if (!raw.endsWith("]")) throw invalid();
            text = raw.substring(1, raw.length() - 1);
        } else if (raw.indexOf('[') >= 0 || raw.indexOf(']') >= 0) {
            throw invalid();
        }

        try {
            if (text.indexOf(':') < 0) {
                if (bracketed) throw invalid();
                byte[] bytes = parseIpv4(text);
                if (bytes == null) throw invalid();
                return InetAddress.getByAddress(bytes);
            }

            String scope = null;
            int percent = text.indexOf('%');
            if (percent >= 0) {
                scope = text.substring(percent + 1);
                text = text.substring(0, percent);
                if (scope.isEmpty() || scope.indexOf('%') >= 0) throw invalid();
            }
            byte[] bytes = parseIpv6(text);
            if (bytes == null) throw invalid();
            if (scope == null) return InetAddress.getByAddress(bytes);

            int scopeId = 0;
            boolean numeric = true;
            for (int i = 0; i < scope.length(); i++) {
                char c = scope.charAt(i);
                if (c < '0' || c > '9') {
                    numeric = false;
                    break;
                }
                if (scopeId > (Integer.MAX_VALUE - (c - '0')) / 10) throw invalid();
                scopeId = scopeId * 10 + c - '0';
            }
            if (numeric) return Inet6Address.getByAddress(null, bytes, scopeId);
            // Interface scope names are resolved locally, never through DNS.
            NetworkInterface networkInterface = NetworkInterface.getByName(scope);
            if (networkInterface == null) throw invalid();
            return Inet6Address.getByAddress(null, bytes, networkInterface);
        } catch (UnknownHostException | SocketException e) {
            throw new IllegalArgumentException("invalid IP literal", e);
        }
    }

    private static byte[] parseIpv4(String text) {
        byte[] bytes = new byte[4];
        int part = 0;
        int number = 0;
        int digits = 0;
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : '.';
            if (c == '.') {
                if (digits == 0 || part == 4) return null;
                bytes[part++] = (byte) number;
                number = digits = 0;
            } else {
                if (c < '0' || c > '9' || digits == 3 || (digits == 1 && number == 0)) return null;
                number = number * 10 + c - '0';
                if (number > 255) return null;
                digits++;
            }
        }
        return part == 4 ? bytes : null;
    }

    private static byte[] parseIpv6(String text) {
        int gap = text.indexOf("::");
        if (gap >= 0 && text.indexOf("::", gap + 2) >= 0) return null;
        int[] words = new int[8];
        if (gap < 0) {
            if (parseGroups(text, words, 0) != 8) return null;
        } else {
            int left = gap == 0 ? 0 : parseGroups(text.substring(0, gap), words, 0);
            if (left < 0) return null;
            int[] rightWords = new int[8];
            int right = gap + 2 == text.length() ? 0
                    : parseGroups(text.substring(gap + 2), rightWords, 0);
            if (right < 0 || left + right >= 8) return null;
            System.arraycopy(rightWords, 0, words, 8 - right, right);
        }
        byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            bytes[2 * i] = (byte) (words[i] >>> 8);
            bytes[2 * i + 1] = (byte) words[i];
        }
        return bytes;
    }

    private static int parseGroups(String text, int[] words, int offset) {
        String[] groups = text.split(":", -1);
        for (int i = 0; i < groups.length; i++) {
            String group = groups[i];
            if (group.indexOf('.') >= 0) {
                if (i != groups.length - 1 || offset > 6) return -1;
                byte[] ipv4 = parseIpv4(group);
                if (ipv4 == null) return -1;
                words[offset++] = (ipv4[0] & 255) << 8 | (ipv4[1] & 255);
                words[offset++] = (ipv4[2] & 255) << 8 | (ipv4[3] & 255);
            } else {
                if (group.isEmpty() || group.length() > 4 || offset >= 8) return -1;
                int value = 0;
                for (int j = 0; j < group.length(); j++) {
                    char c = group.charAt(j);
                    int digit = c >= '0' && c <= '9' ? c - '0'
                            : c >= 'a' && c <= 'f' ? c - 'a' + 10
                            : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
                    if (digit < 0) return -1;
                    value = (value << 4) | digit;
                }
                words[offset++] = value;
            }
        }
        return offset;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid IP literal");
    }
}
