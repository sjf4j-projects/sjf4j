package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.sjf4j.backend.jackson2.binding.Jackson2NameMatcher;
import org.sjf4j.backend.jackson2.binding.Jackson2Parser;
import org.sjf4j.testbench.model.fabien.Clients;
import org.sjf4j.util.FastUuid;
import org.sjf4j.util.FastLocalDate;
import org.sjf4j.util.FastOffsetDateTime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Diagnostic implementation of a potential Jackson2 APT fast path.
 *
 * A single Jackson2Parser is passed throughout, as it would be in generated code.
 * Object/array boundaries and array element iteration use the native JsonParser.
 * Property matching directly uses the native JsonParser with the reusable Jackson2NameMatcher, and individual
 * POJO scalar reads use JsonParser directly, with identical FastUuid/Date parsing.
 *
 * IMPORTANT: The raw scalar getter consumes a value token once. Native
 * container branches also consume their own value token exactly once.
 * Do not use nextIfArrayEnd or other lookahead operations while using the
 * raw parser; this keeps Jackson2Parser.prefetched false throughout.
 */
final class Jackson2HybridNativeScalarRead {

    private static final Jackson2NameMatcher ROOT_NAMES = new Jackson2NameMatcher("clients");
    private static final Jackson2NameMatcher CLIENT_NAMES = new Jackson2NameMatcher(
            "id", "index", "guid", "isActive", "balance",
            "picture", "age", "eyeColor", "name", "gender", "company",
            "emails", "phones", "address", "about", "registered",
            "latitude", "longitude", "tags", "partners");
    private static final Jackson2NameMatcher PARTNER_NAMES =
            new Jackson2NameMatcher("id", "name", "since");

    private Jackson2HybridNativeScalarRead() {}

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
        while ((index = ROOT_NAMES.matchNext(parser, expected)) != Jackson2NameMatcher.OBJECT_END) {
            if (index >= 0) expected = index + 1;
            if (index == 0) {
                result.setClients(readClientsArray(reader));
            } else {
                skipNode(parser);
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
        while ((index = CLIENT_NAMES.matchNext(parser, expected)) != Jackson2NameMatcher.OBJECT_END) {
            if (index >= 0) expected = index + 1;
            switch (index) {
                case 0: value.setId(readLong(parser)); break;
                case 1: value.setIndex(readInt(parser)); break;
                case 2: value.setGuid(readUuid(parser)); break;
                case 3: value.setIsActive(readBoolean(parser)); break;
                case 4: value.setBalance(readDecimal(parser)); break;
                case 5: value.setPicture(readString(parser)); break;
                case 6: value.setAge(readInt(parser)); break;
                case 7: {
                    String color = readString(parser);
                    value.setEyeColor(color == null ? null : Clients.EyeColor.valueOf(color));
                    break;
                }
                case 8: value.setName(readString(parser)); break;
                case 9: value.setGender(readString(parser)); break;
                case 10: value.setCompany(readString(parser)); break;
                case 11: value.setEmails(readEmails(reader)); break;
                case 12: value.setPhones(readPhones(reader)); break;
                case 13: value.setAddress(readString(parser)); break;
                case 14: value.setAbout(readString(parser)); break;
                case 15: value.setRegistered(readLocalDate(parser)); break;
                case 16: value.setLatitude(readDouble(parser)); break;
                case 17: value.setLongitude(readDouble(parser)); break;
                case 18: value.setTags(readTags(reader)); break;
                case 19: value.setPartners(readPartners(reader)); break;
                default: skipNode(parser); break;
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
        while ((index = PARTNER_NAMES.matchNext(parser, expected)) != Jackson2NameMatcher.OBJECT_END) {
            if (index >= 0) expected = index + 1;
            switch (index) {
                case 0: value.setId(readLong(parser)); break;
                case 1: value.setName(readString(parser)); break;
                case 2: value.setSince(readOffsetDateTime(parser)); break;
                default: skipNode(parser); break;
            }
        }
        return value;
    }

    // Scalar value remains pending at FIELD_NAME; consume only once.
    private static long readLong(JsonParser parser) throws IOException {
        parser.nextToken();
        return parser.getLongValue();
    }

    private static int readInt(JsonParser parser) throws IOException {
        parser.nextToken();
        return parser.getIntValue();
    }

    private static boolean readBoolean(JsonParser parser) throws IOException {
        parser.nextToken();
        return parser.getBooleanValue();
    }

    private static double readDouble(JsonParser parser) throws IOException {
        parser.nextToken();
        return parser.getDoubleValue();
    }

    private static String readString(JsonParser parser) throws IOException {
        return readNativeString(parser, parser.nextToken());
    }

    private static BigDecimal readDecimal(JsonParser parser) throws IOException {
        return parser.nextToken() == JsonToken.VALUE_NULL ? null : parser.getDecimalValue();
    }

    private static UUID readUuid(JsonParser parser) throws IOException {
        JsonToken token = parser.nextToken();
        if (token == JsonToken.VALUE_NULL) return null;
        require(token, JsonToken.VALUE_STRING);
        return FastUuid.parseUUID(
                parser.getTextCharacters(), parser.getTextOffset(), parser.getTextLength());
    }

    private static LocalDate readLocalDate(JsonParser parser) throws IOException {
        JsonToken token = parser.nextToken();
        if (token == JsonToken.VALUE_NULL) return null;
        require(token, JsonToken.VALUE_STRING);
        return FastLocalDate.parse(
                parser.getTextCharacters(), parser.getTextOffset(), parser.getTextLength());
    }

    private static OffsetDateTime readOffsetDateTime(JsonParser parser) throws IOException {
        JsonToken token = parser.nextToken();
        if (token == JsonToken.VALUE_NULL) return null;
        require(token, JsonToken.VALUE_STRING);
        return FastOffsetDateTime.parse(
                parser.getTextCharacters(), parser.getTextOffset(), parser.getTextLength());
    }

    private static void skipNode(JsonParser parser) throws IOException {
        parser.nextToken();
        parser.skipChildren();
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
