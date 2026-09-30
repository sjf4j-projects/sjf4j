package org.sjf4j.testbench.binding.handwritten;

import org.sjf4j.backend.jackson2.binding.Jackson2ReaderV2;
import org.sjf4j.binding.StreamingReaderV2;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Ordered generated-reader prototype using only the V2 reader API. */
public final class Jackson2HandPojoReaderV2Unrolled {

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

    private Jackson2HandPojoReaderV2Unrolled() {}

    public static User readUser(Jackson2ReaderV2 reader) throws IOException {
        reader.startDocument();
        if (reader.isNull()) {
            reader.readNull();
            reader.endDocument();
            return null;
        }

        User user = new User();
        reader.beginObject();
        if (!reader.nextExpectedObjectField(USER_FIELDS, 0)) return readUserFallback(reader, user);
        user.setId(reader.nextLongValue());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 1)) return readUserFallback(reader, user);
        user.setCreatedAt(reader.nextLongValue());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 2)) return readUserFallback(reader, user);
        user.setUpdatedAt(reader.nextLongValue());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 3)) return readUserFallback(reader, user);
        user.setReputation(reader.nextLongValue());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 4)) return readUserFallback(reader, user);
        user.setLoginCount(reader.nextIntValue());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 5)) return readUserFallback(reader, user);
        user.setAge(reader.nextIntValue());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 6)) return readUserFallback(reader, user);
        user.setActive(reader.nextBooleanValue());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 7)) return readUserFallback(reader, user);
        user.setVerified(reader.nextBooleanValue());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 8)) return readUserFallback(reader, user);
        user.setAdmin(reader.nextBooleanValue());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 9)) return readUserFallback(reader, user);
        user.setSuspended(reader.nextBooleanValue());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 10)) return readUserFallback(reader, user);
        user.setScore(reader.nextDoubleValue());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 11)) return readUserFallback(reader, user);
        user.setLatitude(reader.nextDoubleValue());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 12)) return readUserFallback(reader, user);
        user.setLongitude(reader.nextDoubleValue());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 13)) return readUserFallback(reader, user);
        user.setUsername(reader.nextStringOrNull());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 14)) return readUserFallback(reader, user);
        user.setEmail(reader.nextStringOrNull());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 15)) return readUserFallback(reader, user);
        user.setDisplayName(reader.nextStringOrNull());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 16)) return readUserFallback(reader, user);
        user.setPasswordHash(reader.nextStringOrNull());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 17)) return readUserFallback(reader, user);
        user.setBio(reader.nextStringOrNull());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 18)) return readUserFallback(reader, user);
        user.setWebsite(reader.nextStringOrNull());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 19)) return readUserFallback(reader, user);
        user.setDepartment(reader.nextStringOrNull());
        if (!reader.nextExpectedObjectField(USER_FIELDS, 20)) return readUserFallback(reader, user);
        reader.nextObjectValue();
        user.setAddress(readAddress(reader));
        if (!reader.nextExpectedObjectField(USER_FIELDS, 21)) return readUserFallback(reader, user);
        reader.nextObjectValue();
        user.setTags(readStrings(reader));
        if (!reader.nextExpectedObjectField(USER_FIELDS, 22)) return readUserFallback(reader, user);
        reader.nextObjectValue();
        user.setFriends(readFriends(reader));

        if (reader.nextOrderedObjectField()) return readUserFallback(reader, user);
        reader.endObject();
        reader.endDocument();
        return user;
    }

    private static User readUserFallback(Jackson2ReaderV2 reader, User user) throws IOException {
        int field;
        while ((field = reader.matchCurrentObjectField(USER_FIELDS))
                != StreamingReaderV2.END_OF_OBJECT) {
            switch (field) {
                case 0: user.setId(reader.readLongValue()); break;
                case 1: user.setCreatedAt(reader.readLongValue()); break;
                case 2: user.setUpdatedAt(reader.readLongValue()); break;
                case 3: user.setReputation(reader.readLongValue()); break;
                case 4: user.setLoginCount(reader.readIntValue()); break;
                case 5: user.setAge(reader.readIntValue()); break;
                case 6: user.setActive(reader.readBooleanValue()); break;
                case 7: user.setVerified(reader.readBooleanValue()); break;
                case 8: user.setAdmin(reader.readBooleanValue()); break;
                case 9: user.setSuspended(reader.readBooleanValue()); break;
                case 10: user.setScore(reader.readDoubleValue()); break;
                case 11: user.setLatitude(reader.readDoubleValue()); break;
                case 12: user.setLongitude(reader.readDoubleValue()); break;
                case 13: user.setUsername(reader.readStringOrNull()); break;
                case 14: user.setEmail(reader.readStringOrNull()); break;
                case 15: user.setDisplayName(reader.readStringOrNull()); break;
                case 16: user.setPasswordHash(reader.readStringOrNull()); break;
                case 17: user.setBio(reader.readStringOrNull()); break;
                case 18: user.setWebsite(reader.readStringOrNull()); break;
                case 19: user.setDepartment(reader.readStringOrNull()); break;
                case 20: user.setAddress(readAddress(reader)); break;
                case 21: user.setTags(readStrings(reader)); break;
                case 22: user.setFriends(readFriends(reader)); break;
                default: reader.skipValue(); break;
            }
            reader.nextOrderedObjectField();
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
        if (!reader.nextExpectedObjectField(ADDRESS_FIELDS, 0)) return readAddressFallback(reader, address);
        address.setStreet(reader.nextStringOrNull());
        if (!reader.nextExpectedObjectField(ADDRESS_FIELDS, 1)) return readAddressFallback(reader, address);
        address.setCity(reader.nextStringOrNull());
        if (!reader.nextExpectedObjectField(ADDRESS_FIELDS, 2)) return readAddressFallback(reader, address);
        address.setState(reader.nextStringOrNull());
        if (!reader.nextExpectedObjectField(ADDRESS_FIELDS, 3)) return readAddressFallback(reader, address);
        address.setZip(reader.nextStringOrNull());
        if (!reader.nextExpectedObjectField(ADDRESS_FIELDS, 4)) return readAddressFallback(reader, address);
        address.setCountry(reader.nextStringOrNull());
        if (reader.nextOrderedObjectField()) return readAddressFallback(reader, address);
        reader.endObject();
        return address;
    }

    private static Address readAddressFallback(Jackson2ReaderV2 reader, Address address) throws IOException {
        int field;
        while ((field = reader.matchCurrentObjectField(ADDRESS_FIELDS))
                != StreamingReaderV2.END_OF_OBJECT) {
            switch (field) {
                case 0: address.setStreet(reader.readStringOrNull()); break;
                case 1: address.setCity(reader.readStringOrNull()); break;
                case 2: address.setState(reader.readStringOrNull()); break;
                case 3: address.setZip(reader.readStringOrNull()); break;
                case 4: address.setCountry(reader.readStringOrNull()); break;
                default: reader.skipValue(); break;
            }
            reader.nextOrderedObjectField();
        }
        reader.endObject();
        return address;
    }

    private static List<String> readStrings(Jackson2ReaderV2 reader) throws IOException {
        if (reader.isNull()) {
            reader.readNull();
            return null;
        }
        List<String> strings = new ArrayList<String>();
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
        List<Friend> friends = new ArrayList<Friend>();
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
        if (!reader.nextExpectedObjectField(FRIEND_FIELDS, 0)) return readFriendFallback(reader, friend);
        friend.setId(reader.nextLongValue());
        if (!reader.nextExpectedObjectField(FRIEND_FIELDS, 1)) return readFriendFallback(reader, friend);
        friend.setName(reader.nextStringOrNull());
        if (!reader.nextExpectedObjectField(FRIEND_FIELDS, 2)) return readFriendFallback(reader, friend);
        friend.setSince(reader.nextLongValue());
        if (!reader.nextExpectedObjectField(FRIEND_FIELDS, 3)) return readFriendFallback(reader, friend);
        friend.setClose(reader.nextBooleanValue());
        if (reader.nextOrderedObjectField()) return readFriendFallback(reader, friend);
        reader.endObject();
        return friend;
    }

    private static Friend readFriendFallback(Jackson2ReaderV2 reader, Friend friend) throws IOException {
        int field;
        while ((field = reader.matchCurrentObjectField(FRIEND_FIELDS))
                != StreamingReaderV2.END_OF_OBJECT) {
            switch (field) {
                case 0: friend.setId(reader.readLongValue()); break;
                case 1: friend.setName(reader.readStringOrNull()); break;
                case 2: friend.setSince(reader.readLongValue()); break;
                case 3: friend.setClose(reader.readBooleanValue()); break;
                default: reader.skipValue(); break;
            }
            reader.nextOrderedObjectField();
        }
        reader.endObject();
        return friend;
    }
}
