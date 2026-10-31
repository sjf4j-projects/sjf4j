package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.backend.jackson2.binding.Jackson2Parser;
import org.sjf4j.testbench.model.fabien.Clients;
import org.sjf4j.util.FastUuid;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Handwritten Jackson Core reader for the Fabien Clients benchmark fixture. */
final class Jackson2HandRead {

    private static final String[] CLIENT_FIELDS = {"id", "index", "guid", "isActive", "balance",
            "picture", "age", "eyeColor", "name", "gender", "company", "emails", "phones",
            "address", "about", "registered", "latitude", "longitude", "tags", "partners"};
    private static final String[] PARTNER_FIELDS = {"id", "name", "since"};
    private static final SerializedString[] CLIENT_NAMES = serializedNames(CLIENT_FIELDS);
    private static final SerializedString[] PARTNER_NAMES = serializedNames(PARTNER_FIELDS);
    private static final Map<String, Integer> CLIENT_INDEX = indexes(CLIENT_FIELDS);
    private static final Map<String, Integer> PARTNER_INDEX = indexes(PARTNER_FIELDS);

    private Jackson2HandRead() {
    }

    static Clients readClients(JsonFactory factory, byte[] json) throws IOException {
        return readClients(factory, json, true, false, false);
    }

    static Clients readClientsWithJdkUuid(JsonFactory factory, byte[] json) throws IOException {
        return readClients(factory, json, false, false, false);
    }

    static Clients readClientsWithWrappedScalars(JsonFactory factory, byte[] json) throws IOException {
        return readClients(factory, json, true, true, false);
    }

    static Clients readClientsWithWrappedArrays(JsonFactory factory, byte[] json) throws IOException {
        return readClients(factory, json, true, false, true);
    }

