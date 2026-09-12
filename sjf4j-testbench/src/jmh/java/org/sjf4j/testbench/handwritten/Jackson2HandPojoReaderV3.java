package org.sjf4j.testbench.handwritten;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Direct Jackson2 parser baseline for {@link User}. */
public final class Jackson2HandPojoReaderV3 {

    private Jackson2HandPojoReaderV3() {}

    public static User readUser(JsonParser parser) throws IOException {
        if (startObject(parser) == JsonToken.VALUE_NULL) {
            parser.nextToken();
            return null;
        }

        User user = new User();

        String name;
        while ((name = parser.nextFieldName()) != null) {
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
                    JsonToken token = parser.nextToken();
                    user.setAddress(readAddress(parser, token));
                    break;
                }

                case "tags": {
                    JsonToken token = parser.nextToken();
                    user.setTags(readStrings(parser, token));
                    break;
                }

                case "friends": {
                    JsonToken token = parser.nextToken();
                    user.setFriends(readFriends(parser, token));
                    break;
                }

                default:
                    parser.nextToken();
                    parser.skipChildren();
                    break;
            }
        }

        parser.nextToken();
        return user;
    }

    private static Address readAddress(JsonParser parser, JsonToken token) throws IOException {
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        Address address = new Address();

        String name;
        while ((name = parser.nextFieldName()) != null) {
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
        }

        return address;
    }

    private static List<String> readStrings(JsonParser parser, JsonToken token)
            throws IOException {

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

    private static List<Friend> readFriends(JsonParser parser, JsonToken token)
            throws IOException {

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        List<Friend> friends = new ArrayList<>();

        while (parser.nextToken() != JsonToken.END_ARRAY) {
            friends.add(readFriend(parser));
        }

        return friends;
    }

    private static Friend readFriend(JsonParser parser) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }

        Friend friend = new Friend();

        String name;
        while ((name = parser.nextFieldName()) != null) {
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
        }

        return friend;
    }

    private static boolean nextBooleanValue(JsonParser parser) throws IOException {
        Boolean value = parser.nextBooleanValue();
        return Boolean.TRUE.equals(value);
    }

    private static JsonToken startObject(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();

        if (token == null) {
            token = parser.nextToken();
        }

        if (token == JsonToken.VALUE_NULL || token == JsonToken.START_OBJECT) {
            return token;
        }

        throw new IllegalStateException("expected object, but was " + token);
    }
}