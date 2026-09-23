package org.sjf4j.testbench.handwritten;

import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Direct Fastjson2 writer using packed UTF-8 field-name writes where they fit. */
@SuppressWarnings("deprecation")
public final class Fastjson2HandPojoRawWriter {
    private static final long ID = pack("\"id\":");
    private static final long EMAIL = pack("\"email\":");
    private static final long BIO = pack("\"bio\":");
    private static final long WEBSITE = pack("\"website");
    private static final long ACTIVE = pack("\"active\"");
    private static final long ADMIN = pack("\"admin\":");
    private static final long SCORE = pack("\"score\":");
    private static final long AGE = pack("\"age\":");
    private static final long ADDRESS = pack("\"address");
    private static final long TAGS = pack("\"tags\":");
    private static final long STREET = pack("\"street\"");
    private static final long CITY = pack("\"city\":");
    private static final long STATE = pack("\"state\":");
    private static final long ZIP = pack("\"zip\":");
    private static final long COUNTRY = pack("\"country");
    private static final long NAME = pack("\"name\":");
    private static final long SINCE = pack("\"since\":");
    private static final long CLOSE = pack("\"close\":");
    private static final byte[] USERNAME8 = name8("username");
    private static final byte[] DISPLAY_NAME8 = name8("displayName");
    private static final byte[] PASSWORD_HASH8 = name8("passwordHash");
    private static final byte[] DEPARTMENT8 = name8("department");
    private static final byte[] CREATED_AT8 = name8("createdAt");
    private static final byte[] UPDATED_AT8 = name8("updatedAt");
    private static final byte[] LOGIN_COUNT8 = name8("loginCount");
    private static final byte[] REPUTATION8 = name8("reputation");
    private static final byte[] VERIFIED8 = name8("verified");
    private static final byte[] SUSPENDED8 = name8("suspended");
    private static final byte[] LATITUDE8 = name8("latitude");
    private static final byte[] LONGITUDE8 = name8("longitude");
    private static final byte[] FRIENDS8 = name8("friends");
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
    private static final Unsafe UNSAFE;
    private static final long STRING_VALUE_OFFSET;
    private static final long STRING_CODER_OFFSET;


    static {
        Unsafe unsafe = null;
        long valueOffset = 0;
        long coderOffset = 0;
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
            Field value = String.class.getDeclaredField("value");
            Field coder = String.class.getDeclaredField("coder");
            if (value.getType() != byte[].class || coder.getType() != byte.class) {
                unsafe = null;
            } else {
                valueOffset = unsafe.objectFieldOffset(value);
                coderOffset = unsafe.objectFieldOffset(coder);
            }
        } catch (Throwable ignored) {
            unsafe = null;
        }
        UNSAFE = unsafe;
        STRING_VALUE_OFFSET = valueOffset;
        STRING_CODER_OFFSET = coderOffset;
    }

    private Fastjson2HandPojoRawWriter() {
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
        w.writeName2Raw(ID);
        w.writeInt64(v.getId());
        w.writeNameRaw(USERNAME8);
        value(w, v.getUsername());
        w.writeName5Raw(EMAIL);
        value(w, v.getEmail());
        w.writeNameRaw(DISPLAY_NAME8);
        value(w, v.getDisplayName());
        w.writeNameRaw(PASSWORD_HASH8);
        value(w, v.getPasswordHash());
        w.writeName3Raw(BIO);
        value(w, v.getBio());
        w.writeName7Raw(WEBSITE);
        value(w, v.getWebsite());
        w.writeNameRaw(DEPARTMENT8);
        value(w, v.getDepartment());
        w.writeNameRaw(CREATED_AT8);
        w.writeInt64(v.getCreatedAt());
        w.writeNameRaw(UPDATED_AT8);
        w.writeInt64(v.getUpdatedAt());
        w.writeNameRaw(LOGIN_COUNT8);
        w.writeInt32(v.getLoginCount());
        w.writeNameRaw(REPUTATION8);
        w.writeInt64(v.getReputation());
        w.writeName6Raw(ACTIVE);
        w.writeBool(v.isActive());
        w.writeNameRaw(VERIFIED8);
        w.writeBool(v.isVerified());
        w.writeName5Raw(ADMIN);
        w.writeBool(v.isAdmin());
        w.writeNameRaw(SUSPENDED8);
        w.writeBool(v.isSuspended());
        w.writeName5Raw(SCORE);
        w.writeDouble(v.getScore());
        w.writeNameRaw(LATITUDE8);
        w.writeDouble(v.getLatitude());
        w.writeNameRaw(LONGITUDE8);
        w.writeDouble(v.getLongitude());
        w.writeName3Raw(AGE);
        w.writeInt32(v.getAge());
        w.writeName7Raw(ADDRESS);
        address8(w, v.getAddress());
        w.writeName4Raw(TAGS);
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
        w.writeName6Raw(STREET);
        value(w, v.getStreet());
        w.writeName4Raw(CITY);
        value(w, v.getCity());
        w.writeName5Raw(STATE);
        value(w, v.getState());
        w.writeName3Raw(ZIP);
        value(w, v.getZip());
        w.writeName7Raw(COUNTRY);
        value(w, v.getCountry());
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
            w.writeName2Raw(ID);
            w.writeInt64(v.getId());
            w.writeName4Raw(NAME);
            value(w, v.getName());
            w.writeName5Raw(SINCE);
            w.writeInt64(v.getSince());
            w.writeName5Raw(CLOSE);
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
                value(w, v);
            }
        }
        w.endArray();
    }

    private static void string16(JSONWriter w, char[] name, String v) {
        w.writeNameRaw(name);
        value(w, v);
    }

    private static void value(JSONWriter w, String v) {
        if (v == null) {
            w.writeNull();
        } else if (UNSAFE == null) {
            w.writeString(v);
        } else {
            byte[] bytes = (byte[]) UNSAFE.getObject(v, STRING_VALUE_OFFSET);
            if (UNSAFE.getByte(v, STRING_CODER_OFFSET) == 0) {
                w.writeStringLatin1(bytes);
            } else {
                w.writeStringUTF16(bytes);
            }
        }
    }

    private static byte[] name8(String name) {
        return ('"' + name + "\":").getBytes(StandardCharsets.US_ASCII);
    }

    private static char[] name16(String name) {
        return ('"' + name + "\":").toCharArray();
    }

    private static long pack(String value) {
        long packed = 0;
        int i = 0;
        for (; i < value.length(); i++) {
            packed |= (long) value.charAt(i)
                    << ((ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? i : 7 - i) << 3);
        }
        return packed;
    }
}
