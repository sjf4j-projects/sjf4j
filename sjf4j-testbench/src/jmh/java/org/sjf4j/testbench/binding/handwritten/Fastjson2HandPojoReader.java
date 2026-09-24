package org.sjf4j.testbench.binding.handwritten;

import com.alibaba.fastjson2.JSONReader;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.util.ArrayList;
import java.util.List;

/** Direct Fastjson2 baseline for {@link User}. */
public final class Fastjson2HandPojoReader {

    private Fastjson2HandPojoReader() {}

    public static User readUser(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfObjectStart()) throw new IllegalStateException("expected object, but was " + reader.current());
        User user = new User();
        while (!reader.nextIfObjectEnd()) {
            String name = reader.readFieldName();
            if ("id".equals(name)) user.setId(reader.readInt64Value());
            else if ("createdAt".equals(name)) user.setCreatedAt(reader.readInt64Value());
            else if ("updatedAt".equals(name)) user.setUpdatedAt(reader.readInt64Value());
            else if ("reputation".equals(name)) user.setReputation(reader.readInt64Value());
            else if ("loginCount".equals(name)) user.setLoginCount(reader.readInt32Value());
            else if ("age".equals(name)) user.setAge(reader.readInt32Value());
            else if ("active".equals(name)) user.setActive(reader.readBoolValue());
            else if ("verified".equals(name)) user.setVerified(reader.readBoolValue());
            else if ("admin".equals(name)) user.setAdmin(reader.readBoolValue());
            else if ("suspended".equals(name)) user.setSuspended(reader.readBoolValue());
            else if ("score".equals(name)) user.setScore(reader.readDoubleValue());
            else if ("latitude".equals(name)) user.setLatitude(reader.readDoubleValue());
            else if ("longitude".equals(name)) user.setLongitude(reader.readDoubleValue());
            else if ("username".equals(name)) user.setUsername(readString(reader));
            else if ("email".equals(name)) user.setEmail(readString(reader));
            else if ("displayName".equals(name)) user.setDisplayName(readString(reader));
            else if ("passwordHash".equals(name)) user.setPasswordHash(readString(reader));
            else if ("bio".equals(name)) user.setBio(readString(reader));
            else if ("website".equals(name)) user.setWebsite(readString(reader));
            else if ("department".equals(name)) user.setDepartment(readString(reader));
            else if ("address".equals(name)) user.setAddress(readAddress(reader));
            else if ("tags".equals(name)) user.setTags(readStrings(reader));
            else if ("friends".equals(name)) user.setFriends(readFriends(reader));
            else reader.skipValue();
        }
        return user;
    }

    private static Address readAddress(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfObjectStart()) throw new IllegalStateException("expected object, but was " + reader.current());
        Address address = new Address();
        while (!reader.nextIfObjectEnd()) {
            String name = reader.readFieldName();
            if ("street".equals(name)) address.setStreet(readString(reader));
            else if ("city".equals(name)) address.setCity(readString(reader));
            else if ("state".equals(name)) address.setState(readString(reader));
            else if ("zip".equals(name)) address.setZip(readString(reader));
            else if ("country".equals(name)) address.setCountry(readString(reader));
            else reader.skipValue();
        }
        return address;
    }

    private static List<String> readStrings(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfArrayStart()) throw new IllegalStateException("expected array, but was " + reader.current());
        List<String> strings = new ArrayList<>();
        while (!reader.nextIfArrayEnd()) strings.add(readString(reader));
        return strings;
    }

    private static List<Friend> readFriends(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfArrayStart()) throw new IllegalStateException("expected array, but was " + reader.current());
        List<Friend> friends = new ArrayList<>();
        while (!reader.nextIfArrayEnd()) friends.add(readFriend(reader));
        return friends;
    }

    private static Friend readFriend(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfObjectStart()) throw new IllegalStateException("expected object, but was " + reader.current());
        Friend friend = new Friend();
        while (!reader.nextIfObjectEnd()) {
            String name = reader.readFieldName();
            if ("id".equals(name)) friend.setId(reader.readInt64Value());
            else if ("name".equals(name)) friend.setName(readString(reader));
            else if ("since".equals(name)) friend.setSince(reader.readInt64Value());
            else if ("close".equals(name)) friend.setClose(reader.readBoolValue());
            else reader.skipValue();
        }
        return friend;
    }

    private static String readString(JSONReader reader) {
        return reader.nextIfNull() ? null : reader.readString();
    }
}
