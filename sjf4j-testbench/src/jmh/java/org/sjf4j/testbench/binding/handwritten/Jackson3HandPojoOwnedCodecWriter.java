package org.sjf4j.testbench.binding.handwritten;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SerializedString;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;
import org.sjf4j.value.ValueCodec;
import org.sjf4j.testbench.model.Users;

import java.io.IOException;
import java.util.List;

/** Diagnostic handwritten Jackson3 writer with the same codec null handling as generated CompiledBinder. */
public final class Jackson3HandPojoOwnedCodecWriter {
    private static final SerializedString USERS = new SerializedString("users"),
            TOTAL = new SerializedString("total"),
            PAGE = new SerializedString("page"),
            GENERATED_AT = new SerializedString("generatedAt"),
            ID = new SerializedString("id"),
            USERNAME = new SerializedString("username"),
            EMAIL = new SerializedString("email"),
            DISPLAY_NAME = new SerializedString("displayName"),
            PASSWORD_HASH = new SerializedString("passwordHash"),
            BIO = new SerializedString("bio"),
            WEBSITE = new SerializedString("website"),
            DEPARTMENT = new SerializedString("department"),
            CREATED_AT = new SerializedString("createdAt"),
            UPDATED_AT = new SerializedString("updatedAt"),
            LOGIN_COUNT = new SerializedString("loginCount"),
            REPUTATION = new SerializedString("reputation"),
            ACTIVE = new SerializedString("active"),
            VERIFIED = new SerializedString("verified"),
            ADMIN = new SerializedString("admin"),
            SUSPENDED = new SerializedString("suspended"),
            SCORE = new SerializedString("score"),
            LATITUDE = new SerializedString("latitude"),
            LONGITUDE = new SerializedString("longitude"),
            AGE = new SerializedString("age"),
            ADDRESS = new SerializedString("address"),
            TAGS = new SerializedString("tags"),
            FRIENDS = new SerializedString("friends"),
            STREET = new SerializedString("street"),
            CITY = new SerializedString("city"),
            STATE = new SerializedString("state"),
            ZIP = new SerializedString("zip"),
            COUNTRY = new SerializedString("country"),
            NAME = new SerializedString("name"),
            SINCE = new SerializedString("since"),
            CLOSE = new SerializedString("close");

    private final org.sjf4j.backend.jackson3.binding.Jackson3Binder binder;

    public Jackson3HandPojoOwnedCodecWriter(org.sjf4j.backend.jackson3.binding.Jackson3Binder binder) {
        this.binder = java.util.Objects.requireNonNull(binder, "binder");
    }

    public byte[] writePojoUtf8(User value) throws IOException {
        try (org.sjf4j.backend.jackson3.binding.Jackson3Writer writer = this.binder.createWriterUtf8()) {
            writeUser(writer.nativeWriter(), value);
            writer.flush();
            return writer.toBytes();
        }
    }

    public String writePojoString(User value) throws IOException {
        try (org.sjf4j.backend.jackson3.binding.Jackson3Writer writer = this.binder.createWriter()) {
            writeUser(writer.nativeWriter(), value);
            writer.flush();
            return writer.toString();
        }
    }



    public static void writeUsers(JsonGenerator g, Users v)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        g.writeName(USERS);
        users(g, v.getUsers());
        g.writeName(TOTAL);
        g.writeNumber(v.getTotal());
        g.writeName(PAGE);
        g.writeNumber(v.getPage());
        g.writeName(GENERATED_AT);
        g.writeNumber(v.getGeneratedAt());
        g.writeEndObject();
    }

    private static void users(JsonGenerator g, List<User> v)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) writeUser(g, v.get(i));
        } else {
            for (User item : v) writeUser(g, item);
        }
        g.writeEndArray();
    }

    public static void writeUser(JsonGenerator g, User v) throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        g.writeName(ID);
        g.writeNumber(v.getId());
        str(g, USERNAME, v.getUsername());
        str(g, EMAIL, v.getEmail());
        str(g, DISPLAY_NAME, v.getDisplayName());
        str(g, PASSWORD_HASH, v.getPasswordHash());
        str(g, BIO, v.getBio());
        java.net.URI property18 = v.getWebsite();
        g.writeName(WEBSITE);
        {
            java.net.URI codecValue = property18;
            if (codecValue == null) {
                g.writeNull();
            } else {
                String rawValue = ValueCodec.URI_CODEC.valueToRaw(codecValue);
                _writeString(g, rawValue);
            }
        }
        str(g, DEPARTMENT, v.getDepartment());
        g.writeName(CREATED_AT);
        g.writeNumber(v.getCreatedAt());
        g.writeName(UPDATED_AT);
        g.writeNumber(v.getUpdatedAt());
        g.writeName(LOGIN_COUNT);
        g.writeNumber(v.getLoginCount());
        g.writeName(REPUTATION);
        g.writeNumber(v.getReputation());
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
        g.writeName(AGE);
        g.writeNumber(v.getAge());
        g.writeName(ADDRESS);
        address(g, v.getAddress());
        g.writeName(TAGS);
        tags(g, v.getTags());
        g.writeName(FRIENDS);
        friends(g, v.getFriends());
        g.writeEndObject();
    }

    private static void address(JsonGenerator g, Address v)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        str(g, STREET, v.getStreet());
        str(g, CITY, v.getCity());
        str(g, STATE, v.getState());
        str(g, ZIP, v.getZip());
        str(g, COUNTRY, v.getCountry());
        g.writeEndObject();
    }

    private static void friends(JsonGenerator g, List<Friend> v)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartArray();
        for (Friend f : v) {
            if (f == null) {
                g.writeNull();
                continue;
            }
            g.writeStartObject();
            g.writeName(ID);
            g.writeNumber(f.getId());
            str(g, NAME, f.getName());
            g.writeName(SINCE);
            g.writeNumber(f.getSince());
            g.writeName(CLOSE);
            g.writeBoolean(f.isClose());
            g.writeEndObject();
        }
        g.writeEndArray();
    }

    private static void tags(JsonGenerator g, List<String> v) throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) {
                String s = v.get(i);
                if (s == null) g.writeNull();
                else g.writeString(s);
            }
        } else {
            for (String s : v) {
                if (s == null) g.writeNull();
                else g.writeString(s);
            }
        }
        g.writeEndArray();
    }

    private static void _writeString(JsonGenerator g, String value) throws IOException {
        if (value == null) g.writeNull();
        else g.writeString(value);
    }

    private static void str(JsonGenerator g, SerializedString n, String v) throws IOException {
        g.writeName(n);
        if (v == null) g.writeNull();
        else g.writeString(v);
    }
}
