package org.sjf4j.testbench.binding.handwritten;

import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Jackson2Reader V1 baseline for the HandReadBenchmark User fixture. */
public final class Jackson2HandPojoReaderV1 {

    private static final StreamingReader.NameMatcher USER_FIELDS =
            Jackson2Reader.createNameMatcher(
                    "id", "createdAt", "updatedAt", "reputation", "loginCount", "age",
                    "active", "verified", "admin", "suspended", "score", "latitude",
                    "longitude", "username", "email", "displayName", "passwordHash", "bio",
                    "website", "department", "address", "tags", "friends");
    private static final StreamingReader.NameMatcher ADDRESS_FIELDS =
            Jackson2Reader.createNameMatcher("street", "city", "state", "zip", "country");
    private static final StreamingReader.NameMatcher FRIEND_FIELDS =
            Jackson2Reader.createNameMatcher("id", "name", "since", "close");

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
                    user.setId(reader.nextLongValue());
                    break;
                case 1:
                    user.setCreatedAt(reader.nextLongValue());
                    break;
                case 2:
                    user.setUpdatedAt(reader.nextLongValue());
                    break;
                case 3:
                    user.setReputation(reader.nextLongValue());
                    break;
                case 4:
                    user.setLoginCount(reader.nextIntValue());
                    break;
                case 5:
                    user.setAge(reader.nextIntValue());
                    break;
                case 6:
                    user.setActive(reader.nextBooleanValue());
                    break;
                case 7:
                    user.setVerified(reader.nextBooleanValue());
                    break;
                case 8:
                    user.setAdmin(reader.nextBooleanValue());
                    break;
                case 9:
                    user.setSuspended(reader.nextBooleanValue());
                    break;
                case 10:
                    user.setScore(reader.nextDoubleValue());
                    break;
                case 11:
                    user.setLatitude(reader.nextDoubleValue());
                    break;
                case 12:
                    user.setLongitude(reader.nextDoubleValue());
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
                    reader.skipNext();
                    break;
            }
            expected = field == expected && expected + 1 < 23
                    ? expected + 1 : StreamingReader.NameMatcher.UNKNOWN;
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
                    reader.skipNext();
                    break;
            }
            expected = field == expected && expected + 1 < 5
                    ? expected + 1 : StreamingReader.NameMatcher.UNKNOWN;
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
                    friend.setId(reader.nextLongValue());
                    break;
                case 1:
                    friend.setName(readString(reader));
                    break;
                case 2:
                    friend.setSince(reader.nextLongValue());
                    break;
                case 3:
                    friend.setClose(reader.nextBooleanValue());
                    break;
                default:
                    reader.skipNext();
                    break;
            }
            expected = field == expected && expected + 1 < 4
                    ? expected + 1 : StreamingReader.NameMatcher.UNKNOWN;
        }
        return friend;
    }

    private static String readString(Jackson2Reader reader) throws IOException {
        return reader.nextIfNull() ? null : reader.nextStringValue();
    }
}
