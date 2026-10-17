package org.sjf4j.backend.fastjson2.binding;

import org.sjf4j.binding.CompiledName;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Pre-escaped Fastjson2 object property name for its raw-name writer path. */
public final class Fastjson2CompiledName extends CompiledName {

    /** Internal generated-code contract. Do not mutate these pre-escaped arrays. */
    public final char[] rawNameUtf16;
    /** Internal generated-code contract. Do not mutate these pre-escaped arrays. */
    public final byte[] rawNameUtf8;

    /** 2–16 for a plain ASCII field name eligible for native fixed-width writes; otherwise 0. */
    public final int rawNameLength;
    /** Native-endian first packed word for writeNameXRaw(). */
    public final long rawName0;
    /** Native-endian second word (or low 32-bit int for length 9). */
    public final long rawName1;

    /** Pre-escaped UTF-8 property token for compiled Fastjson2 UTF-8 writers. */
    public byte[] rawNameUtf8() {
        return rawNameUtf8;
    }

    /** Pre-escaped UTF-16 property token for native compiled String writers. */
    public char[] rawNameUtf16() {
        return rawNameUtf16;
    }


    /**
     * Prepares a property name for UTF-8 and UTF-16 raw-name writes.
     * @param name the property name
     */
    public Fastjson2CompiledName(String name) {
        super(name);

        int length = name.length();
        int escapedLength = length;
        boolean fixedRaw = length >= 2 && length <= 16;

        for (int i = 0; i < length; i++) {
            char ch = name.charAt(i);
            if (ch < ' ' || ch > '~' || ch == '"' || ch == '\\') fixedRaw = false;
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

        // Fastjson2 writes names of lengths 8 and 16 with quotes supplied
        // by the writer; the other fixed-width variants consume quoted bytes.
        this.rawNameLength = fixedRaw ? length : 0;
        if (fixedRaw) {
            int start = length == 8 || length == 16 ? 1 : 0;
            ByteBuffer packed = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder());
            packed.put(rawNameUtf8, start, Math.min(16, rawNameUtf8.length - start));
            this.rawName0 = packed.getLong(0);
            this.rawName1 = length == 9 ? packed.getInt(8)
                    : length >= 10 ? packed.getLong(8) : 0;
        } else {
            this.rawName0 = 0;
            this.rawName1 = 0;
        }
    }
}
