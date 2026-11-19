package org.sjf4j.testbench.binding.handwritten;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;
import org.sjf4j.value.ValueCodec;
import org.sjf4j.testbench.model.Users;

import java.io.IOException;
import java.net.URI;
import org.sjf4j.NodeConfig;
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

    /** Backwards-compatible default; benchmarks pass the same config as Compiled. */
    public static void writeUsers(JsonGenerator g, Users v) throws IOException {
        writeUsers(g, v, NodeConfig.DEFAULT);
    }

    public static void writeUsers(JsonGenerator g, Users v, NodeConfig config)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        List<User> property0 = v.getUsers();
        if (config.includeNulls || property0 != null) {
            g.writeFieldName(USERS);
            users(g, property0, config);
        }
        g.writeFieldName(TOTAL);
        g.writeNumber(v.getTotal());
        g.writeFieldName(PAGE);
        g.writeNumber(v.getPage());
        g.writeFieldName(GENERATED_AT);
        g.writeNumber(v.getGeneratedAt());
        g.writeEndObject();
    }

    private static void users(JsonGenerator g, List<User> v, NodeConfig config)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) writeUser(g, v.get(i), config);
        } else {
            for (User item : v) writeUser(g, item, config);
        }
        g.writeEndArray();
    }

    /** Field order and null-policy branches mirror generated CompiledBinder. */
    public static void writeUser(JsonGenerator g, User v) throws IOException {
        writeUser(g, v, NodeConfig.DEFAULT);
    }

    public static void writeUser(JsonGenerator g, User v, NodeConfig config)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
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
        String property13 = v.getUsername();
        if (config.includeNulls || property13 != null) {
            str(g, USERNAME, property13);
        }
        String property14 = v.getEmail();
        if (config.includeNulls || property14 != null) {
            str(g, EMAIL, property14);
        }
        String property15 = v.getDisplayName();
        if (config.includeNulls || property15 != null) {
            str(g, DISPLAY_NAME, property15);
        }
        String property16 = v.getPasswordHash();
        if (config.includeNulls || property16 != null) {
            str(g, PASSWORD_HASH, property16);
        }
        String property17 = v.getBio();
        if (config.includeNulls || property17 != null) {
            str(g, BIO, property17);
        }
        URI property18 = v.getWebsite();
        if (config.includeNulls || property18 != null) {
            str(g, WEBSITE, ValueCodec.URI_CODEC.valueToRaw(property18));
        }
        String property19 = v.getDepartment();
        if (config.includeNulls || property19 != null) {
            str(g, DEPARTMENT, property19);
        }
        Address property20 = v.getAddress();
        if (config.includeNulls || property20 != null) {
            g.writeFieldName(ADDRESS);
            address(g, property20, config);
        }
        List<String> property21 = v.getTags();
        if (config.includeNulls || property21 != null) {
            g.writeFieldName(TAGS);
            tags(g, property21, config);
        }
        List<Friend> property22 = v.getFriends();
        if (config.includeNulls || property22 != null) {
            g.writeFieldName(FRIENDS);
            friends(g, property22, config);
        }
        g.writeEndObject();
    }

    private static void address(JsonGenerator g, Address v, NodeConfig config)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        String property0 = v.getStreet();
        if (config.includeNulls || property0 != null) {
            str(g, STREET, property0);
        }
        String property1 = v.getCity();
        if (config.includeNulls || property1 != null) {
            str(g, CITY, property1);
        }
        String property2 = v.getState();
        if (config.includeNulls || property2 != null) {
            str(g, STATE, property2);
        }
        String property3 = v.getZip();
        if (config.includeNulls || property3 != null) {
            str(g, ZIP, property3);
        }
        String property4 = v.getCountry();
        if (config.includeNulls || property4 != null) {
            str(g, COUNTRY, property4);
        }
        g.writeEndObject();
    }

    private static void friends(JsonGenerator g, List<Friend> v, NodeConfig config)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) friend(g, v.get(i), config);
        } else {
            for (Friend item : v) friend(g, item, config);
        }
        g.writeEndArray();
    }

    private static void friend(JsonGenerator g, Friend v, NodeConfig config)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        g.writeFieldName(ID);
        g.writeNumber(v.getId());
        g.writeFieldName(SINCE);
        g.writeNumber(v.getSince());
        String property2 = v.getName();
        if (config.includeNulls || property2 != null) {
            str(g, NAME, property2);
        }
        g.writeFieldName(CLOSE);
        g.writeBoolean(v.isClose());
        g.writeEndObject();
    }

    private static void tags(JsonGenerator g, List<String> v, NodeConfig config)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) writeString(g, v.get(i));
        } else {
            for (String item : v) writeString(g, item);
        }
        g.writeEndArray();
    }

    private static void writeString(JsonGenerator g, String v) throws IOException {
        if (v == null) g.writeNull();
        else g.writeString(v);
    }

    private static void str(JsonGenerator g, SerializableString n, String v)
            throws IOException {
        g.writeFieldName(n);
        if (v == null) g.writeNull();
        else g.writeString(v);
    }
}
