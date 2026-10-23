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
public final class Jackson3WriteShapeDiagnostics {
    public static final Jackson3WriteShapeDiagnostics INSTANCE = new Jackson3WriteShapeDiagnostics();
    private static final tools.jackson.core.io.SerializedString ID = new tools.jackson.core.io.SerializedString("id");
    private static final tools.jackson.core.io.SerializedString CREATED_AT = new tools.jackson.core.io.SerializedString("createdAt");
    private static final tools.jackson.core.io.SerializedString UPDATED_AT = new tools.jackson.core.io.SerializedString("updatedAt");
    private static final tools.jackson.core.io.SerializedString REPUTATION = new tools.jackson.core.io.SerializedString("reputation");
    private static final tools.jackson.core.io.SerializedString LOGIN_COUNT = new tools.jackson.core.io.SerializedString("loginCount");
    private static final tools.jackson.core.io.SerializedString AGE = new tools.jackson.core.io.SerializedString("age");
    private static final tools.jackson.core.io.SerializedString ACTIVE = new tools.jackson.core.io.SerializedString("active");
    private static final tools.jackson.core.io.SerializedString VERIFIED = new tools.jackson.core.io.SerializedString("verified");
    private static final tools.jackson.core.io.SerializedString ADMIN = new tools.jackson.core.io.SerializedString("admin");
    private static final tools.jackson.core.io.SerializedString SUSPENDED = new tools.jackson.core.io.SerializedString("suspended");
    private static final tools.jackson.core.io.SerializedString SCORE = new tools.jackson.core.io.SerializedString("score");
    private static final tools.jackson.core.io.SerializedString LATITUDE = new tools.jackson.core.io.SerializedString("latitude");
    private static final tools.jackson.core.io.SerializedString LONGITUDE = new tools.jackson.core.io.SerializedString("longitude");
    private static final tools.jackson.core.io.SerializedString USERNAME = new tools.jackson.core.io.SerializedString("username");
    private static final tools.jackson.core.io.SerializedString EMAIL = new tools.jackson.core.io.SerializedString("email");
    private static final tools.jackson.core.io.SerializedString DISPLAY_NAME = new tools.jackson.core.io.SerializedString("displayName");
    private static final tools.jackson.core.io.SerializedString PASSWORD_HASH = new tools.jackson.core.io.SerializedString("passwordHash");
    private static final tools.jackson.core.io.SerializedString BIO = new tools.jackson.core.io.SerializedString("bio");
    private static final tools.jackson.core.io.SerializedString WEBSITE = new tools.jackson.core.io.SerializedString("website");
    private static final tools.jackson.core.io.SerializedString DEPARTMENT = new tools.jackson.core.io.SerializedString("department");
    private static final tools.jackson.core.io.SerializedString ADDRESS = new tools.jackson.core.io.SerializedString("address");
    private static final tools.jackson.core.io.SerializedString TAGS = new tools.jackson.core.io.SerializedString("tags");
    private static final tools.jackson.core.io.SerializedString FRIENDS = new tools.jackson.core.io.SerializedString("friends");
    private static final tools.jackson.core.io.SerializedString STREET = new tools.jackson.core.io.SerializedString("street");
    private static final tools.jackson.core.io.SerializedString CITY = new tools.jackson.core.io.SerializedString("city");
    private static final tools.jackson.core.io.SerializedString STATE = new tools.jackson.core.io.SerializedString("state");
    private static final tools.jackson.core.io.SerializedString ZIP = new tools.jackson.core.io.SerializedString("zip");
    private static final tools.jackson.core.io.SerializedString COUNTRY = new tools.jackson.core.io.SerializedString("country");
    private static final tools.jackson.core.io.SerializedString NAME = new tools.jackson.core.io.SerializedString("name");
    private static final tools.jackson.core.io.SerializedString SINCE = new tools.jackson.core.io.SerializedString("since");
    private static final tools.jackson.core.io.SerializedString CLOSE = new tools.jackson.core.io.SerializedString("close");

    private Jackson3WriteShapeDiagnostics() {}

