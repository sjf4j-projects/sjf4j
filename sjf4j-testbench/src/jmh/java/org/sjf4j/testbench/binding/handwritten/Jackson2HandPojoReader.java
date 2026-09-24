package org.sjf4j.testbench.binding.handwritten;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/** Direct Jackson2 parser baseline for {@link User}. */
public final class Jackson2HandPojoReader {

    private Jackson2HandPojoReader() {}

    public static User readUser(JsonParser parser) throws IOException {
        JsonToken token = startObject(parser);
        if (token == JsonToken.VALUE_NULL) {
            parser.nextToken();
            return null;
        }

        User user = new User();

        String name;
        while ((name = parser.nextFieldName()) != null) {
            token = parser.nextToken();

            switch (name) {
                case "id":
                    user.setId(parser.getLongValue());
                    break;
                case "createdAt":
                    user.setCreatedAt(parser.getLongValue());
                    break;
                case "updatedAt":
                    user.setUpdatedAt(parser.getLongValue());
                    break;
                case "reputation":
                    user.setReputation(parser.getLongValue());
                    break;

                case "loginCount":
                    user.setLoginCount(parser.getIntValue());
                    break;
                case "age":
                    user.setAge(parser.getIntValue());
                    break;

                case "active":
                    user.setActive(parser.getBooleanValue());
                    break;
                case "verified":
                    user.setVerified(parser.getBooleanValue());
                    break;
                case "admin":
                    user.setAdmin(parser.getBooleanValue());
                    break;
                case "suspended":
                    user.setSuspended(parser.getBooleanValue());
                    break;

                case "score":
                    user.setScore(parser.getDoubleValue());
                    break;
                case "latitude":
                    user.setLatitude(parser.getDoubleValue());
                    break;
                case "longitude":
                    user.setLongitude(parser.getDoubleValue());
                    break;

                case "username":
                    user.setUsername(string(parser, token));
                    break;
                case "email":
                    user.setEmail(string(parser, token));
                    break;
                case "displayName":
                    user.setDisplayName(string(parser, token));
                    break;
                case "passwordHash":
                    user.setPasswordHash(string(parser, token));
                    break;
                case "bio":
                    user.setBio(string(parser, token));
                    break;
                case "website":
                    user.setWebsite(string(parser, token));
                    break;
                case "department":
                    user.setDepartment(string(parser, token));
                    break;

                case "address":
                    user.setAddress(readAddress(parser, token));
                    break;
                case "tags":
                    user.setTags(readStrings(parser, token));
                    break;
                case "friends":
                    user.setFriends(readFriends(parser, token));
                    break;

                default:
                    parser.skipChildren();
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
            token = parser.nextToken();

            switch (name) {
                case "street":
                    address.setStreet(string(parser, token));
                    break;
                case "city":
                    address.setCity(string(parser, token));
                    break;
                case "state":
                    address.setState(string(parser, token));
                    break;
                case "zip":
                    address.setZip(string(parser, token));
                    break;
                case "country":
                    address.setCountry(string(parser, token));
                    break;
                default:
                    parser.skipChildren();
            }
        }

        return address;
    }

    private static List<String> readStrings(JsonParser parser, JsonToken token) throws IOException {
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        List<String> strings = new ArrayList<>();

        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            strings.add(string(parser, token));
        }

        return strings;
    }

    private static List<Friend> readFriends(JsonParser parser, JsonToken token) throws IOException {
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
            JsonToken token = parser.nextToken();

            switch (name) {
                case "id":
                    friend.setId(parser.getLongValue());
                    break;
                case "name":
                    friend.setName(string(parser, token));
                    break;
                case "since":
                    friend.setSince(parser.getLongValue());
                    break;
                case "close":
                    friend.setClose(parser.getBooleanValue());
                    break;
                default:
                    parser.skipChildren();
            }
        }

        return friend;
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

    private static String string(JsonParser parser, JsonToken token) throws IOException {
        return token == JsonToken.VALUE_NULL ? null : parser.getText();
    }
}