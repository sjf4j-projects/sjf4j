package org.sjf4j.testbench.binding.handwritten;

import java.io.IOException;
import java.util.List;
import org.sjf4j.testbench.model.User;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;

/**
 * Paired handwritten diagnostics for the compiled Jackson write shape.
 * The ordered baseline uses the same property ordering as the compiled model.
 * The wrapped baseline also mirrors compiled instance helpers and two-level string helpers.
 * Both reuse the same caller-created SJF4J writer lifecycle.
 */
public final class Jackson2WriteShapeDiagnostics {
    public static final Jackson2WriteShapeDiagnostics INSTANCE = new Jackson2WriteShapeDiagnostics();
    private static final com.fasterxml.jackson.core.SerializableString ID = new com.fasterxml.jackson.core.io.SerializedString("id");
    private static final com.fasterxml.jackson.core.SerializableString CREATED_AT = new com.fasterxml.jackson.core.io.SerializedString("createdAt");
    private static final com.fasterxml.jackson.core.SerializableString UPDATED_AT = new com.fasterxml.jackson.core.io.SerializedString("updatedAt");
    private static final com.fasterxml.jackson.core.SerializableString REPUTATION = new com.fasterxml.jackson.core.io.SerializedString("reputation");
    private static final com.fasterxml.jackson.core.SerializableString LOGIN_COUNT = new com.fasterxml.jackson.core.io.SerializedString("loginCount");
    private static final com.fasterxml.jackson.core.SerializableString AGE = new com.fasterxml.jackson.core.io.SerializedString("age");
    private static final com.fasterxml.jackson.core.SerializableString ACTIVE = new com.fasterxml.jackson.core.io.SerializedString("active");
    private static final com.fasterxml.jackson.core.SerializableString VERIFIED = new com.fasterxml.jackson.core.io.SerializedString("verified");
    private static final com.fasterxml.jackson.core.SerializableString ADMIN = new com.fasterxml.jackson.core.io.SerializedString("admin");
    private static final com.fasterxml.jackson.core.SerializableString SUSPENDED = new com.fasterxml.jackson.core.io.SerializedString("suspended");
    private static final com.fasterxml.jackson.core.SerializableString SCORE = new com.fasterxml.jackson.core.io.SerializedString("score");
    private static final com.fasterxml.jackson.core.SerializableString LATITUDE = new com.fasterxml.jackson.core.io.SerializedString("latitude");
    private static final com.fasterxml.jackson.core.SerializableString LONGITUDE = new com.fasterxml.jackson.core.io.SerializedString("longitude");
    private static final com.fasterxml.jackson.core.SerializableString USERNAME = new com.fasterxml.jackson.core.io.SerializedString("username");
    private static final com.fasterxml.jackson.core.SerializableString EMAIL = new com.fasterxml.jackson.core.io.SerializedString("email");
    private static final com.fasterxml.jackson.core.SerializableString DISPLAY_NAME = new com.fasterxml.jackson.core.io.SerializedString("displayName");
    private static final com.fasterxml.jackson.core.SerializableString PASSWORD_HASH = new com.fasterxml.jackson.core.io.SerializedString("passwordHash");
    private static final com.fasterxml.jackson.core.SerializableString BIO = new com.fasterxml.jackson.core.io.SerializedString("bio");
    private static final com.fasterxml.jackson.core.SerializableString WEBSITE = new com.fasterxml.jackson.core.io.SerializedString("website");
    private static final com.fasterxml.jackson.core.SerializableString DEPARTMENT = new com.fasterxml.jackson.core.io.SerializedString("department");
    private static final com.fasterxml.jackson.core.SerializableString ADDRESS = new com.fasterxml.jackson.core.io.SerializedString("address");
    private static final com.fasterxml.jackson.core.SerializableString TAGS = new com.fasterxml.jackson.core.io.SerializedString("tags");
    private static final com.fasterxml.jackson.core.SerializableString FRIENDS = new com.fasterxml.jackson.core.io.SerializedString("friends");
    private static final com.fasterxml.jackson.core.SerializableString STREET = new com.fasterxml.jackson.core.io.SerializedString("street");
    private static final com.fasterxml.jackson.core.SerializableString CITY = new com.fasterxml.jackson.core.io.SerializedString("city");
    private static final com.fasterxml.jackson.core.SerializableString STATE = new com.fasterxml.jackson.core.io.SerializedString("state");
    private static final com.fasterxml.jackson.core.SerializableString ZIP = new com.fasterxml.jackson.core.io.SerializedString("zip");
    private static final com.fasterxml.jackson.core.SerializableString COUNTRY = new com.fasterxml.jackson.core.io.SerializedString("country");
    private static final com.fasterxml.jackson.core.SerializableString NAME = new com.fasterxml.jackson.core.io.SerializedString("name");
    private static final com.fasterxml.jackson.core.SerializableString SINCE = new com.fasterxml.jackson.core.io.SerializedString("since");
    private static final com.fasterxml.jackson.core.SerializableString CLOSE = new com.fasterxml.jackson.core.io.SerializedString("close");

