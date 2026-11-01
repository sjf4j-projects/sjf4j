package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.sjf4j.backend.jackson2.binding.Jackson2NameMatcher;
import org.sjf4j.backend.jackson2.binding.Jackson2Parser;
import org.sjf4j.testbench.model.fabien.Clients;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Diagnostic implementation of a potential Jackson2 APT fast path.
 *
 * A single Jackson2Parser is passed throughout, as it would be in generated code.
 * Object/array boundaries and array element iteration use the native JsonParser.
 * Object names are matched by Jackson2Parser.nextNameMatch while native JsonParser handles arrays; individual
 * POJO scalar reads use the existing Jackson2Parser contract.
 *
 * IMPORTANT: After matching FIELD_NAME, scalar reads must consume their own token.
 * Only native container branches may call parser.nextToken() for the field value.
 * Do not use nextIfArrayEnd or other lookahead operations while using the
 * raw parser; this keeps Jackson2Parser.prefetched false throughout.
 */
final class Jackson2HalfHybridHandRead {

    private static final Jackson2NameMatcher ROOT_NAMES = new Jackson2NameMatcher("clients");
    private static final Jackson2NameMatcher CLIENT_NAMES = new Jackson2NameMatcher(
            "id", "index", "guid", "isActive", "balance",
            "picture", "age", "eyeColor", "name", "gender", "company",
            "emails", "phones", "address", "about", "registered",
            "latitude", "longitude", "tags", "partners");
    private static final Jackson2NameMatcher PARTNER_NAMES =
            new Jackson2NameMatcher("id", "name", "since");

    private Jackson2HalfHybridHandRead() {}

    static Clients readClients(JsonFactory factory, byte[] json) throws IOException {
        try (JsonParser nativeParser = factory.createParser(json)) {
            return readClients(new Jackson2Parser(nativeParser));
        }
    }

    /** Parameter and recursive call contract for a generated Jackson2 compiled binder. */
    static Clients readClients(Jackson2Parser reader) throws IOException {
        JsonParser parser = reader.nativeParser();
        JsonToken start = parser.nextToken();
        if (start == JsonToken.VALUE_NULL) {
            requireEnd(parser);
            return null;
        }
        require(start, JsonToken.START_OBJECT);
        Clients result = new Clients();
        int expected = 0;
        int index;
        while ((index = reader.nextNameMatch(ROOT_NAMES, expected)) != Jackson2NameMatcher.OBJECT_END) {
            if (index >= 0) expected = index + 1;
            if (index == 0) {
                result.setClients(readClientsArray(reader));
            } else {
                reader.skipNode();
            }
        }
        requireEnd(parser);
        return result;
    }

