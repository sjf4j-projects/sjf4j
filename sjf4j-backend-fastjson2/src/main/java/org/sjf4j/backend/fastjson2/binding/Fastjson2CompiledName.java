package org.sjf4j.backend.fastjson2.binding;

import org.sjf4j.binding.CompiledName;

import java.nio.charset.StandardCharsets;

/** Pre-escaped Fastjson2 object property name for its raw-name writer path. */
final class Fastjson2CompiledName extends CompiledName {

    final char[] rawNameUtf16;
    final byte[] rawNameUtf8;


    Fastjson2CompiledName(String name) {
        super(name);

        int length = name.length();
        int escapedLength = length;

        for (int i = 0; i < length; i++) {
            char ch = name.charAt(i);
            if (ch == '"' || ch == '\\'
                    || ch == '\b' || ch == '\f'
                    || ch == '\n' || ch == '\r'
                    || ch == '\t') {
                escapedLength++;
            } else if (ch < ' ') {
                escapedLength += 5;
            }
        }

        char[] raw = new char[escapedLength + 3];
        raw[0] = '"';

        int offset = 1;
        for (int i = 0; i < length; i++) {
            char ch = name.charAt(i);

            switch (ch) {
                case '"':
                case '\\':
                    raw[offset++] = '\\';
                    raw[offset++] = ch;
                    break;
                case '\b':
                    raw[offset++] = '\\';
                    raw[offset++] = 'b';
                    break;
                case '\f':
                    raw[offset++] = '\\';
                    raw[offset++] = 'f';
                    break;
                case '\n':
                    raw[offset++] = '\\';
                    raw[offset++] = 'n';
                    break;
                case '\r':
                    raw[offset++] = '\\';
                    raw[offset++] = 'r';
                    break;
                case '\t':
                    raw[offset++] = '\\';
                    raw[offset++] = 't';
                    break;
                default:
                    if (ch < ' ') {
                        raw[offset++] = '\\';
                        raw[offset++] = 'u';
                        raw[offset++] = '0';
                        raw[offset++] = '0';
                        raw[offset++] = Character.forDigit(ch >>> 4, 16);
                        raw[offset++] = Character.forDigit(ch & 15, 16);
                    } else {
                        raw[offset++] = ch;
                    }
                    break;
            }
        }

        raw[offset++] = '"';
        raw[offset] = ':';

        this.rawNameUtf16 = raw;
        this.rawNameUtf8 = new String(raw).getBytes(StandardCharsets.UTF_8);
    }

}
