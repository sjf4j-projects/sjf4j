package org.sjf4j.testbench.handwritten;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Direct Gson streaming baseline for {@link User}. */
public final class GsonHandPojoReaderV2 {

    private GsonHandPojoReaderV2() {}

    public static User readUser(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }

        User user = new User();
        reader.beginObject();

        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "id":
                    user.setId(reader.nextLong());
                    break;
                case "createdAt":
                    user.setCreatedAt(reader.nextLong());
                    break;
                case "updatedAt":
                    user.setUpdatedAt(reader.nextLong());
                    break;
                case "reputation":
                    user.setReputation(reader.nextLong());
                    break;
                case "loginCount":
                    user.setLoginCount(reader.nextInt());
                    break;
                case "age":
                    user.setAge(reader.nextInt());
                    break;
                case "active":
                    user.setActive(reader.nextBoolean());
                    break;
                case "verified":
                    user.setVerified(reader.nextBoolean());
                    break;
                case "admin":
                    user.setAdmin(reader.nextBoolean());
                    break;
                case "suspended":
                    user.setSuspended(reader.nextBoolean());
                    break;
                case "score":
                    user.setScore(reader.nextDouble());
                    break;
                case "latitude":
                    user.setLatitude(reader.nextDouble());
                    break;
                case "longitude":
                    user.setLongitude(reader.nextDouble());
                    break;
                case "username":
                    user.setUsername(readString(reader));
                    break;
                case "email":
                    user.setEmail(readString(reader));
                    break;
                case "displayName":
                    user.setDisplayName(readString(reader));
                    break;
                case "passwordHash":
                    user.setPasswordHash(readString(reader));
                    break;
                case "bio":
                    user.setBio(readString(reader));
                    break;
                case "website":
                    user.setWebsite(readString(reader));
                    break;
                case "department":
                    user.setDepartment(readString(reader));
                    break;
                case "address":
                    user.setAddress(readAddress(reader));
                    break;
                case "tags":
                    user.setTags(readStrings(reader));
                    break;
                case "friends":
                    user.setFriends(readFriends(reader));
                    break;
                default:
                    reader.skipValue();
            }
        }

        reader.endObject();
        return user;
    }

    private static Address readAddress(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }

        Address address = new Address();
        reader.beginObject();

        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "street":
                    address.setStreet(readString(reader));
                    break;
                case "city":
                    address.setCity(readString(reader));
                    break;
                case "state":
                    address.setState(readString(reader));
                    break;
                case "zip":
                    address.setZip(readString(reader));
                    break;
                case "country":
                    address.setCountry(readString(reader));
                    break;
                default:
                    reader.skipValue();
            }
        }

        reader.endObject();
        return address;
    }

    private static List<String> readStrings(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }

        List<String> strings = new ArrayList<>();
        reader.beginArray();

        while (reader.hasNext()) {
            strings.add(readString(reader));
        }

        reader.endArray();
        return strings;
    }

    private static List<Friend> readFriends(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }

        List<Friend> friends = new ArrayList<>();
        reader.beginArray();

        while (reader.hasNext()) {
            friends.add(readFriend(reader));
        }

        reader.endArray();
        return friends;
    }

    private static Friend readFriend(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }

        Friend friend = new Friend();
        reader.beginObject();

        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "id":
                    friend.setId(reader.nextLong());
                    break;
                case "name":
                    friend.setName(readString(reader));
                    break;
                case "since":
                    friend.setSince(reader.nextLong());
                    break;
                case "close":
                    friend.setClose(reader.nextBoolean());
                    break;
                default:
                    reader.skipValue();
            }
        }

        reader.endObject();
        return friend;
    }

    private static String readString(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        return reader.nextString();
    }
}