    private Jackson2WriteShapeDiagnostics() {}

    public static void writeOrdered(com.fasterxml.jackson.core.JsonGenerator g, User v) throws IOException {
        if (v == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeFieldName(ID);
        g.writeNumber(v.getId());
        g.writeFieldName(CREATED_AT);
        g.writeNumber(v.getCreatedAt());
        g.writeFieldName(UPDATED_AT);
        g.writeNumber(v.getUpdatedAt());
        g.writeFieldName(REPUTATION);
        g.writeNumber(v.getReputation());
        g.writeFieldName(LOGIN_COUNT);
        g.writeNumber(v.getLoginCount());
        g.writeFieldName(AGE);
        g.writeNumber(v.getAge());
        g.writeFieldName(ACTIVE);
        g.writeBoolean(v.isActive());
        g.writeFieldName(VERIFIED);
        g.writeBoolean(v.isVerified());
        g.writeFieldName(ADMIN);
        g.writeBoolean(v.isAdmin());
        g.writeFieldName(SUSPENDED);
        g.writeBoolean(v.isSuspended());
        g.writeFieldName(SCORE);
        g.writeNumber(v.getScore());
        g.writeFieldName(LATITUDE);
        g.writeNumber(v.getLatitude());
        g.writeFieldName(LONGITUDE);
        g.writeNumber(v.getLongitude());
        str(g, USERNAME, v.getUsername());
        str(g, EMAIL, v.getEmail());
        str(g, DISPLAY_NAME, v.getDisplayName());
        str(g, PASSWORD_HASH, v.getPasswordHash());
        str(g, BIO, v.getBio());
        str(g, WEBSITE, org.sjf4j.value.ValueCodec.URI_CODEC.valueToRaw(v.getWebsite()));
        str(g, DEPARTMENT, v.getDepartment());
        g.writeFieldName(ADDRESS);
        orderedAddress(g, v.getAddress());
        g.writeFieldName(TAGS);
        orderedTags(g, v.getTags());
        g.writeFieldName(FRIENDS);
        orderedFriends(g, v.getFriends());
        g.writeEndObject();
    }

    public void writeWrapped(org.sjf4j.backend.jackson2.binding.Jackson2Writer writer, User v) throws IOException {
        com.fasterxml.jackson.core.JsonGenerator g = writer.nativeWriter();
        if (v == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeFieldName(ID);
        g.writeNumber(v.getId());
        g.writeFieldName(CREATED_AT);
        g.writeNumber(v.getCreatedAt());
        g.writeFieldName(UPDATED_AT);
        g.writeNumber(v.getUpdatedAt());
        g.writeFieldName(REPUTATION);
        g.writeNumber(v.getReputation());
        g.writeFieldName(LOGIN_COUNT);
        g.writeNumber(v.getLoginCount());
        g.writeFieldName(AGE);
        g.writeNumber(v.getAge());
        g.writeFieldName(ACTIVE);
        g.writeBoolean(v.isActive());
        g.writeFieldName(VERIFIED);
        g.writeBoolean(v.isVerified());
        g.writeFieldName(ADMIN);
        g.writeBoolean(v.isAdmin());
        g.writeFieldName(SUSPENDED);
        g.writeBoolean(v.isSuspended());
        g.writeFieldName(SCORE);
        g.writeNumber(v.getScore());
        g.writeFieldName(LATITUDE);
        g.writeNumber(v.getLatitude());
        g.writeFieldName(LONGITUDE);
        g.writeNumber(v.getLongitude());
        _writeStringMember(g, USERNAME, v.getUsername());
        _writeStringMember(g, EMAIL, v.getEmail());
        _writeStringMember(g, DISPLAY_NAME, v.getDisplayName());
        _writeStringMember(g, PASSWORD_HASH, v.getPasswordHash());
        _writeStringMember(g, BIO, v.getBio());
        g.writeFieldName(WEBSITE);
        java.net.URI codecValue = v.getWebsite();
        if (codecValue == null) g.writeNull();
        else _writeString(g, org.sjf4j.value.ValueCodec.URI_CODEC.valueToRaw(codecValue));
        _writeStringMember(g, DEPARTMENT, v.getDepartment());
        g.writeFieldName(ADDRESS);
        writeAddress(writer, v.getAddress());
        g.writeFieldName(TAGS);
        writeTags(writer, v.getTags());
        g.writeFieldName(FRIENDS);
        writeFriends(writer, v.getFriends());
        g.writeEndObject();
    }

    private static void orderedAddress(com.fasterxml.jackson.core.JsonGenerator g, Address v) throws IOException {
        if (v == null) { g.writeNull(); return; }
        g.writeStartObject();
        str(g, STREET, v.getStreet());
        str(g, CITY, v.getCity());
        str(g, STATE, v.getState());
        str(g, ZIP, v.getZip());
        str(g, COUNTRY, v.getCountry());
        g.writeEndObject();
    }

    private void writeAddress(org.sjf4j.backend.jackson2.binding.Jackson2Writer writer, Address v) throws IOException {
        com.fasterxml.jackson.core.JsonGenerator g = writer.nativeWriter();
        if (v == null) { g.writeNull(); return; }
        g.writeStartObject();
        _writeStringMember(g, STREET, v.getStreet());
        _writeStringMember(g, CITY, v.getCity());
        _writeStringMember(g, STATE, v.getState());
        _writeStringMember(g, ZIP, v.getZip());
        _writeStringMember(g, COUNTRY, v.getCountry());
        g.writeEndObject();
    }

    private static void orderedTags(com.fasterxml.jackson.core.JsonGenerator g, List<String> v) throws IOException {
        if (v == null) { g.writeNull(); return; }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) { String x = v.get(i); if (x == null) g.writeNull(); else g.writeString(x); }
        } else {
            for (String x : v) { if (x == null) g.writeNull(); else g.writeString(x); }
        }
        g.writeEndArray();
    }

