package org.sjf4j.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatsTest {

    @Test
    void ipv4ValidationAndParsingAgree() {
        assertArrayEquals(new byte[]{0, 1, (byte) 128, (byte) 255}, Formats.parseIpv4("0.1.128.255"));
        for (String value : new String[]{"0.0.0.0", "255.255.255.255", "192.168.0.1"}) {
            assertTrue(Formats.isIpv4(value), value);
            assertTrue(Formats.parseIpv4(value).length == 4, value);
        }
        for (String value : new String[]{null, "", "127.1", "01.2.3.4", "1.2.3.4.",
                "256.0.0.1", "1.2.3.4.5", "1.2.3.-4", "1.2.3.৪", " 1.2.3.4"}) {
            assertFalse(Formats.isIpv4(value), value);
            assertThrows(IllegalArgumentException.class, () -> Formats.parseIpv4(value), value);
        }
    }

    @Test
    void ipv6ValidationAndParsingAgree() {
        byte[] loopback = new byte[16];
        loopback[15] = 1;
        assertArrayEquals(new byte[16], Formats.parseIpv6("::"));
        assertArrayEquals(loopback, Formats.parseIpv6("::1"));
        assertArrayEquals(loopback, Formats.parseIpv6("0:0:0:0:0:0:0:1"));
        assertArrayEquals(new byte[]{0x20, 1, 0x0d, (byte) 0xb8, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 1}, Formats.parseIpv6("2001:DB8::1"));
        assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, (byte) 255, (byte) 255, (byte) 192, (byte) 168, 0, 1},
                Formats.parseIpv6("::ffff:192.168.0.1"));
        assertArrayEquals(new byte[]{0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 2, 0, 3}, Formats.parseIpv6("1::2:3"));
        for (String value : new String[]{"::", "::1", "1::", "1::2:3",
                "1:2:3:4:5:6:7:8", "1:2:3:4:5:6:192.168.0.1",
                "::1:2:3:4:5:192.168.0.1", "ffff:ffff:ffff:ffff:ffff:ffff:255.255.255.255"}) {
            assertTrue(Formats.isIpv6(value), value);
            assertTrue(Formats.parseIpv6(value).length == 16, value);
        }
        for (String value : new String[]{null, "", "1", "1:", ":1", ":::1", "1::2::3",
                "1:2:3:4:5:6:7", "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7:8::",
                "1:2:3:4:5:6:7::8", "::12345", "::g", "::４", "127.0.0.1",
                "[::1]", "fe80::1%3", "fe80::/64", "::1\n", "::ffff:192.168.0.01",
                "::ffff:192.168.0.256", "1:2:3:4:5:6:7:192.168.0.1", "1.2.3.4::",
                "::1.2.3.4:5", "::ffff:1.2.3", "::ffff:1.2.3.4."}) {
            assertFalse(Formats.isIpv6(value), value);
            assertThrows(IllegalArgumentException.class, () -> Formats.parseIpv6(value), value);
        }
    }
}
