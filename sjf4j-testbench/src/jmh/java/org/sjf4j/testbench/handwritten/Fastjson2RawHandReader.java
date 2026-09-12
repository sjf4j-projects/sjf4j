package org.sjf4j.testbench.handwritten;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.util.Fnv;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Fastjson2 handwritten reader using the same raw field-name fast path shape
 * as ObjectReaderCreatorASM:
 *
 * getRawInt() -> switch -> nextIfName4MatchN(...)
 *
 * Falls back to readFieldNameHashCode() + folded-int switch when the raw match
 * fails.
 *
 * Raw constants below are for little-endian platforms (x86_64 / AArch64),
 * which covers normal Intel/AMD and Apple Silicon environments.
 */
public final class Fastjson2RawHandReader {

    // ========================================================================
    // User
    // ========================================================================

    private static final long HASH_ID = Fnv.hashCode64("id");
    private static final long HASH_CREATED_AT = Fnv.hashCode64("createdAt");
    private static final long HASH_UPDATED_AT = Fnv.hashCode64("updatedAt");
    private static final long HASH_REPUTATION = Fnv.hashCode64("reputation");
    private static final long HASH_LOGIN_COUNT = Fnv.hashCode64("loginCount");
    private static final long HASH_AGE = Fnv.hashCode64("age");
    private static final long HASH_ACTIVE = Fnv.hashCode64("active");
    private static final long HASH_VERIFIED = Fnv.hashCode64("verified");
    private static final long HASH_ADMIN = Fnv.hashCode64("admin");
    private static final long HASH_SUSPENDED = Fnv.hashCode64("suspended");
    private static final long HASH_SCORE = Fnv.hashCode64("score");
    private static final long HASH_LATITUDE = Fnv.hashCode64("latitude");
    private static final long HASH_LONGITUDE = Fnv.hashCode64("longitude");
    private static final long HASH_USERNAME = Fnv.hashCode64("username");
    private static final long HASH_EMAIL = Fnv.hashCode64("email");
    private static final long HASH_DISPLAY_NAME = Fnv.hashCode64("displayName");
    private static final long HASH_PASSWORD_HASH = Fnv.hashCode64("passwordHash");
    private static final long HASH_BIO = Fnv.hashCode64("bio");
    private static final long HASH_WEBSITE = Fnv.hashCode64("website");
    private static final long HASH_DEPARTMENT = Fnv.hashCode64("department");
    private static final long HASH_ADDRESS = Fnv.hashCode64("address");
    private static final long HASH_TAGS = Fnv.hashCode64("tags");
    private static final long HASH_FRIENDS = Fnv.hashCode64("friends");

    private static final int KEY_ID = 0x00006469;
    private static final int KEY_CREATED_AT = 0x287DA5FA;
    private static final int KEY_UPDATED_AT = 0xF73EEA0A;
    private static final int KEY_REPUTATION = 0x198DE2AE;
    private static final int KEY_LOGIN_COUNT = 0x31C72932;
    private static final int KEY_AGE = 0x00656761;
    private static final int KEY_ACTIVE = 0x69740617;
    private static final int KEY_VERIFIED = 0x0D170C10;
    private static final int KEY_ADMIN = 0x696D640F;
    private static final int KEY_SUSPENDED = 0x40755DF3;
    private static final int KEY_SCORE = 0x726F6316;
    private static final int KEY_LATITUDE = 0x0C101418;
    private static final int KEY_LONGITUDE = 0x614E7F09;
    private static final int KEY_USERNAME = 0x1708121B;
    private static final int KEY_EMAIL = 0x69616D09;
    private static final int KEY_DISPLAY_NAME = 0xEFF44549;
    private static final int KEY_PASSWORD_HASH = 0x9698F9B5;
    private static final int KEY_BIO = 0x006F6962;
    private static final int KEY_WEBSITE = 0x7307111E;
    private static final int KEY_DEPARTMENT = 0x57F58BC8;
    private static final int KEY_ADDRESS = 0x72171704;
    private static final int KEY_TAGS = 0x73676174;
    private static final int KEY_FRIENDS = 0x651A1608;

