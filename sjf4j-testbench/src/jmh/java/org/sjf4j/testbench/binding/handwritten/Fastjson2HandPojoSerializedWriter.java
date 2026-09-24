package org.sjf4j.testbench.binding.handwritten;

import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Direct Fastjson2 writer using pre-serialized UTF-8 or UTF-16 field names. */
public final class Fastjson2HandPojoSerializedWriter {
    private static final byte[] ID8 = name8("id");
    private static final byte[] USERNAME8 = name8("username");
    private static final byte[] EMAIL8 = name8("email");
    private static final byte[] DISPLAY_NAME8 = name8("displayName");
    private static final byte[] PASSWORD_HASH8 = name8("passwordHash");
    private static final byte[] BIO8 = name8("bio");
    private static final byte[] WEBSITE8 = name8("website");
    private static final byte[] DEPARTMENT8 = name8("department");
    private static final byte[] CREATED_AT8 = name8("createdAt");
    private static final byte[] UPDATED_AT8 = name8("updatedAt");
    private static final byte[] LOGIN_COUNT8 = name8("loginCount");
    private static final byte[] REPUTATION8 = name8("reputation");
    private static final byte[] ACTIVE8 = name8("active");
    private static final byte[] VERIFIED8 = name8("verified");
    private static final byte[] ADMIN8 = name8("admin");
    private static final byte[] SUSPENDED8 = name8("suspended");
    private static final byte[] SCORE8 = name8("score");
    private static final byte[] LATITUDE8 = name8("latitude");
    private static final byte[] LONGITUDE8 = name8("longitude");
    private static final byte[] AGE8 = name8("age");
    private static final byte[] ADDRESS8 = name8("address");
    private static final byte[] TAGS8 = name8("tags");
    private static final byte[] FRIENDS8 = name8("friends");
    private static final byte[] STREET8 = name8("street");
    private static final byte[] CITY8 = name8("city");
    private static final byte[] STATE8 = name8("state");
    private static final byte[] ZIP8 = name8("zip");
    private static final byte[] COUNTRY8 = name8("country");
    private static final byte[] NAME8 = name8("name");
    private static final byte[] SINCE8 = name8("since");
    private static final byte[] CLOSE8 = name8("close");
    private static final char[] ID16 = name16("id");
    private static final char[] USERNAME16 = name16("username");
    private static final char[] EMAIL16 = name16("email");
    private static final char[] DISPLAY_NAME16 = name16("displayName");
    private static final char[] PASSWORD_HASH16 = name16("passwordHash");
    private static final char[] BIO16 = name16("bio");
    private static final char[] WEBSITE16 = name16("website");
    private static final char[] DEPARTMENT16 = name16("department");
    private static final char[] CREATED_AT16 = name16("createdAt");
    private static final char[] UPDATED_AT16 = name16("updatedAt");
    private static final char[] LOGIN_COUNT16 = name16("loginCount");
    private static final char[] REPUTATION16 = name16("reputation");
    private static final char[] ACTIVE16 = name16("active");
    private static final char[] VERIFIED16 = name16("verified");
    private static final char[] ADMIN16 = name16("admin");
    private static final char[] SUSPENDED16 = name16("suspended");
    private static final char[] SCORE16 = name16("score");
    private static final char[] LATITUDE16 = name16("latitude");
    private static final char[] LONGITUDE16 = name16("longitude");
    private static final char[] AGE16 = name16("age");
    private static final char[] ADDRESS16 = name16("address");
    private static final char[] TAGS16 = name16("tags");
    private static final char[] FRIENDS16 = name16("friends");
    private static final char[] STREET16 = name16("street");
    private static final char[] CITY16 = name16("city");
    private static final char[] STATE16 = name16("state");
    private static final char[] ZIP16 = name16("zip");
    private static final char[] COUNTRY16 = name16("country");
    private static final char[] NAME16 = name16("name");
    private static final char[] SINCE16 = name16("since");
    private static final char[] CLOSE16 = name16("close");

