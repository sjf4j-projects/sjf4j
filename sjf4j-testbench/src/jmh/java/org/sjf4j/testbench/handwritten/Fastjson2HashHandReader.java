package org.sjf4j.testbench.handwritten;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.util.Fnv;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.util.ArrayList;
import java.util.List;

/** Direct Fastjson2 parser baseline using field-name hash dispatch for {@link User}. */
public final class Fastjson2HashHandReader {

    private static final long ID = Fnv.hashCode64("id");
    private static final long CREATED_AT = Fnv.hashCode64("createdAt");
    private static final long UPDATED_AT = Fnv.hashCode64("updatedAt");
    private static final long REPUTATION = Fnv.hashCode64("reputation");
    private static final long LOGIN_COUNT = Fnv.hashCode64("loginCount");
    private static final long AGE = Fnv.hashCode64("age");
    private static final long ACTIVE = Fnv.hashCode64("active");
    private static final long VERIFIED = Fnv.hashCode64("verified");
    private static final long ADMIN = Fnv.hashCode64("admin");
    private static final long SUSPENDED = Fnv.hashCode64("suspended");
    private static final long SCORE = Fnv.hashCode64("score");
    private static final long LATITUDE = Fnv.hashCode64("latitude");
    private static final long LONGITUDE = Fnv.hashCode64("longitude");
    private static final long USERNAME = Fnv.hashCode64("username");
    private static final long EMAIL = Fnv.hashCode64("email");
    private static final long DISPLAY_NAME = Fnv.hashCode64("displayName");
    private static final long PASSWORD_HASH = Fnv.hashCode64("passwordHash");
    private static final long BIO = Fnv.hashCode64("bio");
    private static final long WEBSITE = Fnv.hashCode64("website");
    private static final long DEPARTMENT = Fnv.hashCode64("department");
    private static final long ADDRESS = Fnv.hashCode64("address");
    private static final long TAGS = Fnv.hashCode64("tags");
    private static final long FRIENDS = Fnv.hashCode64("friends");

    private Fastjson2HashHandReader() {}

    public static User readUser(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfObjectStart()) {
            throw new IllegalStateException("expected object, but was " + reader.current());
        }
        User user = new User();
        while (!reader.nextIfObjectEnd()) {
            readUserField(reader, user, reader.readFieldNameHashCode());
        }
        return user;
    }

    private static void readUserField(JSONReader reader, User user, long hash) {
        if (hash == ID) user.setId(reader.readInt64Value());
        else if (hash == CREATED_AT) user.setCreatedAt(reader.readInt64Value());
        else if (hash == UPDATED_AT) user.setUpdatedAt(reader.readInt64Value());
        else if (hash == REPUTATION) user.setReputation(reader.readInt64Value());
        else if (hash == LOGIN_COUNT) user.setLoginCount(reader.readInt32Value());
        else if (hash == AGE) user.setAge(reader.readInt32Value());
        else if (hash == ACTIVE) user.setActive(reader.readBoolValue());
        else if (hash == VERIFIED) user.setVerified(reader.readBoolValue());
        else if (hash == ADMIN) user.setAdmin(reader.readBoolValue());
        else if (hash == SUSPENDED) user.setSuspended(reader.readBoolValue());
        else if (hash == SCORE) user.setScore(reader.readDoubleValue());
        else if (hash == LATITUDE) user.setLatitude(reader.readDoubleValue());
        else if (hash == LONGITUDE) user.setLongitude(reader.readDoubleValue());
        else if (hash == USERNAME) user.setUsername(reader.readString());
        else if (hash == EMAIL) user.setEmail(reader.readString());
        else if (hash == DISPLAY_NAME) user.setDisplayName(reader.readString());
        else if (hash == PASSWORD_HASH) user.setPasswordHash(reader.readString());
        else if (hash == BIO) user.setBio(reader.readString());
        else if (hash == WEBSITE) user.setWebsite(reader.readString());
        else if (hash == DEPARTMENT) user.setDepartment(reader.readString());
        else if (hash == ADDRESS) user.setAddress(readAddress(reader));
        else if (hash == TAGS) user.setTags(readStrings(reader));
        else if (hash == FRIENDS) user.setFriends(readFriends(reader));
        else reader.skipValue();
    }

    private static Address readAddress(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfObjectStart()) {
            throw new IllegalStateException("expected object, but was " + reader.current());
        }
        Address address = new Address();
        while (!reader.nextIfObjectEnd()) {
            String name = reader.readFieldName();
            if ("street".equals(name)) address.setStreet(reader.readString());
            else if ("city".equals(name)) address.setCity(reader.readString());
            else if ("state".equals(name)) address.setState(reader.readString());
            else if ("zip".equals(name)) address.setZip(reader.readString());
            else if ("country".equals(name)) address.setCountry(reader.readString());
            else reader.skipValue();
        }
        return address;
    }

    private static List<String> readStrings(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfArrayStart()) {
            throw new IllegalStateException("expected array, but was " + reader.current());
        }
        List<String> strings = new ArrayList<>();
        while (!reader.nextIfArrayEnd()) strings.add(reader.readString());
        return strings;
    }

    private static List<Friend> readFriends(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfArrayStart()) {
            throw new IllegalStateException("expected array, but was " + reader.current());
        }
        List<Friend> friends = new ArrayList<>();
        while (!reader.nextIfArrayEnd()) friends.add(readFriend(reader));
        return friends;
    }

    private static Friend readFriend(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfObjectStart()) {
            throw new IllegalStateException("expected object, but was " + reader.current());
        }
        Friend friend = new Friend();
        while (!reader.nextIfObjectEnd()) {
            String name = reader.readFieldName();
            if ("id".equals(name)) friend.setId(reader.readInt64Value());
            else if ("name".equals(name)) friend.setName(reader.readString());
            else if ("since".equals(name)) friend.setSince(reader.readInt64Value());
            else if ("close".equals(name)) friend.setClose(reader.readBoolValue());
            else reader.skipValue();
        }
        return friend;
    }

}