    // raw first 4 bytes: '"' + first characters of field name
    private static final int RAW_ID = 0x22646922;
    private static final int RAW_CREATED_AT = 0x65726322;
    private static final int RAW_UPDATED_AT = 0x64707522;
    private static final int RAW_REPUTATION = 0x70657222;
    private static final int RAW_LOGIN_COUNT = 0x676F6C22;
    private static final int RAW_AGE = 0x65676122;
    private static final int RAW_ACTIVE = 0x74636122;
    private static final int RAW_VERIFIED = 0x72657622;
    private static final int RAW_ADMIN = 0x6D646122;
    private static final int RAW_SUSPENDED = 0x73757322;
    private static final int RAW_SCORE = 0x6F637322;
    private static final int RAW_LATITUDE = 0x74616C22;
    private static final int RAW_LONGITUDE = 0x6E6F6C22;
    private static final int RAW_USERNAME = 0x65737522;
    private static final int RAW_EMAIL = 0x616D6522;
    private static final int RAW_DISPLAY_NAME = 0x73696422;
    private static final int RAW_PASSWORD_HASH = 0x73617022;
    private static final int RAW_BIO = 0x6F696222;
    private static final int RAW_WEBSITE = 0x62657722;
    private static final int RAW_DEPARTMENT = 0x70656422;
    private static final int RAW_ADDRESS = 0x64646122;
    private static final int RAW_TAGS = 0x67617422;
    private static final int RAW_FRIENDS = 0x69726622;

    // ========================================================================
    // Address
    // ========================================================================

    private static final long HASH_STREET = Fnv.hashCode64("street");
    private static final long HASH_CITY = Fnv.hashCode64("city");
    private static final long HASH_STATE = Fnv.hashCode64("state");
    private static final long HASH_ZIP = Fnv.hashCode64("zip");
    private static final long HASH_COUNTRY = Fnv.hashCode64("country");

    private static final int KEY_STREET = 0x65720016;
    private static final int KEY_CITY = 0x79746963;
    private static final int KEY_STATE = 0x74617416;
    private static final int KEY_ZIP = 0x0070697A;
    private static final int KEY_COUNTRY = 0x6E0C1D17;

    private static final int RAW_STREET = 0x72747322;
    private static final int RAW_CITY = 0x74696322;
    private static final int RAW_STATE = 0x61747322;
    private static final int RAW_ZIP = 0x70697A22;
    private static final int RAW_COUNTRY = 0x756F6322;

    // ========================================================================
    // Friend
    // ========================================================================

    private static final long HASH_NAME = Fnv.hashCode64("name");
    private static final long HASH_SINCE = Fnv.hashCode64("since");
    private static final long HASH_CLOSE = Fnv.hashCode64("close");

    private static final int KEY_NAME = 0x656D616E;
    private static final int KEY_SINCE = 0x636E6916;
    private static final int KEY_CLOSE = 0x736F6C06;

    private static final int RAW_NAME = 0x6D616E22;
    private static final int RAW_SINCE = 0x6E697322;
    private static final int RAW_CLOSE = 0x6F6C6322;

    private Fastjson2RawHandReader() {
    }

    // ========================================================================
    // User
    // ========================================================================

