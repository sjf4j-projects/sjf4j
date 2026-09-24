package org.sjf4j.testbench.binding.handwritten;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;
import org.sjf4j.testbench.model.Users;

import java.io.IOException;
import java.util.List;

/** Direct Jackson UTF-8 writer for {@link Users}. */
public final class Jackson2HandPojoSerializedWriter {
    private static final SerializableString USERS = new SerializedString("users"),
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

    private Jackson2HandPojoSerializedWriter() {}

    public static void writeUsers(JsonGenerator g, Users v)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        g.writeFieldName(USERS);
        users(g, v.getUsers());
        g.writeFieldName(TOTAL);
        g.writeNumber(v.getTotal());
        g.writeFieldName(PAGE);
        g.writeNumber(v.getPage());
        g.writeFieldName(GENERATED_AT);
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
        for (int i = 0, n = v.size(); i < n; i++) writeUser(g, v.get(i));
        g.writeEndArray();
    }

    public static void writeUser(JsonGenerator g, User v) throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        g.writeFieldName(ID);
        g.writeNumber(v.getId());
        str(g, USERNAME, v.getUsername());
        str(g, EMAIL, v.getEmail());
        str(g, DISPLAY_NAME, v.getDisplayName());
        str(g, PASSWORD_HASH, v.getPasswordHash());
        str(g, BIO, v.getBio());
        str(g, WEBSITE, v.getWebsite());
        str(g, DEPARTMENT, v.getDepartment());
        g.writeFieldName(CREATED_AT);
        g.writeNumber(v.getCreatedAt());
        g.writeFieldName(UPDATED_AT);
        g.writeNumber(v.getUpdatedAt());
        g.writeFieldName(LOGIN_COUNT);
        g.writeNumber(v.getLoginCount());
        g.writeFieldName(REPUTATION);
        g.writeNumber(v.getReputation());
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
        g.writeFieldName(AGE);
        g.writeNumber(v.getAge());
        g.writeFieldName(ADDRESS);
        address(g, v.getAddress());
        g.writeFieldName(TAGS);
        tags(g, v.getTags());
        g.writeFieldName(FRIENDS);
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
        for (int i = 0, n = v.size(); i < n; i++) {
            Friend f = v.get(i);
            if (f == null) {
                g.writeNull();
                continue;
            }
            g.writeStartObject();
            g.writeFieldName(ID);
            g.writeNumber(f.getId());
            str(g, NAME, f.getName());
            g.writeFieldName(SINCE);
            g.writeNumber(f.getSince());
            g.writeFieldName(CLOSE);
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
        for (int i = 0, n = v.size(); i < n; i++) {
            String s = v.get(i);
            if (s == null) g.writeNull();
            else g.writeString(s);
        }
        g.writeEndArray();
    }

    private static void str(JsonGenerator g, SerializableString n, String v) throws IOException {
        g.writeFieldName(n);
        if (v == null) g.writeNull();
        else g.writeString(v);
    }
}