    public static void writeOrdered(tools.jackson.core.JsonGenerator g, User v) throws IOException {
        if (v == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeName(ID);
        g.writeNumber(v.getId());
        g.writeName(CREATED_AT);
        g.writeNumber(v.getCreatedAt());
        g.writeName(UPDATED_AT);
        g.writeNumber(v.getUpdatedAt());
        g.writeName(REPUTATION);
        g.writeNumber(v.getReputation());
        g.writeName(LOGIN_COUNT);
        g.writeNumber(v.getLoginCount());
        g.writeName(AGE);
        g.writeNumber(v.getAge());
        g.writeName(ACTIVE);
        g.writeBoolean(v.isActive());
        g.writeName(VERIFIED);
        g.writeBoolean(v.isVerified());
        g.writeName(ADMIN);
        g.writeBoolean(v.isAdmin());
        g.writeName(SUSPENDED);
        g.writeBoolean(v.isSuspended());
        g.writeName(SCORE);
        g.writeNumber(v.getScore());
        g.writeName(LATITUDE);
        g.writeNumber(v.getLatitude());
        g.writeName(LONGITUDE);
        g.writeNumber(v.getLongitude());
        str(g, USERNAME, v.getUsername());
        str(g, EMAIL, v.getEmail());
        str(g, DISPLAY_NAME, v.getDisplayName());
        str(g, PASSWORD_HASH, v.getPasswordHash());
        str(g, BIO, v.getBio());
        str(g, WEBSITE, org.sjf4j.value.ValueCodec.URI_CODEC.valueToRaw(v.getWebsite()));
        str(g, DEPARTMENT, v.getDepartment());
        g.writeName(ADDRESS);
        orderedAddress(g, v.getAddress());
        g.writeName(TAGS);
        orderedTags(g, v.getTags());
        g.writeName(FRIENDS);
        orderedFriends(g, v.getFriends());
        g.writeEndObject();
    }

    public void writeWrapped(org.sjf4j.backend.jackson3.binding.Jackson3Writer writer, User v) throws IOException {
        tools.jackson.core.JsonGenerator g = writer.nativeWriter();
        if (v == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeName(ID);
        g.writeNumber(v.getId());
        g.writeName(CREATED_AT);
        g.writeNumber(v.getCreatedAt());
        g.writeName(UPDATED_AT);
        g.writeNumber(v.getUpdatedAt());
        g.writeName(REPUTATION);
        g.writeNumber(v.getReputation());
        g.writeName(LOGIN_COUNT);
        g.writeNumber(v.getLoginCount());
        g.writeName(AGE);
        g.writeNumber(v.getAge());
        g.writeName(ACTIVE);
        g.writeBoolean(v.isActive());
        g.writeName(VERIFIED);
        g.writeBoolean(v.isVerified());
        g.writeName(ADMIN);
        g.writeBoolean(v.isAdmin());
        g.writeName(SUSPENDED);
        g.writeBoolean(v.isSuspended());
        g.writeName(SCORE);
        g.writeNumber(v.getScore());
        g.writeName(LATITUDE);
        g.writeNumber(v.getLatitude());
        g.writeName(LONGITUDE);
        g.writeNumber(v.getLongitude());
        _writeStringMember(g, USERNAME, v.getUsername());
        _writeStringMember(g, EMAIL, v.getEmail());
        _writeStringMember(g, DISPLAY_NAME, v.getDisplayName());
        _writeStringMember(g, PASSWORD_HASH, v.getPasswordHash());
        _writeStringMember(g, BIO, v.getBio());
        g.writeName(WEBSITE);
        java.net.URI codecValue = v.getWebsite();
        if (codecValue == null) g.writeNull();
        else _writeString(g, org.sjf4j.value.ValueCodec.URI_CODEC.valueToRaw(codecValue));
        _writeStringMember(g, DEPARTMENT, v.getDepartment());
        g.writeName(ADDRESS);
        writeAddress(writer, v.getAddress());
        g.writeName(TAGS);
        writeTags(writer, v.getTags());
        g.writeName(FRIENDS);
        writeFriends(writer, v.getFriends());
        g.writeEndObject();
    }

    private static void orderedAddress(tools.jackson.core.JsonGenerator g, Address v) throws IOException {
        if (v == null) { g.writeNull(); return; }
        g.writeStartObject();
        str(g, STREET, v.getStreet());
        str(g, CITY, v.getCity());
        str(g, STATE, v.getState());
        str(g, ZIP, v.getZip());
        str(g, COUNTRY, v.getCountry());
        g.writeEndObject();
    }

    private void writeAddress(org.sjf4j.backend.jackson3.binding.Jackson3Writer writer, Address v) throws IOException {
        tools.jackson.core.JsonGenerator g = writer.nativeWriter();
        if (v == null) { g.writeNull(); return; }
        g.writeStartObject();
        _writeStringMember(g, STREET, v.getStreet());
        _writeStringMember(g, CITY, v.getCity());
        _writeStringMember(g, STATE, v.getState());
        _writeStringMember(g, ZIP, v.getZip());
        _writeStringMember(g, COUNTRY, v.getCountry());
        g.writeEndObject();
    }

    private static void orderedTags(tools.jackson.core.JsonGenerator g, List<String> v) throws IOException {
        if (v == null) { g.writeNull(); return; }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) { String x = v.get(i); if (x == null) g.writeNull(); else g.writeString(x); }
        } else {
            for (String x : v) { if (x == null) g.writeNull(); else g.writeString(x); }
        }
        g.writeEndArray();
    }

