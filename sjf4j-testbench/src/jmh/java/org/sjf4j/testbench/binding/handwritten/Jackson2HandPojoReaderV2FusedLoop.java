package org.sjf4j.testbench.binding.handwritten;

import org.sjf4j.backend.jackson2.binding.Jackson2ReaderV2;
import org.sjf4j.binding.StreamingReaderV2;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** V2 ordered-field prototype with one fused loop and switch for each POJO. */
public final class Jackson2HandPojoReaderV2FusedLoop {

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

    private Jackson2HandPojoReaderV2FusedLoop() {}

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
        boolean ordered = true;
        while (true) {
            int field;
            if (ordered && expected < 23) {
                if (reader.nextExpectedObjectField(USER_FIELDS, expected)) {
                    field = expected++;
                } else {
                    ordered = false;
                    field = reader.matchCurrentObjectName(USER_FIELDS);
                }
            } else {
                if (!reader.nextOrderedObjectField()) break;
                ordered = false;
                field = reader.matchCurrentObjectName(USER_FIELDS);
            }
            if (field == StreamingReaderV2.END_OF_OBJECT) break;

            switch (field) {
                case 0: user.setId(reader.nextLongValue()); break;
                case 1: user.setCreatedAt(reader.nextLongValue()); break;
                case 2: user.setUpdatedAt(reader.nextLongValue()); break;
                case 3: user.setReputation(reader.nextLongValue()); break;
                case 4: user.setLoginCount(reader.nextIntValue()); break;
                case 5: user.setAge(reader.nextIntValue()); break;
                case 6: user.setActive(reader.nextBooleanValue()); break;
                case 7: user.setVerified(reader.nextBooleanValue()); break;
                case 8: user.setAdmin(reader.nextBooleanValue()); break;
                case 9: user.setSuspended(reader.nextBooleanValue()); break;
                case 10: user.setScore(reader.nextDoubleValue()); break;
                case 11: user.setLatitude(reader.nextDoubleValue()); break;
                case 12: user.setLongitude(reader.nextDoubleValue()); break;
                case 13: user.setUsername(reader.nextStringOrNull()); break;
                case 14: user.setEmail(reader.nextStringOrNull()); break;
                case 15: user.setDisplayName(reader.nextStringOrNull()); break;
                case 16: user.setPasswordHash(reader.nextStringOrNull()); break;
                case 17: user.setBio(reader.nextStringOrNull()); break;
                case 18: user.setWebsite(reader.nextStringOrNull()); break;
                case 19: user.setDepartment(reader.nextStringOrNull()); break;
                case 20:
                    reader.nextObjectValue();
                    user.setAddress(readAddress(reader));
                    break;
                case 21:
                    reader.nextObjectValue();
                    user.setTags(readStrings(reader));
                    break;
                case 22:
                    reader.nextObjectValue();
                    user.setFriends(readFriends(reader));
                    break;
                default:
                    reader.nextObjectValue();
                    reader.skipValue();
                    break;
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
        boolean ordered = true;
        while (true) {
            int field;
            if (ordered && expected < 5) {
                if (reader.nextExpectedObjectField(ADDRESS_FIELDS, expected)) {
                    field = expected++;
                } else {
                    ordered = false;
                    field = reader.matchCurrentObjectName(ADDRESS_FIELDS);
                }
            } else {
                if (!reader.nextOrderedObjectField()) break;
                ordered = false;
                field = reader.matchCurrentObjectName(ADDRESS_FIELDS);
            }
            if (field == StreamingReaderV2.END_OF_OBJECT) break;

            switch (field) {
                case 0: address.setStreet(reader.nextStringOrNull()); break;
                case 1: address.setCity(reader.nextStringOrNull()); break;
                case 2: address.setState(reader.nextStringOrNull()); break;
                case 3: address.setZip(reader.nextStringOrNull()); break;
                case 4: address.setCountry(reader.nextStringOrNull()); break;
                default:
                    reader.nextObjectValue();
                    reader.skipValue();
                    break;
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
        while (reader.nextArrayElement()) strings.add(reader.readStringOrNull());
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
        while (reader.nextArrayElement()) friends.add(readFriend(reader));
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
        boolean ordered = true;
        while (true) {
            int field;
            if (ordered && expected < 4) {
                if (reader.nextExpectedObjectField(FRIEND_FIELDS, expected)) {
                    field = expected++;
                } else {
                    ordered = false;
                    field = reader.matchCurrentObjectName(FRIEND_FIELDS);
                }
            } else {
                if (!reader.nextOrderedObjectField()) break;
                ordered = false;
                field = reader.matchCurrentObjectName(FRIEND_FIELDS);
            }
            if (field == StreamingReaderV2.END_OF_OBJECT) break;

            switch (field) {
                case 0: friend.setId(reader.nextLongValue()); break;
                case 1: friend.setName(reader.nextStringOrNull()); break;
                case 2: friend.setSince(reader.nextLongValue()); break;
                case 3: friend.setClose(reader.nextBooleanValue()); break;
                default:
                    reader.nextObjectValue();
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
        return friend;
    }
}
