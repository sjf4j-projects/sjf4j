
package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.meta.PropertyInfo;

/** Prepared Jackson 2 property-name metadata. */
public final class Jackson2NameMatcher extends NameMatcher {

    final SerializableString[] serializedNames;

    Jackson2NameMatcher(PropertyInfo[] settableProperties) {
        this(settableProperties, null, null);
    }

    /**
     * Prepares declared names for generated binding.
     * @param names the primary property names
     */
    public Jackson2NameMatcher(String... names) {
        this(null, names, null);
    }

    /**
     * Prepares declared names and index-aligned aliases for generated binding.
     * @param names the primary property names
     * @param aliases optional aliases per property
     */
    public Jackson2NameMatcher(String[] names, String[][] aliases) {
        this(null, names, aliases);
    }

    /**
     * Native Jackson field traversal for compiled binding. FIELD_NAME stays
     * current so the next Jackson2Parser scalar reader consumes the value.
     * Handles reordered/aliased/unknown fields by the existing fallback map.
     *
     * @param parser the Jackson parser positioned before the next field or object end
     * @param expectedIndex the expected property index, or a negative value if unknown
     * @return the matched property index, or {@link #OBJECT_END} at the end of the object
     * @throws IOException if parsing fails or the next token is not a field name or object end
     */
    public int matchNext(JsonParser parser, int expectedIndex) throws IOException {
        if (expectedIndex >= 0 && expectedIndex < serializedNames.length
                && parser.nextFieldName(serializedNames[expectedIndex])) {
            return expectedIndex;
        }
        JsonToken token;
        if (expectedIndex < 0 || expectedIndex >= serializedNames.length) {
            String name = parser.nextFieldName();
            if (name != null) {
                return fallback(name);
            }
            token = parser.currentToken();
        } else {
            // Failed nextFieldName has already advanced. Do not advance twice.
            token = parser.currentToken();
        }
        if (token == JsonToken.FIELD_NAME) {
            return fallback(parser.currentName());
        }
        if (token == JsonToken.END_OBJECT) {
            return OBJECT_END;
        }
        throw new IOException("expected FIELD_NAME or END_OBJECT, but was " + token);
    }

    private Jackson2NameMatcher(PropertyInfo[] settableProperties, String[] names, String[][] aliases) {
        super(settableProperties, names, aliases);
        serializedNames = new SerializableString[size()];
        for (int i = 0; i < size(); i++) {
            serializedNames[i] = new SerializedString(name(i));
        }
    }

}