    /** FIELD_NAME is current; structural value is consumed by the native parser. */
    private static List<Clients.Client> readClientsArray(Jackson2Parser reader) throws IOException {
        JsonParser parser = reader.nativeParser();
        JsonToken token = parser.nextToken();
        if (token == JsonToken.VALUE_NULL) return null;
        require(token, JsonToken.START_ARRAY);
        List<Clients.Client> values = new ArrayList<>();
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == JsonToken.VALUE_NULL) {
                values.add(null);
            } else {
                require(token, JsonToken.START_OBJECT);
                values.add(readClient(reader));
            }
        }
        return values;
    }

    /** START_OBJECT is already current; leave cursor at END_OBJECT. */
    private static Clients.Client readClient(Jackson2Parser reader) throws IOException {
        JsonParser parser = reader.nativeParser();
        Clients.Client value = new Clients.Client();
        int expected = 0;
        int index;
        while ((index = reader.nextNameMatch(CLIENT_NAMES, expected)) != Jackson2NameMatcher.OBJECT_END) {
            if (index >= 0) expected = index + 1;
            switch (index) {
                case 0: value.setId(reader.readLongValue()); break;
                case 1: value.setIndex(reader.readIntValue()); break;
                case 2: value.setGuid(reader.readUuid()); break;
                case 3: value.setIsActive(reader.readBooleanValue()); break;
                case 4: value.setBalance(reader.readBigDecimal()); break;
                case 5: value.setPicture(reader.readString()); break;
                case 6: value.setAge(reader.readIntValue()); break;
                case 7: {
                    String color = reader.readString();
                    value.setEyeColor(color == null ? null : Clients.EyeColor.valueOf(color));
                    break;
                }
                case 8: value.setName(reader.readString()); break;
                case 9: value.setGender(reader.readString()); break;
                case 10: value.setCompany(reader.readString()); break;
                case 11: value.setEmails(readEmails(reader)); break;
                case 12: value.setPhones(readPhones(reader)); break;
                case 13: value.setAddress(reader.readString()); break;
                case 14: value.setAbout(reader.readString()); break;
                case 15: value.setRegistered(reader.readLocalDate()); break;
                case 16: value.setLatitude(reader.readDoubleValue()); break;
                case 17: value.setLongitude(reader.readDoubleValue()); break;
                case 18: value.setTags(readTags(reader)); break;
                case 19: value.setPartners(readPartners(reader)); break;
                default: reader.skipNode(); break;
            }
        }
        return value;
    }

    /** FIELD_NAME is current; read array elements using native JsonParser. */
    private static String[] readEmails(Jackson2Parser reader) throws IOException {
        JsonParser parser = reader.nativeParser();
        JsonToken token = parser.nextToken();
        if (token == JsonToken.VALUE_NULL) return null;
        require(token, JsonToken.START_ARRAY);
        String[] result = new String[8];
        int size = 0;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (size == result.length) result = Arrays.copyOf(result, size << 1);
            result[size++] = readNativeString(parser, token);
        }
        return size == result.length ? result : Arrays.copyOf(result, size);
    }

    private static long[] readPhones(Jackson2Parser reader) throws IOException {
        JsonParser parser = reader.nativeParser();
        JsonToken token = parser.nextToken();
        if (token == JsonToken.VALUE_NULL) return null;
        require(token, JsonToken.START_ARRAY);
        long[] result = new long[8];
        int size = 0;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (size == result.length) result = Arrays.copyOf(result, size << 1);
            result[size++] = parser.getLongValue();
        }
        return size == result.length ? result : Arrays.copyOf(result, size);
    }

    private static List<String> readTags(Jackson2Parser reader) throws IOException {
        JsonParser parser = reader.nativeParser();
        JsonToken token = parser.nextToken();
        if (token == JsonToken.VALUE_NULL) return null;
        require(token, JsonToken.START_ARRAY);
        List<String> result = new ArrayList<>();
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            result.add(readNativeString(parser, token));
        }
        return result;
    }

    private static List<Clients.Partner> readPartners(Jackson2Parser reader) throws IOException {
        JsonParser parser = reader.nativeParser();
        JsonToken token = parser.nextToken();
        if (token == JsonToken.VALUE_NULL) return null;
        require(token, JsonToken.START_ARRAY);
        List<Clients.Partner> result = new ArrayList<>();
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == JsonToken.VALUE_NULL) {
                result.add(null);
            } else {
                require(token, JsonToken.START_OBJECT);
                result.add(readPartner(reader));
            }
        }
        return result;
    }

    private static Clients.Partner readPartner(Jackson2Parser reader) throws IOException {
        JsonParser parser = reader.nativeParser();
        Clients.Partner value = new Clients.Partner();
        int index;
        int expected = 0;
        while ((index = reader.nextNameMatch(PARTNER_NAMES, expected)) != Jackson2NameMatcher.OBJECT_END) {
            if (index >= 0) expected = index + 1;
            switch (index) {
                case 0: value.setId(reader.readLongValue()); break;
                case 1: value.setName(reader.readString()); break;
                case 2: value.setSince(reader.readOffsetDateTime()); break;
                default: reader.skipNode(); break;
            }
        }
        return value;
    }

    private static String readNativeString(JsonParser parser, JsonToken token) throws IOException {
        if (token == JsonToken.VALUE_NULL) return null;
        require(token, JsonToken.VALUE_STRING);
        return parser.getText();
    }

    private static void requireEnd(JsonParser parser) throws IOException {
        if (parser.nextToken() != null) throw new IOException("trailing JSON content");
    }

    private static void require(JsonToken actual, JsonToken expected) throws IOException {
        if (actual != expected) throw new IOException("expected " + expected + ", got " + actual);
    }
}
