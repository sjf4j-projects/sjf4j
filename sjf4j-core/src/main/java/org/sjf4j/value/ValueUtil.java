package org.sjf4j.value;

import org.sjf4j.exception.BindingException;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utilities for built-in value types.
 */
public final class ValueUtil {

    private ValueUtil() {}

    /**
     * Parses a numeric IPv4 or IPv6 literal without performing hostname resolution.
     *
     * @throws BindingException if the input is not a valid IP address literal
     */
    public static InetAddress parseInetAddress(String raw) {
        try {
            if (raw.isEmpty() || raw.length() > 128) throw new IllegalArgumentException();
            if (raw.indexOf(':') >= 0) {
                int start = raw.charAt(0) == '[' ? 1 : 0;
                char first = raw.charAt(start);
                if (first != ':' && !(first >= '0' && first <= '9')
                        && !(first >= 'a' && first <= 'f')
                        && !(first >= 'A' && first <= 'F')) {
                    throw new IllegalArgumentException();
                }
                // JDK 8+ parses colon-containing IP literals or rejects them without DNS.
                return InetAddress.getByName(raw);
            }
            String[] parts = raw.split("\\.", -1);
            if (parts.length != 4) throw new IllegalArgumentException();
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                String part = parts[i];
                if (part.isEmpty() || part.length() > 3
                        || (part.length() > 1 && part.charAt(0) == '0')) {
                    throw new IllegalArgumentException();
                }
                int value = 0;
                for (int j = 0; j < part.length(); j++) {
                    char c = part.charAt(j);
                    if (c < '0' || c > '9') throw new IllegalArgumentException();
                    value = value * 10 + c - '0';
                }
                if (value > 255) throw new IllegalArgumentException();
                bytes[i] = (byte) value;
            }
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException | IllegalArgumentException e) {
            throw new BindingException("invalid InetAddress '" + raw + "'", e);
        }
    }
}
