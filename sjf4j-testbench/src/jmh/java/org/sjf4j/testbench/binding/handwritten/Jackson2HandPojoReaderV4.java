package org.sjf4j.testbench.binding.handwritten;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Direct Jackson2 parser with ordered-field fast path.
 */
public final class Jackson2HandPojoReaderV4 {

    private Jackson2HandPojoReaderV4() {}

    /*
     * Public Jackson API.
     *
     * Reusable SerializedString instances allow JsonParser implementations
     * such as the UTF-8 parser to compare the expected field name directly
     * against the input representation.
     */

    private static final SerializableString ID =
            new SerializedString("id");
    private static final SerializableString CREATED_AT =
            new SerializedString("createdAt");
    private static final SerializableString UPDATED_AT =
            new SerializedString("updatedAt");
    private static final SerializableString REPUTATION =
            new SerializedString("reputation");
    private static final SerializableString LOGIN_COUNT =
            new SerializedString("loginCount");
    private static final SerializableString AGE =
            new SerializedString("age");
    private static final SerializableString ACTIVE =
            new SerializedString("active");
    private static final SerializableString VERIFIED =
            new SerializedString("verified");
    private static final SerializableString ADMIN =
            new SerializedString("admin");
    private static final SerializableString SUSPENDED =
            new SerializedString("suspended");
    private static final SerializableString SCORE =
            new SerializedString("score");
    private static final SerializableString LATITUDE =
            new SerializedString("latitude");
    private static final SerializableString LONGITUDE =
            new SerializedString("longitude");
    private static final SerializableString USERNAME =
            new SerializedString("username");
    private static final SerializableString EMAIL =
            new SerializedString("email");
    private static final SerializableString DISPLAY_NAME =
            new SerializedString("displayName");
    private static final SerializableString PASSWORD_HASH =
            new SerializedString("passwordHash");
    private static final SerializableString BIO =
            new SerializedString("bio");
    private static final SerializableString WEBSITE =
            new SerializedString("website");
    private static final SerializableString DEPARTMENT =
            new SerializedString("department");
    private static final SerializableString ADDRESS =
            new SerializedString("address");
    private static final SerializableString TAGS =
            new SerializedString("tags");
    private static final SerializableString FRIENDS =
            new SerializedString("friends");

    // Address

    private static final SerializableString STREET =
            new SerializedString("street");
    private static final SerializableString CITY =
            new SerializedString("city");
    private static final SerializableString STATE =
            new SerializedString("state");
    private static final SerializableString ZIP =
            new SerializedString("zip");
    private static final SerializableString COUNTRY =
            new SerializedString("country");

    // Friend

    private static final SerializableString NAME =
            new SerializedString("name");
    private static final SerializableString SINCE =
            new SerializedString("since");
    private static final SerializableString CLOSE =
            new SerializedString("close");