    private Fastjson2HandPojoSerializedWriter() {
    }

    public static void writeUser(JSONWriter writer, User user) {
        if (writer.isUTF8() && !writer.isUseSingleQuotes()) {
            userUtf8(writer, user);
        } else if (writer.isUTF16() && !writer.isUseSingleQuotes()) {
            userUtf16(writer, user);
        } else {
            Fastjson2HandPojoStringedWriter.writeUser(writer, user);
        }
    }

    private static void userUtf8(JSONWriter w, User v) {
        if (v == null) {
            w.writeNull();
            return;
        }
        w.startObject();
        w.writeNameRaw(ID8);
        w.writeInt64(v.getId());
        string8(w, USERNAME8, v.getUsername());
        string8(w, EMAIL8, v.getEmail());
        string8(w, DISPLAY_NAME8, v.getDisplayName());
        string8(w, PASSWORD_HASH8, v.getPasswordHash());
        string8(w, BIO8, v.getBio());
        string8(w, WEBSITE8, v.getWebsite());
        string8(w, DEPARTMENT8, v.getDepartment());
        w.writeNameRaw(CREATED_AT8);
        w.writeInt64(v.getCreatedAt());
        w.writeNameRaw(UPDATED_AT8);
        w.writeInt64(v.getUpdatedAt());
        w.writeNameRaw(LOGIN_COUNT8);
        w.writeInt32(v.getLoginCount());
        w.writeNameRaw(REPUTATION8);
        w.writeInt64(v.getReputation());
        w.writeNameRaw(ACTIVE8);
        w.writeBool(v.isActive());
        w.writeNameRaw(VERIFIED8);
        w.writeBool(v.isVerified());
        w.writeNameRaw(ADMIN8);
        w.writeBool(v.isAdmin());
        w.writeNameRaw(SUSPENDED8);
        w.writeBool(v.isSuspended());
        w.writeNameRaw(SCORE8);
        w.writeDouble(v.getScore());
        w.writeNameRaw(LATITUDE8);
        w.writeDouble(v.getLatitude());
        w.writeNameRaw(LONGITUDE8);
        w.writeDouble(v.getLongitude());
        w.writeNameRaw(AGE8);
        w.writeInt32(v.getAge());
        w.writeNameRaw(ADDRESS8);
        address8(w, v.getAddress());
        w.writeNameRaw(TAGS8);
        strings(w, v.getTags());
        w.writeNameRaw(FRIENDS8);
        friends8(w, v.getFriends());
        w.endObject();
    }

    private static void userUtf16(JSONWriter w, User v) {
        if (v == null) {
            w.writeNull();
            return;
        }
        w.startObject();
        w.writeNameRaw(ID16);
        w.writeInt64(v.getId());
        string16(w, USERNAME16, v.getUsername());
        string16(w, EMAIL16, v.getEmail());
        string16(w, DISPLAY_NAME16, v.getDisplayName());
        string16(w, PASSWORD_HASH16, v.getPasswordHash());
        string16(w, BIO16, v.getBio());
        string16(w, WEBSITE16, v.getWebsite());
        string16(w, DEPARTMENT16, v.getDepartment());
        w.writeNameRaw(CREATED_AT16);
        w.writeInt64(v.getCreatedAt());
        w.writeNameRaw(UPDATED_AT16);
        w.writeInt64(v.getUpdatedAt());
        w.writeNameRaw(LOGIN_COUNT16);
        w.writeInt32(v.getLoginCount());
        w.writeNameRaw(REPUTATION16);
        w.writeInt64(v.getReputation());
        w.writeNameRaw(ACTIVE16);
        w.writeBool(v.isActive());
        w.writeNameRaw(VERIFIED16);
        w.writeBool(v.isVerified());
        w.writeNameRaw(ADMIN16);
        w.writeBool(v.isAdmin());
        w.writeNameRaw(SUSPENDED16);
        w.writeBool(v.isSuspended());
        w.writeNameRaw(SCORE16);
        w.writeDouble(v.getScore());
        w.writeNameRaw(LATITUDE16);
        w.writeDouble(v.getLatitude());
        w.writeNameRaw(LONGITUDE16);
        w.writeDouble(v.getLongitude());
        w.writeNameRaw(AGE16);
        w.writeInt32(v.getAge());
        w.writeNameRaw(ADDRESS16);
        address16(w, v.getAddress());
        w.writeNameRaw(TAGS16);
        strings(w, v.getTags());
        w.writeNameRaw(FRIENDS16);
        friends16(w, v.getFriends());
        w.endObject();
    }

