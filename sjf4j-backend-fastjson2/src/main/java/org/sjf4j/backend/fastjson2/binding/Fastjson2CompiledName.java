package org.sjf4j.backend.fastjson2.binding;

import org.sjf4j.binding.CompiledName;

/** Pre-escaped Fastjson2 object property name for its raw-name writer path. */
final class Fastjson2CompiledName extends CompiledName {

    final char[] rawName;

    Fastjson2CompiledName(String name) {
        super(name);
        int length = name.length();
        int escapedLength = length;
        for (int i = 0; i < length; i++) {
            char ch = name.charAt(i);
            if (ch == '"' || ch == '\\' || ch == '\b' || ch == '\f' || ch == '\n' || ch == '\r' || ch == '\t') {
                escapedLength++;
            } else if (ch < ' ') {
                escapedLength += 5;
            }
        }

        rawName = new char[escapedLength + 3];
        rawName[0] = '"';
        int offset = 1;
        for (int i = 0; i < length; i++) {
            char ch = name.charAt(i);
            switch (ch) {
                case '"':
                case '\\':
                    rawName[offset++] = '\\';
                    rawName[offset++] = ch;
                    break;
                case '\b':
                    rawName[offset++] = '\\';
                    rawName[offset++] = 'b';
                    break;
                case '\f':
                    rawName[offset++] = '\\';
                    rawName[offset++] = 'f';
                    break;
                case '\n':
                    rawName[offset++] = '\\';
                    rawName[offset++] = 'n';
                    break;
                case '\r':
                    rawName[offset++] = '\\';
                    rawName[offset++] = 'r';
                    break;
                case '\t':
                    rawName[offset++] = '\\';
                    rawName[offset++] = 't';
                    break;
                default:
                    if (ch < ' ') {
                        rawName[offset++] = '\\';
                        rawName[offset++] = 'u';
                        rawName[offset++] = '0';
                        rawName[offset++] = '0';
                        rawName[offset++] = Character.forDigit(ch >>> 4, 16);
                        rawName[offset++] = Character.forDigit(ch & 15, 16);
                    } else {
                        rawName[offset++] = ch;
                    }
                    break;
            }
        }
        rawName[offset++] = '"';
        rawName[offset] = ':';
    }
}