    public static User readUser(JsonParser parser) throws IOException {
        JsonToken token = startObject(parser);

        if (token == JsonToken.VALUE_NULL) {
            parser.nextToken();
            return null;
        }

        User user = new User();

        /*
         * Ordered fast path.
         *
         * Any mismatch immediately falls back to the generic path.
         * On failure nextFieldName(...) leaves the parser positioned on
         * the actual FIELD_NAME or END_OBJECT, so fallback can continue
         * without reparsing anything.
         */

        if (!parser.nextFieldName(ID)) {
            return readUserFallback(parser, user);
        }
        user.setId(parser.nextLongValue(0L));

        if (!parser.nextFieldName(CREATED_AT)) {
            return readUserFallback(parser, user);
        }
        user.setCreatedAt(parser.nextLongValue(0L));

        if (!parser.nextFieldName(UPDATED_AT)) {
            return readUserFallback(parser, user);
        }
        user.setUpdatedAt(parser.nextLongValue(0L));

        if (!parser.nextFieldName(REPUTATION)) {
            return readUserFallback(parser, user);
        }
        user.setReputation(parser.nextLongValue(0L));

        if (!parser.nextFieldName(LOGIN_COUNT)) {
            return readUserFallback(parser, user);
        }
        user.setLoginCount(parser.nextIntValue(0));

        if (!parser.nextFieldName(AGE)) {
            return readUserFallback(parser, user);
        }
        user.setAge(parser.nextIntValue(0));

        if (!parser.nextFieldName(ACTIVE)) {
            return readUserFallback(parser, user);
        }
        user.setActive(nextBooleanValue(parser));

        if (!parser.nextFieldName(VERIFIED)) {
            return readUserFallback(parser, user);
        }
        user.setVerified(nextBooleanValue(parser));

        if (!parser.nextFieldName(ADMIN)) {
            return readUserFallback(parser, user);
        }
        user.setAdmin(nextBooleanValue(parser));

        if (!parser.nextFieldName(SUSPENDED)) {
            return readUserFallback(parser, user);
        }
        user.setSuspended(nextBooleanValue(parser));

        if (!parser.nextFieldName(SCORE)) {
            return readUserFallback(parser, user);
        }
        parser.nextToken();
        user.setScore(parser.getDoubleValue());

        if (!parser.nextFieldName(LATITUDE)) {
            return readUserFallback(parser, user);
        }
        parser.nextToken();
        user.setLatitude(parser.getDoubleValue());

        if (!parser.nextFieldName(LONGITUDE)) {
            return readUserFallback(parser, user);
        }
        parser.nextToken();
        user.setLongitude(parser.getDoubleValue());

        if (!parser.nextFieldName(USERNAME)) {
            return readUserFallback(parser, user);
        }
        user.setUsername(parser.nextTextValue());

        if (!parser.nextFieldName(EMAIL)) {
            return readUserFallback(parser, user);
        }
        user.setEmail(parser.nextTextValue());

        if (!parser.nextFieldName(DISPLAY_NAME)) {
            return readUserFallback(parser, user);
        }
        user.setDisplayName(parser.nextTextValue());

        if (!parser.nextFieldName(PASSWORD_HASH)) {
            return readUserFallback(parser, user);
        }
        user.setPasswordHash(parser.nextTextValue());

        if (!parser.nextFieldName(BIO)) {
            return readUserFallback(parser, user);
        }
        user.setBio(parser.nextTextValue());

        if (!parser.nextFieldName(WEBSITE)) {
            return readUserFallback(parser, user);
        }
        user.setWebsite(parser.nextTextValue());

        if (!parser.nextFieldName(DEPARTMENT)) {
            return readUserFallback(parser, user);
        }
        user.setDepartment(parser.nextTextValue());

        if (!parser.nextFieldName(ADDRESS)) {
            return readUserFallback(parser, user);
        }
        token = parser.nextToken();
        user.setAddress(readAddress(parser, token));

        if (!parser.nextFieldName(TAGS)) {
            return readUserFallback(parser, user);
        }
        token = parser.nextToken();
        user.setTags(readStrings(parser, token));

        if (!parser.nextFieldName(FRIENDS)) {
            return readUserFallback(parser, user);
        }
        token = parser.nextToken();
        user.setFriends(readFriends(parser, token));

        /*
         * Normally next token is END_OBJECT.
         *
         * If another field exists, fall back and process it normally.
         */
        token = parser.nextToken();

        if (token == JsonToken.END_OBJECT) {
            parser.nextToken();
            return user;
        }

        return readUserFallback(parser, user);
    }