    private void writeTags(org.sjf4j.backend.jackson2.binding.Jackson2Writer writer, List<String> v) throws IOException {
        com.fasterxml.jackson.core.JsonGenerator g = writer.nativeWriter();
        if (v == null) { g.writeNull(); return; }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) { String x = v.get(i); _writeString(g, x); }
        } else {
            for (String x : v) _writeString(g, x);
        }
        g.writeEndArray();
    }

    private static void orderedFriends(com.fasterxml.jackson.core.JsonGenerator g, List<Friend> v) throws IOException {
        if (v == null) { g.writeNull(); return; }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) orderedFriend(g, v.get(i));
        } else {
            for (Friend f : v) orderedFriend(g, f);
        }
        g.writeEndArray();
    }

    private void writeFriends(org.sjf4j.backend.jackson2.binding.Jackson2Writer writer, List<Friend> v) throws IOException {
        com.fasterxml.jackson.core.JsonGenerator g = writer.nativeWriter();
        if (v == null) { g.writeNull(); return; }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) writeFriend(writer, v.get(i));
        } else {
            for (Friend f : v) writeFriend(writer, f);
        }
        g.writeEndArray();
    }

    private static void orderedFriend(com.fasterxml.jackson.core.JsonGenerator g, Friend v) throws IOException {
        if (v == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeFieldName(ID);
        g.writeNumber(v.getId());
        str(g, NAME, v.getName());
        g.writeFieldName(SINCE);
        g.writeNumber(v.getSince());
        g.writeFieldName(CLOSE);
        g.writeBoolean(v.isClose());
        g.writeEndObject();
    }

    private void writeFriend(org.sjf4j.backend.jackson2.binding.Jackson2Writer writer, Friend v) throws IOException {
        com.fasterxml.jackson.core.JsonGenerator g = writer.nativeWriter();
        if (v == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeFieldName(ID);
        g.writeNumber(v.getId());
        _writeStringMember(g, NAME, v.getName());
        g.writeFieldName(SINCE);
        g.writeNumber(v.getSince());
        g.writeFieldName(CLOSE);
        g.writeBoolean(v.isClose());
        g.writeEndObject();
    }

    private static void str(com.fasterxml.jackson.core.JsonGenerator g, com.fasterxml.jackson.core.SerializableString name, String value) throws IOException {
        g.writeFieldName(name);
        if (value == null) g.writeNull(); else g.writeString(value);
    }

    private static void _writeString(com.fasterxml.jackson.core.JsonGenerator g, String value) throws IOException {
        if (value == null) g.writeNull(); else g.writeString(value);
    }

    private void _writeStringMember(com.fasterxml.jackson.core.JsonGenerator g, com.fasterxml.jackson.core.SerializableString name, String value) throws IOException {
        g.writeFieldName(name);
        _writeString(g, value);
    }
}
