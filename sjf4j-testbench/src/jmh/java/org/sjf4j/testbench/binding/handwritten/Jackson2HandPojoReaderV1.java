package org.sjf4j.testbench.binding.handwritten;

import org.sjf4j.backend.jackson2.binding.Jackson2Parser;
import org.sjf4j.backend.jackson2.binding.Jackson2NameMatcher;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;
import org.sjf4j.value.ValueCodec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Jackson2Parser V1 baseline for the HandReadBenchmark User fixture. */
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

    public static User readUser(Jackson2Parser parser) throws IOException {
        parser.startRead();
        if (parser.nextIfNull()) {
            parser.endRead();
            return null;
        }

        User user = new User();
        parser.startObject();
        int expected = 0;
        while (!parser.nextIfObjectEnd()) {
            int field = parser.nextNameMatch(USER_FIELDS, expected);
            switch (field) {
                case 0:
                    user.setId(parser.readLongValue());
                    break;
                case 1:
                    user.setCreatedAt(parser.readLongValue());
                    break;
                case 2:
                    user.setUpdatedAt(parser.readLongValue());
                    break;
                case 3:
                    user.setReputation(parser.readLongValue());
                    break;
                case 4:
                    user.setLoginCount(parser.readIntValue());
                    break;
                case 5:
                    user.setAge(parser.readIntValue());
                    break;
                case 6:
                    user.setActive(parser.readBooleanValue());
                    break;
                case 7:
                    user.setVerified(parser.readBooleanValue());
                    break;
                case 8:
                    user.setAdmin(parser.readBooleanValue());
                    break;
                case 9:
                    user.setSuspended(parser.readBooleanValue());
                    break;
                case 10:
                    user.setScore(parser.readDoubleValue());
                    break;
                case 11:
                    user.setLatitude(parser.readDoubleValue());
                    break;
                case 12:
                    user.setLongitude(parser.readDoubleValue());
                    break;
                case 13:
                    user.setUsername(readString(parser));
                    break;
                case 14:
                    user.setEmail(readString(parser));
                    break;
                case 15:
                    user.setDisplayName(readString(parser));
                    break;
                case 16:
                    user.setPasswordHash(readString(parser));
                    break;
                case 17:
                    user.setBio(readString(parser));
                    break;
                case 18:
                    user.setWebsite(ValueCodec.URI_CODEC.rawToValue(readString(parser)));
                    break;
                case 19:
                    user.setDepartment(readString(parser));
                    break;
                case 20:
                    user.setAddress(readAddress(parser));
                    break;
                case 21:
                    user.setTags(readStrings(parser));
                    break;
                case 22:
                    user.setFriends(readFriends(parser));
                    break;
                default:
                    parser.skipNode();
                    break;
            }
            expected = field == expected && expected + 1 < 23
                    ? expected + 1 : NameMatcher.UNKNOWN;
        }
        parser.endRead();
        return user;
    }

    private static Address readAddress(Jackson2Parser parser) throws IOException {
        if (parser.nextIfNull()) {
            return null;
        }

        Address address = new Address();
        parser.startObject();
        int expected = 0;
        while (!parser.nextIfObjectEnd()) {
            int field = parser.nextNameMatch(ADDRESS_FIELDS, expected);
            switch (field) {
                case 0:
                    address.setStreet(readString(parser));
                    break;
                case 1:
                    address.setCity(readString(parser));
                    break;
                case 2:
                    address.setState(readString(parser));
                    break;
                case 3:
                    address.setZip(readString(parser));
                    break;
                case 4:
                    address.setCountry(readString(parser));
                    break;
                default:
                    parser.skipNode();
                    break;
            }
            expected = field == expected && expected + 1 < 5
                    ? expected + 1 : NameMatcher.UNKNOWN;
        }
        return address;
    }

    private static List<String> readStrings(Jackson2Parser parser) throws IOException {
        if (parser.nextIfNull()) {
            return null;
        }

        List<String> strings = new ArrayList<>();
        parser.startArray();
        while (!parser.nextIfArrayEnd()) {
            strings.add(readString(parser));
        }
        return strings;
    }

    private static List<Friend> readFriends(Jackson2Parser parser) throws IOException {
        if (parser.nextIfNull()) {
            return null;
        }

        List<Friend> friends = new ArrayList<>();
        parser.startArray();
        while (!parser.nextIfArrayEnd()) {
            friends.add(readFriend(parser));
        }
        return friends;
    }

    private static Friend readFriend(Jackson2Parser parser) throws IOException {
        if (parser.nextIfNull()) {
            return null;
        }

        Friend friend = new Friend();
        parser.startObject();
        int expected = 0;
        while (!parser.nextIfObjectEnd()) {
            int field = parser.nextNameMatch(FRIEND_FIELDS, expected);
            switch (field) {
                case 0:
                    friend.setId(parser.readLongValue());
                    break;
                case 1:
                    friend.setName(readString(parser));
                    break;
                case 2:
                    friend.setSince(parser.readLongValue());
                    break;
                case 3:
                    friend.setClose(parser.readBooleanValue());
                    break;
                default:
                    parser.skipNode();
                    break;
            }
            expected = field == expected && expected + 1 < 4
                    ? expected + 1 : NameMatcher.UNKNOWN;
        }
        return friend;
    }

    private static String readString(Jackson2Parser parser) throws IOException {
        return parser.readString();
    }
}
