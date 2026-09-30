package org.sjf4j.testbench.binding.handwritten;

import org.sjf4j.backend.jackson2.binding.Jackson2ReaderV2;
import org.sjf4j.binding.StreamingReaderV2;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Jackson2ReaderV2 baseline for the HandReadBenchmark User fixture. */
public final class Jackson2HandPojoReaderV2 {

    private static final StreamingReaderV2.NameMatcher USER_FIELDS =
            Jackson2ReaderV2.createNameMatcher(
                    "id", "createdAt", "updatedAt", "reputation", "loginCount", "age",
                    "active", "verified", "admin", "suspended", "score", "latitude",
                    "longitude", "username", "email", "displayName", "passwordHash", "bio",
                    "website", "department", "address", "tags", "friends");
    private static final StreamingReaderV2.NameMatcher ADDRESS_FIELDS =
            Jackson2ReaderV2.createNameMatcher("street", "city", "state", "zip", "country");
    private static final StreamingReaderV2.NameMatcher FRIEND_FIELDS =
            Jackson2ReaderV2.createNameMatcher("id", "name", "since", "close");

    private Jackson2HandPojoReaderV2() {}

    public static User readUser(Jackson2ReaderV2 reader) throws IOException {
        reader.startDocument();
        if (reader.isNull()) {
            reader.readNull();
            reader.endDocument();
            return null;
        }

        User user = new User();
        reader.beginObject();
        int expected = 0;
        int field;

        while ((field = reader.nextObjectField(USER_FIELDS, expected))
                != StreamingReaderV2.END_OF_OBJECT) {
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
                    user.setUsername(reader.readStringOrNull());
                    break;
                case 14:
                    user.setEmail(reader.readStringOrNull());
                    break;
                case 15:
                    user.setDisplayName(reader.readStringOrNull());
                    break;
                case 16:
                    user.setPasswordHash(reader.readStringOrNull());
                    break;
                case 17:
                    user.setBio(reader.readStringOrNull());
                    break;
                case 18:
                    user.setWebsite(reader.readStringOrNull());
                    break;
                case 19:
                    user.setDepartment(reader.readStringOrNull());
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
                    reader.skipValue();
                    break;
            }

            if (expected >= 0) {
                expected = field == expected && expected + 1 < 23
                        ? expected + 1 : StreamingReaderV2.NO_EXPECTED_FIELD;
            }
        }

        reader.endObject();
        reader.endDocument();
        return user;
    }

    private static Address readAddress(Jackson2ReaderV2 reader) throws IOException {
        if (reader.isNull()) {
            reader.readNull();
            return null;
        }

        Address address = new Address();
        reader.beginObject();
        int expected = 0;
        int field;

        while ((field = reader.nextObjectField(ADDRESS_FIELDS, expected))
                != StreamingReaderV2.END_OF_OBJECT) {
            switch (field) {
                case 0:
                    address.setStreet(reader.readStringOrNull());
                    break;
                case 1:
                    address.setCity(reader.readStringOrNull());
                    break;
                case 2:
                    address.setState(reader.readStringOrNull());
                    break;
                case 3:
                    address.setZip(reader.readStringOrNull());
                    break;
                case 4:
                    address.setCountry(reader.readStringOrNull());
                    break;
                default:
                    reader.skipValue();
                    break;
            }

            if (expected >= 0) {
                expected = field == expected && expected + 1 < 5
                        ? expected + 1 : StreamingReaderV2.NO_EXPECTED_FIELD;
            }
        }

        reader.endObject();
        return address;
    }

    private static List<String> readStrings(Jackson2ReaderV2 reader) throws IOException {
        if (reader.isNull()) {
            reader.readNull();
            return null;
        }

        List<String> strings = new ArrayList<>();
        reader.beginArray();
        while (reader.nextArrayElement()) {
            strings.add(reader.readStringOrNull());
        }
        reader.endArray();
        return strings;
    }

    private static List<Friend> readFriends(Jackson2ReaderV2 reader) throws IOException {
        if (reader.isNull()) {
            reader.readNull();
            return null;
        }

        List<Friend> friends = new ArrayList<>();
        reader.beginArray();
        while (reader.nextArrayElement()) {
            friends.add(readFriend(reader));
        }
        reader.endArray();
        return friends;
    }

    private static Friend readFriend(Jackson2ReaderV2 reader) throws IOException {
        if (reader.isNull()) {
            reader.readNull();
            return null;
        }

        Friend friend = new Friend();
        reader.beginObject();
        int expected = 0;
        int field;

        while ((field = reader.nextObjectField(FRIEND_FIELDS, expected))
                != StreamingReaderV2.END_OF_OBJECT) {
            switch (field) {
                case 0:
                    friend.setId(reader.readLongValue());
                    break;
                case 1:
                    friend.setName(reader.readStringOrNull());
                    break;
                case 2:
                    friend.setSince(reader.readLongValue());
                    break;
                case 3:
                    friend.setClose(reader.readBooleanValue());
                    break;
                default:
                    reader.skipValue();
                    break;
            }

            if (expected >= 0) {
                expected = field == expected && expected + 1 < 4
                        ? expected + 1 : StreamingReaderV2.NO_EXPECTED_FIELD;
            }
        }

        reader.endObject();
        return friend;
    }
}