    private void writeTags(org.sjf4j.backend.jackson3.binding.Jackson3Writer writer, List<String> v) throws IOException {
        tools.jackson.core.JsonGenerator g = writer.nativeWriter();
        if (v == null) { g.writeNull(); return; }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) { String x = v.get(i); _writeString(g, x); }
        } else {
            for (String x : v) _writeString(g, x);
        }
        g.writeEndArray();
    }

    private static void orderedFriends(tools.jackson.core.JsonGenerator g, List<Friend> v) throws IOException {
        if (v == null) { g.writeNull(); return; }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) orderedFriend(g, v.get(i));
        } else {
            for (Friend f : v) orderedFriend(g, f);
        }
        g.writeEndArray();
    }

    private void writeFriends(org.sjf4j.backend.jackson3.binding.Jackson3Writer writer, List<Friend> v) throws IOException {
        tools.jackson.core.JsonGenerator g = writer.nativeWriter();
        if (v == null) { g.writeNull(); return; }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) writeFriend(writer, v.get(i));
        } else {
            for (Friend f : v) writeFriend(writer, f);
        }
        g.writeEndArray();
    }

    private static void orderedFriend(tools.jackson.core.JsonGenerator g, Friend v) throws IOException {
        if (v == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeName(ID);
        g.writeNumber(v.getId());
        str(g, NAME, v.getName());
        g.writeName(SINCE);
        g.writeNumber(v.getSince());
        g.writeName(CLOSE);
        g.writeBoolean(v.isClose());
        g.writeEndObject();
    }

    private void writeFriend(org.sjf4j.backend.jackson3.binding.Jackson3Writer writer, Friend v) throws IOException {
        tools.jackson.core.JsonGenerator g = writer.nativeWriter();
        if (v == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeName(ID);
        g.writeNumber(v.getId());
        _writeStringMember(g, NAME, v.getName());
        g.writeName(SINCE);
        g.writeNumber(v.getSince());
        g.writeName(CLOSE);
        g.writeBoolean(v.isClose());
        g.writeEndObject();
    }

    private static void str(tools.jackson.core.JsonGenerator g, tools.jackson.core.io.SerializedString name, String value) throws IOException {
        g.writeName(name);
        if (value == null) g.writeNull(); else g.writeString(value);
    }

    private static void _writeString(tools.jackson.core.JsonGenerator g, String value) throws IOException {
        if (value == null) g.writeNull(); else g.writeString(value);
    }

    private void _writeStringMember(tools.jackson.core.JsonGenerator g, tools.jackson.core.io.SerializedString name, String value) throws IOException {
        g.writeName(name);
        _writeString(g, value);
    }
}
