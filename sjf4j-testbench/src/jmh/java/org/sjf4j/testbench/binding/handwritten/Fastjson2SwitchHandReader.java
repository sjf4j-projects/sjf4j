package org.sjf4j.testbench.binding.handwritten;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.util.Fnv;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Direct Fastjson2 parser baseline using field-name hash + switch dispatch.
 */
public final class Fastjson2SwitchHandReader {

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

    private static final int ID = 0x00006469;
    private static final int CREATED_AT = 0x287DA5FA;
    private static final int UPDATED_AT = 0xF73EEA0A;
    private static final int REPUTATION = 0x198DE2AE;
    private static final int LOGIN_COUNT = 0x31C72932;
    private static final int AGE = 0x00656761;
    private static final int ACTIVE = 0x69740617;
    private static final int VERIFIED = 0x0D170C10;
    private static final int ADMIN = 0x696D640F;
    private static final int SUSPENDED = 0x40755DF3;
    private static final int SCORE = 0x726F6316;
    private static final int LATITUDE = 0x0C101418;
    private static final int LONGITUDE = 0x614E7F09;
    private static final int USERNAME = 0x1708121B;
    private static final int EMAIL = 0x69616D09;
    private static final int DISPLAY_NAME = 0xEFF44549;
    private static final int PASSWORD_HASH = 0x9698F9B5;
    private static final int BIO = 0x006F6962;
    private static final int WEBSITE = 0x7307111E;
    private static final int DEPARTMENT = 0x57F58BC8;
    private static final int ADDRESS = 0x72171704;
    private static final int TAGS = 0x73676174;
    private static final int FRIENDS = 0x651A1608;

    // ========================================================================
    // Address
    // ========================================================================

    private static final long HASH_STREET = Fnv.hashCode64("street");
    private static final long HASH_CITY = Fnv.hashCode64("city");
    private static final long HASH_STATE = Fnv.hashCode64("state");
    private static final long HASH_ZIP = Fnv.hashCode64("zip");
    private static final long HASH_COUNTRY = Fnv.hashCode64("country");

    private static final int STREET = 0x65720016;
    private static final int CITY = 0x79746963;
    private static final int STATE = 0x74617416;
    private static final int ZIP = 0x0070697A;
    private static final int COUNTRY = 0x6E0C1D17;

    // ========================================================================
    // Friend
    // ========================================================================

    private static final long HASH_NAME = Fnv.hashCode64("name");
    private static final long HASH_SINCE = Fnv.hashCode64("since");
    private static final long HASH_CLOSE = Fnv.hashCode64("close");

    private static final int NAME = 0x656D616E;
    private static final int SINCE = 0x636E6916;
    private static final int CLOSE = 0x736F6C06;

    private Fastjson2SwitchHandReader() {
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
            long hash = reader.readFieldNameHashCode();

            switch (Long.hashCode(hash)) {
                case ID:
                    if (hash == HASH_ID) {
                        user.setId(reader.readInt64Value());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case CREATED_AT:
                    if (hash == HASH_CREATED_AT) {
                        user.setCreatedAt(reader.readInt64Value());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case UPDATED_AT:
                    if (hash == HASH_UPDATED_AT) {
                        user.setUpdatedAt(reader.readInt64Value());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case REPUTATION:
                    if (hash == HASH_REPUTATION) {
                        user.setReputation(reader.readInt64Value());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case LOGIN_COUNT:
                    if (hash == HASH_LOGIN_COUNT) {
                        user.setLoginCount(reader.readInt32Value());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case AGE:
                    if (hash == HASH_AGE) {
                        user.setAge(reader.readInt32Value());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case ACTIVE:
                    if (hash == HASH_ACTIVE) {
                        user.setActive(reader.readBoolValue());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case VERIFIED:
                    if (hash == HASH_VERIFIED) {
                        user.setVerified(reader.readBoolValue());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case ADMIN:
                    if (hash == HASH_ADMIN) {
                        user.setAdmin(reader.readBoolValue());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case SUSPENDED:
                    if (hash == HASH_SUSPENDED) {
                        user.setSuspended(reader.readBoolValue());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case SCORE:
                    if (hash == HASH_SCORE) {
                        user.setScore(reader.readDoubleValue());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case LATITUDE:
                    if (hash == HASH_LATITUDE) {
                        user.setLatitude(reader.readDoubleValue());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case LONGITUDE:
                    if (hash == HASH_LONGITUDE) {
                        user.setLongitude(reader.readDoubleValue());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case USERNAME:
                    if (hash == HASH_USERNAME) {
                        user.setUsername(reader.readString());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case EMAIL:
                    if (hash == HASH_EMAIL) {
                        user.setEmail(reader.readString());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case DISPLAY_NAME:
                    if (hash == HASH_DISPLAY_NAME) {
                        user.setDisplayName(reader.readString());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case PASSWORD_HASH:
                    if (hash == HASH_PASSWORD_HASH) {
                        user.setPasswordHash(reader.readString());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case BIO:
                    if (hash == HASH_BIO) {
                        user.setBio(reader.readString());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case WEBSITE:
                    if (hash == HASH_WEBSITE) {
                        user.setWebsite(reader.readString());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case DEPARTMENT:
                    if (hash == HASH_DEPARTMENT) {
                        user.setDepartment(reader.readString());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case ADDRESS:
                    if (hash == HASH_ADDRESS) {
                        user.setAddress(readAddress(reader));
                    } else {
                        reader.skipValue();
                    }
                    break;

                case TAGS:
                    if (hash == HASH_TAGS) {
                        user.setTags(readStrings(reader));
                    } else {
                        reader.skipValue();
                    }
                    break;

                case FRIENDS:
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

        return user;
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
            long hash = reader.readFieldNameHashCode();

            switch (Long.hashCode(hash)) {
                case STREET:
                    if (hash == HASH_STREET) {
                        address.setStreet(reader.readString());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case CITY:
                    if (hash == HASH_CITY) {
                        address.setCity(reader.readString());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case STATE:
                    if (hash == HASH_STATE) {
                        address.setState(reader.readString());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case ZIP:
                    if (hash == HASH_ZIP) {
                        address.setZip(reader.readString());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case COUNTRY:
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

        return address;
    }

    // ========================================================================
    // Strings
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
    // Friends
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
            long hash = reader.readFieldNameHashCode();

            switch (Long.hashCode(hash)) {
                case ID:
                    if (hash == HASH_ID) {
                        friend.setId(reader.readInt64Value());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case NAME:
                    if (hash == HASH_NAME) {
                        friend.setName(reader.readString());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case SINCE:
                    if (hash == HASH_SINCE) {
                        friend.setSince(reader.readInt64Value());
                    } else {
                        reader.skipValue();
                    }
                    break;

                case CLOSE:
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

        return friend;
    }

}