    public static User readUser(JSONReader reader) {
        if (reader.nextIfNull()) {
            return null;
        }

        if (!reader.nextIfObjectStart()) {
            throw new IllegalStateException(
                    "expected object, but was " + reader.current());
        }

        User user = new User();

        while (!reader.nextIfObjectEnd()) {
            switch (reader.getRawInt()) {
                case RAW_ID:
                    if (reader.nextIfName4Match2()) {
                        user.setId(reader.readInt64Value());
                        continue;
                    }
                    break;

                case RAW_CREATED_AT:
                    if (reader.nextIfName4Match9(
                            0x3A22744164657461L)) {
                        user.setCreatedAt(reader.readInt64Value());
                        continue;
                    }
                    break;

                case RAW_UPDATED_AT:
                    if (reader.nextIfName4Match9(
                            0x3A22744164657461L)) {
                        user.setUpdatedAt(reader.readInt64Value());
                        continue;
                    }
                    break;

                case RAW_REPUTATION:
                    if (reader.nextIfName4Match10(
                            0x226E6F6974617475L)) {
                        user.setReputation(reader.readInt64Value());
                        continue;
                    }
                    break;

                case RAW_LOGIN_COUNT:
                    if (reader.nextIfName4Match10(
                            0x22746E756F436E69L)) {
                        user.setLoginCount(reader.readInt32Value());
                        continue;
                    }
                    break;

                case RAW_AGE:
                    if (reader.nextIfName4Match3()) {
                        user.setAge(reader.readInt32Value());
                        continue;
                    }
                    break;

                case RAW_ACTIVE:
                    if (reader.nextIfName4Match6(
                            0x22657669)) {
                        user.setActive(reader.readBoolValue());
                        continue;
                    }
                    break;

                case RAW_VERIFIED:
                    if (reader.nextIfName4Match8(
                            0x65696669,
                            (byte) 0x64)) {
                        user.setVerified(reader.readBoolValue());
                        continue;
                    }
                    break;

                case RAW_ADMIN:
                    if (reader.nextIfName4Match5(
                            0x3A226E69)) {
                        user.setAdmin(reader.readBoolValue());
                        continue;
                    }
                    break;

                case RAW_SUSPENDED:
                    if (reader.nextIfName4Match9(
                            0x3A226465646E6570L)) {
                        user.setSuspended(reader.readBoolValue());
                        continue;
                    }
                    break;

                case RAW_SCORE:
                    if (reader.nextIfName4Match5(
                            0x3A226572)) {
                        user.setScore(reader.readDoubleValue());
                        continue;
                    }
                    break;

                case RAW_LATITUDE:
                    if (reader.nextIfName4Match8(
                            0x64757469,
                            (byte) 0x65)) {
                        user.setLatitude(reader.readDoubleValue());
                        continue;
                    }
                    break;

                case RAW_LONGITUDE:
                    if (reader.nextIfName4Match9(
                            0x3A22656475746967L)) {
                        user.setLongitude(reader.readDoubleValue());
                        continue;
                    }
                    break;

                case RAW_USERNAME:
                    if (reader.nextIfName4Match8(
                            0x6D616E72,
                            (byte) 0x65)) {
                        user.setUsername(reader.readString());
                        continue;
                    }
                    break;

                case RAW_EMAIL:
                    if (reader.nextIfName4Match5(
                            0x3A226C69)) {
                        user.setEmail(reader.readString());
                        continue;
                    }
                    break;

                case RAW_DISPLAY_NAME:
                    if (reader.nextIfName4Match11(
                            0x656D614E79616C70L)) {
                        user.setDisplayName(reader.readString());
                        continue;
                    }
                    break;

                case RAW_PASSWORD_HASH:
                    if (reader.nextIfName4Match12(
                            0x73614864726F7773L,
                            (byte) 0x68)) {
                        user.setPasswordHash(reader.readString());
                        continue;
                    }
                    break;

                case RAW_BIO:
                    if (reader.nextIfName4Match3()) {
                        user.setBio(reader.readString());
                        continue;
                    }
                    break;

                case RAW_WEBSITE:
                    if (reader.nextIfName4Match7(
                            0x65746973)) {
                        user.setWebsite(reader.readString());
                        continue;
                    }
                    break;

                case RAW_DEPARTMENT:
                    if (reader.nextIfName4Match10(
                            0x22746E656D747261L)) {
                        user.setDepartment(reader.readString());
                        continue;
                    }
                    break;

                case RAW_ADDRESS:
                    if (reader.nextIfName4Match7(
                            0x73736572)) {
                        user.setAddress(readAddress(reader));
                        continue;
                    }
                    break;

                case RAW_TAGS:
                    if (reader.nextIfName4Match4(
                            (byte) 0x73)) {
                        user.setTags(readStrings(reader));
                        continue;
                    }
                    break;

                case RAW_FRIENDS:
                    if (reader.nextIfName4Match7(
                            0x73646E65)) {
                        user.setFriends(readFriends(reader));
                        continue;
                    }
                    break;

                default:
                    break;
            }

            readUserFieldHash(reader, user);
        }

        return user;
    }

