package org.sjf4j.backend.jackson3.binding;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import java.io.IOException;

import org.sjf4j.exception.BindingException;

/**
 * Minimal scalar validation for native compiled Jackson 3 reads.
 *
 * <p>The cursor is already positioned at a value by native field matching or
 * array traversal. These helpers do not advance it and preserve the strict
 * JSON string/null contract of the general streaming reader.</p>
 */
public final class Jackson3NativeReadSupport {

    private Jackson3NativeReadSupport() {
    }

    /** Reads an already-selected JSON string or null without advancing. */
    public static char readCharValue(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw new BindingException("expected string for char");
        }
        return currentChar(parser);
    }

    public static Character readChar(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) return null;
        if (token != JsonToken.VALUE_STRING) {
            throw new BindingException("expected string or null, but was " + token);
        }
        return currentChar(parser);
    }

    private static char currentChar(JsonParser parser) throws IOException {
        int length = parser.getStringLength();
        if (length != 1) {
            throw new BindingException("cannot read char: expected single-character string,"
                    + " but length was " + length);
        }
        return parser.getStringCharacters()[parser.getStringOffset()];
    }

    public static String readString(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_STRING) {
            return parser.getString();
        }
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        throw new IOException("expected string or null, but was " + token);
    }
}
