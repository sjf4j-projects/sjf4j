package org.sjf4j.testbench.binding.handwritten;

import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;
import org.sjf4j.testbench.model.Users;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Direct Fastjson2 UTF-8 writer for {@link Users}. */
public final class Fastjson2HandLargeWriter {
  private static final byte[] USERS = name("users");
  private static final byte[] TOTAL = name("total");
  private static final byte[] PAGE = name("page");
  private static final byte[] GENERATED_AT = name("generatedAt");
  private static final byte[] ID = name("id");
  private static final byte[] USERNAME = name("username");
  private static final byte[] EMAIL = name("email");
  private static final byte[] DISPLAY_NAME = name("displayName");
  private static final byte[] PASSWORD_HASH = name("passwordHash");
  private static final byte[] BIO = name("bio");
  private static final byte[] WEBSITE = name("website");
  private static final byte[] DEPARTMENT = name("department");
  private static final byte[] CREATED_AT = name("createdAt");
  private static final byte[] UPDATED_AT = name("updatedAt");
  private static final byte[] LOGIN_COUNT = name("loginCount");
  private static final byte[] REPUTATION = name("reputation");
  private static final byte[] ACTIVE = name("active");
  private static final byte[] VERIFIED = name("verified");
  private static final byte[] ADMIN = name("admin");
  private static final byte[] SUSPENDED = name("suspended");
  private static final byte[] SCORE = name("score");
  private static final byte[] LATITUDE = name("latitude");
  private static final byte[] LONGITUDE = name("longitude");
  private static final byte[] AGE = name("age");
  private static final byte[] ADDRESS = name("address");
  private static final byte[] TAGS = name("tags");
  private static final byte[] FRIENDS = name("friends");
  private static final byte[] STREET = name("street");
  private static final byte[] CITY = name("city");
  private static final byte[] STATE = name("state");
  private static final byte[] ZIP = name("zip");
  private static final byte[] COUNTRY = name("country");
  private static final byte[] NAME = name("name");
  private static final byte[] SINCE = name("since");
  private static final byte[] CLOSE = name("close");
  private static final long FRIEND_NAME = pack("\"name\":".getBytes(StandardCharsets.US_ASCII));

  private Fastjson2HandLargeWriter() {}

  public static void writeUsers(JSONWriter w, Users v) {
    if (v == null) {
      w.writeNull();
      return;
    }
    w.startObject();
    w.writeNameRaw(USERS);
    users(w, v.getUsers());
    w.writeNameRaw(TOTAL);
    w.writeInt32(v.getTotal());
    w.writeNameRaw(PAGE);
    w.writeInt32(v.getPage());
    w.writeNameRaw(GENERATED_AT);
    w.writeInt64(v.getGeneratedAt());
    w.endObject();
  }

  private static void users(JSONWriter w, List<User> v) {
    if (v == null) {
      w.writeNull();
      return;
    }
    w.startArray();
    for (int i = 0, n = v.size(); i < n; i++) {
      if (i > 0) w.writeComma();
      writeUser(w, v.get(i));
    }
    w.endArray();
  }

  public static void writeUser(JSONWriter w, User v) {
    if (v == null) {
      w.writeNull();
      return;
    }
    w.startObject();
    w.writeNameRaw(ID);
    w.writeInt64(v.getId());
    str(w, USERNAME, v.getUsername());
    str(w, EMAIL, v.getEmail());
    str(w, DISPLAY_NAME, v.getDisplayName());
    str(w, PASSWORD_HASH, v.getPasswordHash());
    str(w, BIO, v.getBio());
    str(w, WEBSITE, v.getWebsite());
    str(w, DEPARTMENT, v.getDepartment());
    w.writeNameRaw(CREATED_AT);
    w.writeInt64(v.getCreatedAt());
    w.writeNameRaw(UPDATED_AT);
    w.writeInt64(v.getUpdatedAt());
    w.writeNameRaw(LOGIN_COUNT);
    w.writeInt32(v.getLoginCount());
    w.writeNameRaw(REPUTATION);
    w.writeInt64(v.getReputation());
    w.writeNameRaw(ACTIVE);
    w.writeBool(v.isActive());
    w.writeNameRaw(VERIFIED);
    w.writeBool(v.isVerified());
    w.writeNameRaw(ADMIN);
    w.writeBool(v.isAdmin());
    w.writeNameRaw(SUSPENDED);
    w.writeBool(v.isSuspended());
    w.writeNameRaw(SCORE);
    w.writeDouble(v.getScore());
    w.writeNameRaw(LATITUDE);
    w.writeDouble(v.getLatitude());
    w.writeNameRaw(LONGITUDE);
    w.writeDouble(v.getLongitude());
    w.writeNameRaw(AGE);
    w.writeInt32(v.getAge());
    w.writeNameRaw(ADDRESS);
    address(w, v.getAddress());
    w.writeNameRaw(TAGS);
    tags(w, v.getTags());
    w.writeNameRaw(FRIENDS);
    friends(w, v.getFriends());
    w.endObject();
  }

  private static void address(JSONWriter w, Address v) {
    if (v == null) {
      w.writeNull();
      return;
    }
    w.startObject();
    str(w, STREET, v.getStreet());
    str(w, CITY, v.getCity());
    str(w, STATE, v.getState());
    str(w, ZIP, v.getZip());
    str(w, COUNTRY, v.getCountry());
    w.endObject();
  }

  private static void friends(JSONWriter w, List<Friend> v) {
    if (v == null) {
      w.writeNull();
      return;
    }
    w.startArray();
    for (int i = 0, n = v.size(); i < n; i++) {
      if (i > 0) w.writeComma();
      Friend f = v.get(i);
      if (f == null) {
        w.writeNull();
        continue;
      }
      w.startObject();
      w.writeNameRaw(ID);
      w.writeInt64(f.getId());
      w.writeName4Raw(FRIEND_NAME);
      if (f.getName() == null) w.writeNull();
      else w.writeString(f.getName());
      w.writeNameRaw(SINCE);
      w.writeInt64(f.getSince());
      w.writeNameRaw(CLOSE);
      w.writeBool(f.isClose());
      w.endObject();
    }
    w.endArray();
  }

  private static void tags(JSONWriter w, List<String> v) {
    if (v == null) {
      w.writeNull();
      return;
    }
    w.startArray();
    for (int i = 0, n = v.size(); i < n; i++) {
      if (i > 0) w.writeComma();
      String s = v.get(i);
      if (s == null) w.writeNull();
      else w.writeString(s);
    }
    w.endArray();
  }

  private static void str(JSONWriter w, byte[] n, String v) {
    w.writeNameRaw(n);
    if (v == null) w.writeNull();
    else w.writeString(v);
  }

  private static byte[] name(String name) {
    return ("\"" + name + "\":").getBytes(StandardCharsets.US_ASCII);
  }

  private static long pack(byte[] bytes) {
    long value = 0;
    if (java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN) {
      for (int i = 0; i < bytes.length; i++) value |= (long) (bytes[i] & 0xff) << (i << 3);
    } else {
      for (int i = 0; i < bytes.length; i++) value |= (long) (bytes[i] & 0xff) << ((7 - i) << 3);
    }
    return value;
  }
}
