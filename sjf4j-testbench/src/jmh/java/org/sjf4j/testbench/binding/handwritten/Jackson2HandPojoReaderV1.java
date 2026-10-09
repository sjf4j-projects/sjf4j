package org.sjf4j.testbench.binding.handwritten;

import org.sjf4j.backend.jackson2.binding.Jackson2Reader;
import org.sjf4j.backend.jackson2.binding.Jackson2NameMatcher;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Jackson2Reader V1 baseline for the HandReadBenchmark User fixture. */
public final class Jackson2HandPojoReaderV1 {

    private static final NameMatcher USER_FIELDS =
            new Jackson2NameMatcher(
                    "id", "createdAt", "updatedAt", "reputation", "loginCount", "age",
                    "active", "verified", "admin", "suspended", "score", "latitude",
                    "longitude", "username", "email", "displayName", "passwordHash", "bio",
                    "website", "department", "address", "tags", "friends");
    private static final NameMatcher ADDRESS_FIELDS =
            new Jackson2NameMatcher("street", "city", "state", "zip", "country");
    private static final NameMatcher FRIEND_FIELDS =
            new Jackson2NameMatcher("id", "name", "since", "close");

    private Jackson2HandPojoReaderV1() {}

    public static User readUser(Jackson2Reader reader) throws IOException {
        reader.startDocument();
        if (reader.nextIfNull()) {
            reader.endDocument();
            return null;
        }

        User user = new User();
        reader.startObject();
        int expected = 0;
        while (!reader.nextIfObjectEnd()) {
            int field = reader.nextNameMatch(USER_FIELDS, expected);
            switch (field) {
                case 0:
                    user.setId(reader.readLongValue());
                    break;
                case 1:
                    user.setCreatedAt(reader.readLongValue());
                    break;
                case 2:
                    user.setUpdatedAt(reader.readLongValue());
                    break;
                case 3:
                    user.setReputation(reader.readLongValue());
                    break;
                case 4:
                    user.setLoginCount(reader.readIntValue());
                    break;
                case 5:
                    user.setAge(reader.readIntValue());
                    break;
                case 6:
                    user.setActive(reader.readBooleanValue());
                    break;
                case 7:
                    user.setVerified(reader.readBooleanValue());
                    break;
                case 8:
                    user.setAdmin(reader.readBooleanValue());
                    break;
                case 9:
                    user.setSuspended(reader.readBooleanValue());
                    break;
                case 10:
                    user.setScore(reader.readDoubleValue());
                    break;
                case 11:
                    user.setLatitude(reader.readDoubleValue());
                    break;
                case 12:
                    user.setLongitude(reader.readDoubleValue());
                    break;
                case 13:
                    user.setUsername(readString(reader));
                    break;
                case 14:
                    user.setEmail(readString(reader));
                    break;
                case 15:
                    user.setDisplayName(readString(reader));
                    break;
                case 16:
                    user.setPasswordHash(readString(reader));
                    break;
                case 17:
                    user.setBio(readString(reader));
                    break;
                case 18:
                    user.setWebsite(readString(reader));
                    break;
                case 19:
                    user.setDepartment(readString(reader));
                    break;
                case 20:
                    user.setAddress(readAddress(reader));
                    break;
                case 21:
                    user.setTags(readStrings(reader));
                    break;
                case 22:
                    user.setFriends(readFriends(reader));
                    break;
                default:
                    reader.skipNode();
                    break;
            }
            expected = field == expected && expected + 1 < 23
                    ? expected + 1 : NameMatcher.UNKNOWN;
        }
        reader.endDocument();
        return user;
    }

    private static Address readAddress(Jackson2Reader reader) throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }

        Address address = new Address();
        reader.startObject();
        int expected = 0;
        while (!reader.nextIfObjectEnd()) {
            int field = reader.nextNameMatch(ADDRESS_FIELDS, expected);
            switch (field) {
                case 0:
                    address.setStreet(readString(reader));
                    break;
                case 1:
                    address.setCity(readString(reader));
                    break;
                case 2:
                    address.setState(readString(reader));
                    break;
                case 3:
                    address.setZip(readString(reader));
                    break;
                case 4:
                    address.setCountry(readString(reader));
                    break;
                default:
                    reader.skipNode();
                    break;
            }
            expected = field == expected && expected + 1 < 5
                    ? expected + 1 : NameMatcher.UNKNOWN;
        }
        return address;
    }

    private static List<String> readStrings(Jackson2Reader reader) throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }

        List<String> strings = new ArrayList<>();
        reader.startArray();
        while (!reader.nextIfArrayEnd()) {
            strings.add(readString(reader));
        }
        return strings;
    }

    private static List<Friend> readFriends(Jackson2Reader reader) throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }

        List<Friend> friends = new ArrayList<>();
        reader.startArray();
        while (!reader.nextIfArrayEnd()) {
            friends.add(readFriend(reader));
        }
        return friends;
    }

    private static Friend readFriend(Jackson2Reader reader) throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }

        Friend friend = new Friend();
        reader.startObject();
        int expected = 0;
        while (!reader.nextIfObjectEnd()) {
            int field = reader.nextNameMatch(FRIEND_FIELDS, expected);
            switch (field) {
                case 0:
                    friend.setId(reader.readLongValue());
                    break;
                case 1:
                    friend.setName(readString(reader));
                    break;
                case 2:
                    friend.setSince(reader.readLongValue());
                    break;
                case 3:
                    friend.setClose(reader.readBooleanValue());
                    break;
                default:
                    reader.skipNode();
                    break;
            }
            expected = field == expected && expected + 1 < 4
                    ? expected + 1 : NameMatcher.UNKNOWN;
        }
        return friend;
    }

    private static String readString(Jackson2Reader reader) throws IOException {
        return reader.readString();
    }
}
