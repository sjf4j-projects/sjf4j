package org.sjf4j.value;

import org.sjf4j.exception.BindingException;
import org.sjf4j.util.Formats;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Utilities for built-in value types.
 */
public final class ValueUtil {

    private ValueUtil() {}

    /**
     * Parses a numeric IPv4 or IPv6 literal without performing hostname resolution.
     * IPv6 brackets and scope IDs are accepted here for Java address compatibility,
     * but are not part of the strict literal grammar in {@link Formats}.
     *
     * @throws BindingException if the input is not a valid IP address literal
     */
    public static InetAddress parseInetAddress(String raw) {
        try {
            if (raw == null || raw.isEmpty() || raw.length() > 128) throw new IllegalArgumentException();
            if (raw.indexOf(':') < 0) return InetAddress.getByAddress(Formats.parseIpv4(raw));

            String literal = raw;
            if (raw.charAt(0) == '[') {
                if (raw.charAt(raw.length() - 1) != ']') throw new IllegalArgumentException();
                literal = raw.substring(1, raw.length() - 1);
            }
            int percent = literal.indexOf('%');
            String scope = null;
            if (percent >= 0) {
                scope = literal.substring(percent + 1);
                if (scope.isEmpty() || scope.indexOf('%') >= 0) throw new IllegalArgumentException();
                literal = literal.substring(0, percent);
            }
            byte[] bytes = Formats.parseIpv6(literal);
            // Preserve JDK behavior for IPv4-mapped IPv6 addresses.
            InetAddress address = InetAddress.getByAddress(bytes);
            if (scope == null) return address;
            if (!(address instanceof Inet6Address)) throw new IllegalArgumentException();

            boolean numeric = true;
            for (int i = 0; i < scope.length(); i++) {
                char ch = scope.charAt(i);
                if (ch < '0' || ch > '9') {
                    numeric = false;
                    break;
                }
            }
            if (numeric) return Inet6Address.getByAddress(null, bytes, Integer.parseInt(scope));
            NetworkInterface network = NetworkInterface.getByName(scope);
            if (network == null) throw new IllegalArgumentException();
            return Inet6Address.getByAddress(null, bytes, network);
        } catch (UnknownHostException | SocketException | IllegalArgumentException e) {
            throw new BindingException("invalid InetAddress '" + raw + "'", e);
        }
    }
}
