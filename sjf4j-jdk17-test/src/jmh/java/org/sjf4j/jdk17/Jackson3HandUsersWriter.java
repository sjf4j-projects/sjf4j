package org.sjf4j.jdk17;

import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.util.BufferRecycler;
import tools.jackson.core.util.ByteArrayBuilder;

import java.io.IOException;

/** JMH-only direct writer for the Micronaut Serde benchmark model. */
final class Jackson3HandUsersWriter {
    private static final SerializedString REQUEST_ID = name("requestId");
    private static final SerializedString GENERATED_AT = name("generatedAt");
    private static final SerializedString SOURCE = name("source");
    private static final SerializedString USERS = name("users");
    private static final SerializedString ID = name("id");
    private static final SerializedString NAME = name("name");
    private static final SerializedString EMAIL = name("email");
    private static final SerializedString AGE = name("age");
    private static final SerializedString ACTIVE = name("active");
    private static final SerializedString SCORE = name("score");
    private static final SerializedString BALANCE = name("balance");
    private static final SerializedString CREATED_AT = name("createdAt");
    private static final SerializedString UPDATED_AT = name("updatedAt");
    private static final SerializedString LOGIN_COUNT = name("loginCount");
    private static final SerializedString RANK = name("rank");
    private static final SerializedString VERIFIED = name("verified");
    private static final SerializedString DEPARTMENT = name("department");
    private static final SerializedString TITLE = name("title");
    private static final SerializedString PHONE = name("phone");
    private static final SerializedString WEBSITE = name("website");
    private static final SerializedString LOCALE = name("locale");
    private static final SerializedString TIME_ZONE = name("timeZone");
    private static final SerializedString STATUS = name("status");
    private static final SerializedString NOTE = name("note");
    private static final SerializedString ADDRESS = name("address");
    private static final SerializedString TAGS = name("tags");
    private static final SerializedString FRIENDS = name("friends");
    private static final SerializedString STREET = name("street");
    private static final SerializedString CITY = name("city");
    private static final SerializedString STATE = name("state");
    private static final SerializedString POSTAL_CODE = name("postalCode");
    private static final SerializedString COUNTRY = name("country");
    private static final SerializedString LATITUDE = name("latitude");
    private static final SerializedString LONGITUDE = name("longitude");
    private static final SerializedString SINCE = name("since");

    private final TokenStreamFactory factory;

    Jackson3HandUsersWriter(TokenStreamFactory factory) {
        this.factory = factory;
    }

    byte[] write(Users value) throws IOException {
        // This is the public buffer/generator path used by ObjectWriter.writeValueAsBytes.
        BufferRecycler recycler = factory._getBufferRecycler();
        ByteArrayBuilder output = new ByteArrayBuilder(recycler);
        try {
            try (JsonGenerator generator = factory.createGenerator(TokenStreamFactory.EMPTY_WRITE_CONTEXT, output, JsonEncoding.UTF8)) {
                writeUsers(generator, value);
            }
            return output.getClearAndRelease();
        } finally {
            output.close();
            recycler.releaseToPool();
        }
    }

    private static void writeUsers(JsonGenerator generator, Users value) throws IOException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        generator.writeStartObject();
        generator.writeName(GENERATED_AT);
        generator.writeNumber(value.generatedAt);
        string(generator, REQUEST_ID, value.requestId);
        string(generator, SOURCE, value.source);
        generator.writeName(USERS);
        writeUsersArray(generator, value.users);
        generator.writeEndObject();
    }

    private static void writeUser(JsonGenerator generator, User value) throws IOException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        generator.writeStartObject();
        generator.writeName(ACTIVE); generator.writeBoolean(value.active);
        generator.writeName(ADDRESS); writeAddress(generator, value.address);
        generator.writeName(AGE); generator.writeNumber(value.age);
        generator.writeName(BALANCE); generator.writeNumber(value.balance);
        generator.writeName(CREATED_AT); generator.writeNumber(value.createdAt);
        string(generator, DEPARTMENT, value.department);
        string(generator, EMAIL, value.email);
        generator.writeName(FRIENDS); writeFriends(generator, value.friends);
        generator.writeName(ID); generator.writeNumber(value.id);
        string(generator, LOCALE, value.locale);
        generator.writeName(LOGIN_COUNT); generator.writeNumber(value.loginCount);
        string(generator, NAME, value.name);
        string(generator, NOTE, value.note);
        string(generator, PHONE, value.phone);
        generator.writeName(RANK); generator.writeNumber(value.rank);
        generator.writeName(SCORE); generator.writeNumber(value.score);
        string(generator, STATUS, value.status);
        generator.writeName(TAGS); writeStrings(generator, value.tags);
        string(generator, TIME_ZONE, value.timeZone);
        string(generator, TITLE, value.title);
        generator.writeName(UPDATED_AT); generator.writeNumber(value.updatedAt);
        generator.writeName(VERIFIED); generator.writeBoolean(value.verified);
        string(generator, WEBSITE, value.website);
        generator.writeEndObject();
    }

    private static void writeAddress(JsonGenerator generator, Address value) throws IOException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        generator.writeStartObject();
        string(generator, CITY, value.city);
        string(generator, COUNTRY, value.country);
        generator.writeName(LATITUDE); generator.writeNumber(value.latitude);
        generator.writeName(LONGITUDE); generator.writeNumber(value.longitude);
        string(generator, POSTAL_CODE, value.postalCode);
        string(generator, STATE, value.state);
        string(generator, STREET, value.street);
        generator.writeEndObject();
    }

    private static void writeFriend(JsonGenerator generator, Friend value) throws IOException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        generator.writeStartObject();
        generator.writeName(ACTIVE); generator.writeBoolean(value.active);
        generator.writeName(AGE); generator.writeNumber(value.age);
        string(generator, EMAIL, value.email);
        generator.writeName(ID); generator.writeNumber(value.id);
        string(generator, NAME, value.name);
        generator.writeName(SCORE); generator.writeNumber(value.score);
        generator.writeName(SINCE); generator.writeNumber(value.since);
        generator.writeEndObject();
    }

    private static void writeUsersArray(JsonGenerator generator, User[] values) throws IOException {
        if (values == null) {
            generator.writeNull();
            return;
        }
        generator.writeStartArray();
        for (int i = 0; i < values.length; i++) {
            writeUser(generator, values[i]);
        }
        generator.writeEndArray();
    }

    private static void writeStrings(JsonGenerator generator, String[] values) throws IOException {
        if (values == null) {
            generator.writeNull();
            return;
        }
        generator.writeStartArray();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                generator.writeNull();
            } else {
                generator.writeString(values[i]);
            }
        }
        generator.writeEndArray();
    }

    private static void writeFriends(JsonGenerator generator, Friend[] values) throws IOException {
        if (values == null) {
            generator.writeNull();
            return;
        }
        generator.writeStartArray();
        for (int i = 0; i < values.length; i++) {
            writeFriend(generator, values[i]);
        }
        generator.writeEndArray();
    }

    private static void string(JsonGenerator generator, SerializedString name, String value) throws IOException {
        generator.writeName(name);
        if (value == null) {
            generator.writeNull();
        } else {
            generator.writeString(value);
        }
    }

    private static SerializedString name(String value) {
        return new SerializedString(value);
    }
}