    private static void address8(JSONWriter w, Address v) {
        if (v == null) {
            w.writeNull();
            return;
        }
        w.startObject();
        string8(w, STREET8, v.getStreet());
        string8(w, CITY8, v.getCity());
        string8(w, STATE8, v.getState());
        string8(w, ZIP8, v.getZip());
        string8(w, COUNTRY8, v.getCountry());
        w.endObject();
    }

    private static void address16(JSONWriter w, Address v) {
        if (v == null) {
            w.writeNull();
            return;
        }
        w.startObject();
        string16(w, STREET16, v.getStreet());
        string16(w, CITY16, v.getCity());
        string16(w, STATE16, v.getState());
        string16(w, ZIP16, v.getZip());
        string16(w, COUNTRY16, v.getCountry());
        w.endObject();
    }

    private static void friends8(JSONWriter w, List<Friend> vs) {
        if (vs == null) {
            w.writeNull();
            return;
        }
        w.startArray();
        int i = 0;
        int n = vs.size();
        for (; i < n; i++) {
            if (i != 0) {
                w.writeComma();
            }
            Friend v = vs.get(i);
            if (v == null) {
                w.writeNull();
                continue;
            }
            w.startObject();
            w.writeNameRaw(ID8);
            w.writeInt64(v.getId());
            string8(w, NAME8, v.getName());
            w.writeNameRaw(SINCE8);
            w.writeInt64(v.getSince());
            w.writeNameRaw(CLOSE8);
            w.writeBool(v.isClose());
            w.endObject();
        }
        w.endArray();
    }

    private static void friends16(JSONWriter w, List<Friend> vs) {
        if (vs == null) {
            w.writeNull();
            return;
        }
        w.startArray();
        int i = 0;
        int n = vs.size();
        for (; i < n; i++) {
            if (i != 0) {
                w.writeComma();
            }
            Friend v = vs.get(i);
            if (v == null) {
                w.writeNull();
                continue;
            }
            w.startObject();
            w.writeNameRaw(ID16);
            w.writeInt64(v.getId());
            string16(w, NAME16, v.getName());
            w.writeNameRaw(SINCE16);
            w.writeInt64(v.getSince());
            w.writeNameRaw(CLOSE16);
            w.writeBool(v.isClose());
            w.endObject();
        }
        w.endArray();
    }

    private static void strings(JSONWriter w, List<String> vs) {
        if (vs == null) {
            w.writeNull();
            return;
        }
        w.startArray();
        int i = 0;
        int n = vs.size();
        for (; i < n; i++) {
            if (i != 0) {
                w.writeComma();
            }
            String v = vs.get(i);
            if (v == null) {
                w.writeNull();
            } else {
                w.writeString(v);
            }
        }
        w.endArray();
    }

    private static void string8(JSONWriter w, byte[] name, String v) {
        w.writeNameRaw(name);
        if (v == null) {
            w.writeNull();
        } else {
            w.writeString(v);
        }
    }

    private static void string16(JSONWriter w, char[] name, String v) {
        w.writeNameRaw(name);
        if (v == null) {
            w.writeNull();
        } else {
            w.writeString(v);
        }
    }

    private static byte[] name8(String name) {
        return ('"' + name + "\":").getBytes(StandardCharsets.US_ASCII);
    }

    private static char[] name16(String name) {
        return ('"' + name + "\":").toCharArray();
    }
}