    private static void readUserFieldHash(
            JSONReader reader,
            User user) {

        long hash = reader.readFieldNameHashCode();

        switch (Long.hashCode(hash)) {
            case KEY_ID:
                if (hash == HASH_ID) {
                    user.setId(reader.readInt64Value());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_CREATED_AT:
                if (hash == HASH_CREATED_AT) {
                    user.setCreatedAt(reader.readInt64Value());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_UPDATED_AT:
                if (hash == HASH_UPDATED_AT) {
                    user.setUpdatedAt(reader.readInt64Value());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_REPUTATION:
                if (hash == HASH_REPUTATION) {
                    user.setReputation(reader.readInt64Value());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_LOGIN_COUNT:
                if (hash == HASH_LOGIN_COUNT) {
                    user.setLoginCount(reader.readInt32Value());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_AGE:
                if (hash == HASH_AGE) {
                    user.setAge(reader.readInt32Value());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_ACTIVE:
                if (hash == HASH_ACTIVE) {
                    user.setActive(reader.readBoolValue());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_VERIFIED:
                if (hash == HASH_VERIFIED) {
                    user.setVerified(reader.readBoolValue());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_ADMIN:
                if (hash == HASH_ADMIN) {
                    user.setAdmin(reader.readBoolValue());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_SUSPENDED:
                if (hash == HASH_SUSPENDED) {
                    user.setSuspended(reader.readBoolValue());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_SCORE:
                if (hash == HASH_SCORE) {
                    user.setScore(reader.readDoubleValue());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_LATITUDE:
                if (hash == HASH_LATITUDE) {
                    user.setLatitude(reader.readDoubleValue());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_LONGITUDE:
                if (hash == HASH_LONGITUDE) {
                    user.setLongitude(reader.readDoubleValue());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_USERNAME:
                if (hash == HASH_USERNAME) {
                    user.setUsername(reader.readString());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_EMAIL:
                if (hash == HASH_EMAIL) {
                    user.setEmail(reader.readString());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_DISPLAY_NAME:
                if (hash == HASH_DISPLAY_NAME) {
                    user.setDisplayName(reader.readString());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_PASSWORD_HASH:
                if (hash == HASH_PASSWORD_HASH) {
                    user.setPasswordHash(reader.readString());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_BIO:
                if (hash == HASH_BIO) {
                    user.setBio(reader.readString());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_WEBSITE:
                if (hash == HASH_WEBSITE) {
                    user.setWebsite(reader.readString());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_DEPARTMENT:
                if (hash == HASH_DEPARTMENT) {
                    user.setDepartment(reader.readString());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_ADDRESS:
                if (hash == HASH_ADDRESS) {
                    user.setAddress(readAddress(reader));
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_TAGS:
                if (hash == HASH_TAGS) {
                    user.setTags(readStrings(reader));
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_FRIENDS:
                if (hash == HASH_FRIENDS) {
                    user.setFriends(readFriends(reader));
                } else {
                    reader.skipValue();
                }
                break;

            default:
                reader.skipValue();
        }
    }

    // ========================================================================
    // Address
    // ========================================================================

    private static Address readAddress(JSONReader reader) {
        if (reader.nextIfNull()) {
            return null;
        }

        if (!reader.nextIfObjectStart()) {
            throw new IllegalStateException(
                    "expected object, but was " + reader.current());
        }

        Address address = new Address();

        while (!reader.nextIfObjectEnd()) {
            switch (reader.getRawInt()) {
                case RAW_STREET:
                    if (reader.nextIfName4Match6(
                            0x22746565)) {
                        address.setStreet(reader.readString());
                        continue;
                    }
                    break;

                case RAW_CITY:
                    if (reader.nextIfName4Match4(
                            (byte) 0x79)) {
                        address.setCity(reader.readString());
                        continue;
                    }
                    break;

                case RAW_STATE:
                    if (reader.nextIfName4Match5(
                            0x3A226574)) {
                        address.setState(reader.readString());
                        continue;
                    }
                    break;

                case RAW_ZIP:
                    if (reader.nextIfName4Match3()) {
                        address.setZip(reader.readString());
                        continue;
                    }
                    break;

                case RAW_COUNTRY:
                    if (reader.nextIfName4Match7(
                            0x7972746E)) {
                        address.setCountry(reader.readString());
                        continue;
                    }
                    break;

                default:
                    break;
            }

            readAddressFieldHash(reader, address);
        }

        return address;
    }

    private static void readAddressFieldHash(
            JSONReader reader,
            Address address) {

        long hash = reader.readFieldNameHashCode();

        switch (Long.hashCode(hash)) {
            case KEY_STREET:
                if (hash == HASH_STREET) {
                    address.setStreet(reader.readString());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_CITY:
                if (hash == HASH_CITY) {
                    address.setCity(reader.readString());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_STATE:
                if (hash == HASH_STATE) {
                    address.setState(reader.readString());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_ZIP:
                if (hash == HASH_ZIP) {
                    address.setZip(reader.readString());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_COUNTRY:
                if (hash == HASH_COUNTRY) {
                    address.setCountry(reader.readString());
                } else {
                    reader.skipValue();
                }
                break;

            default:
                reader.skipValue();
        }
    }

    // ========================================================================
    // List<String>
    // ========================================================================

    private static List<String> readStrings(JSONReader reader) {
        if (reader.nextIfNull()) {
            return null;
        }

        if (!reader.nextIfArrayStart()) {
            throw new IllegalStateException(
                    "expected array, but was " + reader.current());
        }

        List<String> strings = new ArrayList<>();

        while (!reader.nextIfArrayEnd()) {
            strings.add(reader.readString());
        }

        return strings;
    }

    // ========================================================================
    // Friend
    // ========================================================================

    private static List<Friend> readFriends(JSONReader reader) {
        if (reader.nextIfNull()) {
            return null;
        }

        if (!reader.nextIfArrayStart()) {
            throw new IllegalStateException(
                    "expected array, but was " + reader.current());
        }

        List<Friend> friends = new ArrayList<>();

        while (!reader.nextIfArrayEnd()) {
            friends.add(readFriend(reader));
        }

        return friends;
    }

    private static Friend readFriend(JSONReader reader) {
        if (reader.nextIfNull()) {
            return null;
        }

        if (!reader.nextIfObjectStart()) {
            throw new IllegalStateException(
                    "expected object, but was " + reader.current());
        }

        Friend friend = new Friend();

        while (!reader.nextIfObjectEnd()) {
            switch (reader.getRawInt()) {
                case RAW_ID:
                    if (reader.nextIfName4Match2()) {
                        friend.setId(reader.readInt64Value());
                        continue;
                    }
                    break;

                case RAW_NAME:
                    if (reader.nextIfName4Match4(
                            (byte) 0x65)) {
                        friend.setName(reader.readString());
                        continue;
                    }
                    break;

                case RAW_SINCE:
                    if (reader.nextIfName4Match5(
                            0x3A226563)) {
                        friend.setSince(reader.readInt64Value());
                        continue;
                    }
                    break;

                case RAW_CLOSE:
                    if (reader.nextIfName4Match5(
                            0x3A226573)) {
                        friend.setClose(reader.readBoolValue());
                        continue;
                    }
                    break;

                default:
                    break;
            }

            readFriendFieldHash(reader, friend);
        }

        return friend;
    }

    private static void readFriendFieldHash(
            JSONReader reader,
            Friend friend) {

        long hash = reader.readFieldNameHashCode();

        switch (Long.hashCode(hash)) {
            case KEY_ID:
                if (hash == HASH_ID) {
                    friend.setId(reader.readInt64Value());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_NAME:
                if (hash == HASH_NAME) {
                    friend.setName(reader.readString());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_SINCE:
                if (hash == HASH_SINCE) {
                    friend.setSince(reader.readInt64Value());
                } else {
                    reader.skipValue();
                }
                break;

            case KEY_CLOSE:
                if (hash == HASH_CLOSE) {
                    friend.setClose(reader.readBoolValue());
                } else {
                    reader.skipValue();
                }
                break;

            default:
                reader.skipValue();
        }
    }
}