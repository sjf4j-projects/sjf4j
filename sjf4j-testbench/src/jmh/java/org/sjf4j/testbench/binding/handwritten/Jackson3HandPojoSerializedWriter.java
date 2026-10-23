package org.sjf4j.testbench.binding.handwritten;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SerializedString;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;
import org.sjf4j.value.ValueCodec;
import org.sjf4j.testbench.model.Users;

import java.io.IOException;
import java.net.URI;
import org.sjf4j.RuntimeContext;
import java.util.List;

/** Direct Jackson3 token writer for User, matching the Jackson2 hand-written baseline. */
public final class Jackson3HandPojoSerializedWriter {
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

    private Jackson3HandPojoSerializedWriter() {}

    /** Backwards-compatible default; benchmarks pass the same context as Compiled. */
    public static void writeUsers(JsonGenerator g, Users v) throws IOException {
        writeUsers(g, v, RuntimeContext.EMPTY);
    }

    public static void writeUsers(JsonGenerator g, Users v, RuntimeContext context)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        List<User> property0 = v.getUsers();
        if (context.includeNulls || property0 != null) {
            g.writeName(USERS);
            users(g, property0, context);
        }
        g.writeName(TOTAL);
        g.writeNumber(v.getTotal());
        g.writeName(PAGE);
        g.writeNumber(v.getPage());
        g.writeName(GENERATED_AT);
        g.writeNumber(v.getGeneratedAt());
        g.writeEndObject();
    }

    private static void users(JsonGenerator g, List<User> v, RuntimeContext context)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) writeUser(g, v.get(i), context);
        } else {
            for (User item : v) writeUser(g, item, context);
        }
        g.writeEndArray();
    }

    /** Field order and null-policy branches mirror generated CompiledBinder. */
    public static void writeUser(JsonGenerator g, User v) throws IOException {
        writeUser(g, v, RuntimeContext.EMPTY);
    }

    public static void writeUser(JsonGenerator g, User v, RuntimeContext context)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
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
        String property13 = v.getUsername();
        if (context.includeNulls || property13 != null) {
            str(g, USERNAME, property13);
        }
        String property14 = v.getEmail();
        if (context.includeNulls || property14 != null) {
            str(g, EMAIL, property14);
        }
        String property15 = v.getDisplayName();
        if (context.includeNulls || property15 != null) {
            str(g, DISPLAY_NAME, property15);
        }
        String property16 = v.getPasswordHash();
        if (context.includeNulls || property16 != null) {
            str(g, PASSWORD_HASH, property16);
        }
        String property17 = v.getBio();
        if (context.includeNulls || property17 != null) {
            str(g, BIO, property17);
        }
        URI property18 = v.getWebsite();
        if (context.includeNulls || property18 != null) {
            str(g, WEBSITE, ValueCodec.URI_CODEC.valueToRaw(property18));
        }
        String property19 = v.getDepartment();
        if (context.includeNulls || property19 != null) {
            str(g, DEPARTMENT, property19);
        }
        Address property20 = v.getAddress();
        if (context.includeNulls || property20 != null) {
            g.writeName(ADDRESS);
            address(g, property20, context);
        }
        List<String> property21 = v.getTags();
        if (context.includeNulls || property21 != null) {
            g.writeName(TAGS);
            tags(g, property21, context);
        }
        List<Friend> property22 = v.getFriends();
        if (context.includeNulls || property22 != null) {
            g.writeName(FRIENDS);
            friends(g, property22, context);
        }
        g.writeEndObject();
    }

    private static void address(JsonGenerator g, Address v, RuntimeContext context)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        String property0 = v.getStreet();
        if (context.includeNulls || property0 != null) {
            str(g, STREET, property0);
        }
        String property1 = v.getCity();
        if (context.includeNulls || property1 != null) {
            str(g, CITY, property1);
        }
        String property2 = v.getState();
        if (context.includeNulls || property2 != null) {
            str(g, STATE, property2);
        }
        String property3 = v.getZip();
        if (context.includeNulls || property3 != null) {
            str(g, ZIP, property3);
        }
        String property4 = v.getCountry();
        if (context.includeNulls || property4 != null) {
            str(g, COUNTRY, property4);
        }
        g.writeEndObject();
    }

    private static void friends(JsonGenerator g, List<Friend> v, RuntimeContext context)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartArray();
        if (v instanceof java.util.RandomAccess) {
            for (int i = 0, n = v.size(); i < n; i++) friend(g, v.get(i), context);
        } else {
            for (Friend item : v) friend(g, item, context);
        }
        g.writeEndArray();
    }

    private static void friend(JsonGenerator g, Friend v, RuntimeContext context)
            throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        g.writeName(ID);
        g.writeNumber(v.getId());
        g.writeName(SINCE);
        g.writeNumber(v.getSince());
        String property2 = v.getName();
        if (context.includeNulls || property2 != null) {
            str(g, NAME, property2);
        }
        g.writeName(CLOSE);
        g.writeBoolean(v.isClose());
        g.writeEndObject();
    }

    private static void tags(JsonGenerator g, List<String> v, RuntimeContext context)
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

    private static void str(JsonGenerator g, SerializedString n, String v)
            throws IOException {
        g.writeName(n);
        if (v == null) g.writeNull();
        else g.writeString(v);
    }
}