    /*
     * Generic fallback.
     *
     * Entry state:
     *
     *   FIELD_NAME   -> ordered path encountered another/misordered field
     *   END_OBJECT   -> object ended before all expected fields
     *
     * Exit state matches readUser(): parser has advanced beyond END_OBJECT.
     */
    private static User readUserFallback(
            JsonParser parser,
            User user) throws IOException {

        JsonToken token = parser.currentToken();

        if (token == JsonToken.END_OBJECT) {
            parser.nextToken();
            return user;
        }

        while (token == JsonToken.FIELD_NAME) {
            String name = parser.currentName();

            switch (name) {
                case "id":
                    user.setId(parser.nextLongValue(0L));
                    break;

                case "createdAt":
                    user.setCreatedAt(parser.nextLongValue(0L));
                    break;

                case "updatedAt":
                    user.setUpdatedAt(parser.nextLongValue(0L));
                    break;

                case "reputation":
                    user.setReputation(parser.nextLongValue(0L));
                    break;

                case "loginCount":
                    user.setLoginCount(parser.nextIntValue(0));
                    break;

                case "age":
                    user.setAge(parser.nextIntValue(0));
                    break;

                case "active":
                    user.setActive(nextBooleanValue(parser));
                    break;

                case "verified":
                    user.setVerified(nextBooleanValue(parser));
                    break;

                case "admin":
                    user.setAdmin(nextBooleanValue(parser));
                    break;

                case "suspended":
                    user.setSuspended(nextBooleanValue(parser));
                    break;

                case "score":
                    parser.nextToken();
                    user.setScore(parser.getDoubleValue());
                    break;

                case "latitude":
                    parser.nextToken();
                    user.setLatitude(parser.getDoubleValue());
                    break;

                case "longitude":
                    parser.nextToken();
                    user.setLongitude(parser.getDoubleValue());
                    break;

                case "username":
                    user.setUsername(parser.nextTextValue());
                    break;

                case "email":
                    user.setEmail(parser.nextTextValue());
                    break;

                case "displayName":
                    user.setDisplayName(parser.nextTextValue());
                    break;

                case "passwordHash":
                    user.setPasswordHash(parser.nextTextValue());
                    break;

                case "bio":
                    user.setBio(parser.nextTextValue());
                    break;

                case "website":
                    user.setWebsite(parser.nextTextValue());
                    break;

                case "department":
                    user.setDepartment(parser.nextTextValue());
                    break;

                case "address": {
                    JsonToken valueToken = parser.nextToken();
                    user.setAddress(readAddress(parser, valueToken));
                    break;
                }

                case "tags": {
                    JsonToken valueToken = parser.nextToken();
                    user.setTags(readStrings(parser, valueToken));
                    break;
                }

                case "friends": {
                    JsonToken valueToken = parser.nextToken();
                    user.setFriends(readFriends(parser, valueToken));
                    break;
                }

                default:
                    parser.nextToken();
                    parser.skipChildren();
                    break;
            }

            token = parser.nextToken();
        }

        if (token != JsonToken.END_OBJECT) {
            throw new IllegalStateException(
                    "expected field or end object, but was " + token);
        }

        parser.nextToken();
        return user;
    }

    private static Address readAddress(
            JsonParser parser,
            JsonToken token) throws IOException {

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        Address address = new Address();

        /*
         * Address ordered fast path.
         */

        if (!parser.nextFieldName(STREET)) {
            return readAddressFallback(parser, address);
        }
        address.setStreet(parser.nextTextValue());

        if (!parser.nextFieldName(CITY)) {
            return readAddressFallback(parser, address);
        }
        address.setCity(parser.nextTextValue());

        if (!parser.nextFieldName(STATE)) {
            return readAddressFallback(parser, address);
        }
        address.setState(parser.nextTextValue());

        if (!parser.nextFieldName(ZIP)) {
            return readAddressFallback(parser, address);
        }
        address.setZip(parser.nextTextValue());

        if (!parser.nextFieldName(COUNTRY)) {
            return readAddressFallback(parser, address);
        }
        address.setCountry(parser.nextTextValue());

        token = parser.nextToken();

        if (token == JsonToken.END_OBJECT) {
            return address;
        }

        return readAddressFallback(parser, address);
    }