    private static Clients readClients(JsonFactory factory, byte[] json,
                                       boolean fastUuid, boolean wrappedScalars,
                                       boolean wrappedArrays) throws IOException {
        try (JsonParser parser = factory.createParser(json)) {
            Jackson2Parser wrapped = wrappedScalars || wrappedArrays ? new Jackson2Parser(parser) : null;
            JsonToken token = parser.nextToken();
            if (token == JsonToken.VALUE_NULL) {
                requireEnd(parser);
                return null;
            }
            require(token, JsonToken.START_OBJECT);
            Clients result = new Clients();
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                require(token, JsonToken.FIELD_NAME);
                String name = parser.currentName();
                parser.nextToken();
                if ("clients".equals(name)) {
                    result.setClients(readClientList(parser, wrapped, fastUuid, wrappedScalars, wrappedArrays));
                } else {
                    parser.skipChildren();
                }
            }
            requireEnd(parser);
            return result;
        }
    }

    private static List<Clients.Client> readClientList(JsonParser parser, Jackson2Parser wrapped,
                                                       boolean fastUuid, boolean wrappedScalars,
                                                       boolean wrappedArrays) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) return null;
        require(parser.currentToken(), JsonToken.START_ARRAY);
        List<Clients.Client> result = new ArrayList<>();
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == JsonToken.VALUE_NULL) {
                result.add(null);
            } else {
                require(token, JsonToken.START_OBJECT);
                result.add(readClient(parser, wrapped, fastUuid, wrappedScalars, wrappedArrays));
            }
        }
        return result;
    }

    private static Clients.Client readClient(JsonParser parser, Jackson2Parser wrapped,
                                             boolean fastUuid, boolean wrappedScalars,
                                             boolean wrappedArrays) throws IOException {
        Clients.Client client = new Clients.Client();
        int expected = 0;
        int index;
        while ((index = nextFieldIndex(parser, CLIENT_NAMES, CLIENT_INDEX, expected)) != -1) {
            if (index >= 0) expected = index + 1;
            // Leave a scalar field value pending for Jackson2Parser; array helpers
            // still consume their start token through the native JsonParser.
            boolean wrappedValue = wrappedScalars && index >= 0
                    && index != 11 && index != 12 && index != 18 && index != 19;
            if (!wrappedValue) parser.nextToken();
            switch (index) {
                case 0: client.setId(wrappedValue ? wrapped.readLongValue() : parser.getLongValue()); break;
                case 1: client.setIndex(wrappedValue ? wrapped.readIntValue() : parser.getIntValue()); break;
                case 2: client.setGuid(wrappedValue ? wrapped.readUuid() : readUuid(parser, fastUuid)); break;
                case 3: client.setIsActive(wrappedValue ? wrapped.readBooleanValue()
                        : parser.getBooleanValue()); break;
                case 4: client.setBalance(wrappedValue ? wrapped.readBigDecimal()
                        : parser.currentToken() == JsonToken.VALUE_NULL ? null : parser.getDecimalValue()); break;
                case 5: client.setPicture(readFieldString(parser, wrapped, wrappedValue)); break;
                case 6: client.setAge(wrappedValue ? wrapped.readIntValue() : parser.getIntValue()); break;
                case 7: {
                    String color = readFieldString(parser, wrapped, wrappedValue);
                    client.setEyeColor(color == null ? null : Clients.EyeColor.valueOf(color));
                    break;
                }
                case 8: client.setName(readFieldString(parser, wrapped, wrappedValue)); break;
                case 9: client.setGender(readFieldString(parser, wrapped, wrappedValue)); break;
                case 10: client.setCompany(readFieldString(parser, wrapped, wrappedValue)); break;
                case 11: client.setEmails(wrappedArrays ? readEmailsWrapped(parser, wrapped)
                        : readEmails(parser)); break;
                case 12: client.setPhones(wrappedArrays ? readPhonesWrapped(parser, wrapped)
                        : readPhones(parser)); break;
                case 13: client.setAddress(readFieldString(parser, wrapped, wrappedValue)); break;
                case 14: client.setAbout(readFieldString(parser, wrapped, wrappedValue)); break;
                case 15: {
                    String date = readFieldString(parser, wrapped, wrappedValue);
                    client.setRegistered(date == null ? null : LocalDate.parse(date));
                    break;
                }
                case 16: client.setLatitude(wrappedValue ? wrapped.readDoubleValue()
                        : parser.getDoubleValue()); break;
                case 17: client.setLongitude(wrappedValue ? wrapped.readDoubleValue()
                        : parser.getDoubleValue()); break;
                case 18: client.setTags(wrappedArrays ? readTagsWrapped(parser, wrapped)
                        : readTags(parser)); break;
                case 19: client.setPartners(readPartners(parser, wrapped, wrappedScalars)); break;
                default: parser.skipChildren(); break;
            }
        }
        return client;
    }

    private static UUID readUuid(JsonParser parser, boolean fastUuid) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) return null;
        require(parser.currentToken(), JsonToken.VALUE_STRING);
        if (!fastUuid) return UUID.fromString(parser.getText());
        // Same conversion as SJF4J's Jackson2Parser.readUuid(): decode in the
        // Jackson text buffer, without constructing an intermediate String.
        int length = parser.getTextLength();
        return FastUuid.parseUUID(parser.getTextCharacters(), parser.getTextOffset(), length);
    }

    private static String[] readEmails(JsonParser parser) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) return null;
        require(parser.currentToken(), JsonToken.START_ARRAY);
        String[] value = new String[8];
        int size = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (size == value.length) value = Arrays.copyOf(value, size << 1);
            value[size++] = readString(parser);
        }
        return size == value.length ? value : Arrays.copyOf(value, size);
    }

    private static String[] readEmailsWrapped(JsonParser parser, Jackson2Parser wrapped) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) return null;
        require(parser.currentToken(), JsonToken.START_ARRAY);
        String[] value = new String[8];
        int size = 0;
        while (!wrapped.nextIfArrayEnd()) {
            if (size == value.length) value = Arrays.copyOf(value, size << 1);
            value[size++] = wrapped.readString();
        }
        return size == value.length ? value : Arrays.copyOf(value, size);
    }

    private static long[] readPhones(JsonParser parser) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) return null;
        require(parser.currentToken(), JsonToken.START_ARRAY);
        long[] value = new long[8];
        int size = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (size == value.length) value = Arrays.copyOf(value, size << 1);
            value[size++] = parser.getLongValue();
        }
        return size == value.length ? value : Arrays.copyOf(value, size);
    }

    private static long[] readPhonesWrapped(JsonParser parser, Jackson2Parser wrapped) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) return null;
        require(parser.currentToken(), JsonToken.START_ARRAY);
        long[] value = new long[8];
        int size = 0;
        while (!wrapped.nextIfArrayEnd()) {
            if (size == value.length) value = Arrays.copyOf(value, size << 1);
            value[size++] = wrapped.readLongValue();
        }
        return size == value.length ? value : Arrays.copyOf(value, size);
    }

    private static List<String> readTags(JsonParser parser) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) return null;
        require(parser.currentToken(), JsonToken.START_ARRAY);
        List<String> result = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            result.add(readString(parser));
        }
        return result;
    }

    private static List<String> readTagsWrapped(JsonParser parser, Jackson2Parser wrapped) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) return null;
        require(parser.currentToken(), JsonToken.START_ARRAY);
        List<String> result = new ArrayList<>();
        while (!wrapped.nextIfArrayEnd()) {
            result.add(wrapped.readString());
        }
        return result;
    }

    private static List<Clients.Partner> readPartners(JsonParser parser, Jackson2Parser wrapped,
                                                     boolean wrappedScalars) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) return null;
        require(parser.currentToken(), JsonToken.START_ARRAY);
        List<Clients.Partner> result = new ArrayList<>();
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == JsonToken.VALUE_NULL) {
                result.add(null);
            } else {
                require(token, JsonToken.START_OBJECT);
                result.add(readPartner(parser, wrapped, wrappedScalars));
            }
        }
        return result;
    }

    private static Clients.Partner readPartner(JsonParser parser, Jackson2Parser wrapped,
                                               boolean wrappedScalars) throws IOException {
        Clients.Partner partner = new Clients.Partner();
        int expected = 0;
        int index;
        while ((index = nextFieldIndex(parser, PARTNER_NAMES, PARTNER_INDEX, expected)) != -1) {
            if (index >= 0) expected = index + 1;
            if (!wrappedScalars || index < 0) parser.nextToken();
            switch (index) {
                case 0: partner.setId(wrappedScalars ? wrapped.readLongValue() : parser.getLongValue()); break;
                case 1: partner.setName(readFieldString(parser, wrapped, wrappedScalars)); break;
                case 2: {
                    String since = readFieldString(parser, wrapped, wrappedScalars);
                    partner.setSince(since == null ? null : OffsetDateTime.parse(since));
                    break;
                }
                default: parser.skipChildren(); break;
            }
        }
        return partner;
    }

    private static String readString(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) return null;
        require(token, JsonToken.VALUE_STRING);
        return parser.getText();
    }

    private static String readFieldString(JsonParser parser, Jackson2Parser wrapped,
                                          boolean wrappedScalar) throws IOException {
        return wrappedScalar ? wrapped.readString() : readString(parser);
    }

    private static int nextFieldIndex(JsonParser parser, SerializedString[] names,
                                      Map<String, Integer> indexes, int expected) throws IOException {
        if (expected < names.length && parser.nextFieldName(names[expected])) return expected;
        JsonToken token = parser.currentToken();
        if (expected >= names.length) {
            parser.nextFieldName();
            token = parser.currentToken();
        }
        if (token == JsonToken.END_OBJECT) return -1;
        require(token, JsonToken.FIELD_NAME);
        return indexes.getOrDefault(parser.currentName(), -2);
    }

    private static SerializedString[] serializedNames(String[] names) {
        SerializedString[] result = new SerializedString[names.length];
        for (int i = 0; i < names.length; i++) result[i] = new SerializedString(names[i]);
        return result;
    }

    private static Map<String, Integer> indexes(String[] names) {
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < names.length; i++) result.put(names[i], i);
        return result;
    }

    private static void requireEnd(JsonParser parser) throws IOException {
        if (parser.nextToken() != null) throw new IOException("trailing JSON content");
    }

    private static void require(JsonToken actual, JsonToken expected) throws IOException {
        if (actual != expected) throw new IOException("expected " + expected + ", got " + actual);
    }
}