    /*
     * Leaves parser positioned on END_OBJECT.
     */
    private static Address readAddressFallback(
            JsonParser parser,
            Address address) throws IOException {

        JsonToken token = parser.currentToken();

        if (token == JsonToken.END_OBJECT) {
            return address;
        }

        while (token == JsonToken.FIELD_NAME) {
            String name = parser.currentName();

            switch (name) {
                case "street":
                    address.setStreet(parser.nextTextValue());
                    break;

                case "city":
                    address.setCity(parser.nextTextValue());
                    break;

                case "state":
                    address.setState(parser.nextTextValue());
                    break;

                case "zip":
                    address.setZip(parser.nextTextValue());
                    break;

                case "country":
                    address.setCountry(parser.nextTextValue());
                    break;

                default:
                    parser.nextToken();
                    parser.skipChildren();
                    break;
            }

            token = parser.nextToken();
        }

        if (token != JsonToken.END_OBJECT) {
            throw new IllegalStateException(
                    "expected field or end object, but was " + token);
        }

        return address;
    }

    private static List<String> readStrings(
            JsonParser parser,
            JsonToken token) throws IOException {

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        List<String> strings = new ArrayList<>();

        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == JsonToken.VALUE_NULL) {
                strings.add(null);
            } else {
                strings.add(parser.getText());
            }
        }

        return strings;
    }

    private static List<Friend> readFriends(
            JsonParser parser,
            JsonToken token) throws IOException {

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        List<Friend> friends = new ArrayList<>();

        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            friends.add(readFriend(parser, token));
        }

        return friends;
    }

    private static Friend readFriend(
            JsonParser parser,
            JsonToken token) throws IOException {

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        Friend friend = new Friend();

        /*
         * Friend ordered fast path.
         */

        if (!parser.nextFieldName(ID)) {
            return readFriendFallback(parser, friend);
        }
        friend.setId(parser.nextLongValue(0L));

        if (!parser.nextFieldName(NAME)) {
            return readFriendFallback(parser, friend);
        }
        friend.setName(parser.nextTextValue());

        if (!parser.nextFieldName(SINCE)) {
            return readFriendFallback(parser, friend);
        }
        friend.setSince(parser.nextLongValue(0L));

        if (!parser.nextFieldName(CLOSE)) {
            return readFriendFallback(parser, friend);
        }
        friend.setClose(nextBooleanValue(parser));

        token = parser.nextToken();

        if (token == JsonToken.END_OBJECT) {
            return friend;
        }

        return readFriendFallback(parser, friend);
    }

    /*
     * Leaves parser positioned on END_OBJECT.
     */
    private static Friend readFriendFallback(
            JsonParser parser,
            Friend friend) throws IOException {

        JsonToken token = parser.currentToken();

        if (token == JsonToken.END_OBJECT) {
            return friend;
        }

        while (token == JsonToken.FIELD_NAME) {
            String name = parser.currentName();

            switch (name) {
                case "id":
                    friend.setId(parser.nextLongValue(0L));
                    break;

                case "name":
                    friend.setName(parser.nextTextValue());
                    break;

                case "since":
                    friend.setSince(parser.nextLongValue(0L));
                    break;

                case "close":
                    friend.setClose(nextBooleanValue(parser));
                    break;

                default:
                    parser.nextToken();
                    parser.skipChildren();
                    break;
            }

            token = parser.nextToken();
        }

        if (token != JsonToken.END_OBJECT) {
            throw new IllegalStateException(
                    "expected field or end object, but was " + token);
        }

        return friend;
    }

    private static boolean nextBooleanValue(JsonParser parser)
            throws IOException {

        return Boolean.TRUE.equals(parser.nextBooleanValue());
    }

    private static JsonToken startObject(JsonParser parser)
            throws IOException {

        JsonToken token = parser.currentToken();

        if (token == null) {
            token = parser.nextToken();
        }

        if (token == JsonToken.VALUE_NULL
                || token == JsonToken.START_OBJECT) {
            return token;
        }

        throw new IllegalStateException(
                "expected object, but was " + token);
    }
}