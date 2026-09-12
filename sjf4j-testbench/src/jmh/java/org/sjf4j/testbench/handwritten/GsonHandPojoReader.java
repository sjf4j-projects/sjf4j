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
public final class GsonHandPojoReader {

    private GsonHandPojoReader() {}

    public static User readUser(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        User user = new User();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("id".equals(name)) user.setId(reader.nextLong());
            else if ("createdAt".equals(name)) user.setCreatedAt(reader.nextLong());
            else if ("updatedAt".equals(name)) user.setUpdatedAt(reader.nextLong());
            else if ("reputation".equals(name)) user.setReputation(reader.nextLong());
            else if ("loginCount".equals(name)) user.setLoginCount(reader.nextInt());
            else if ("age".equals(name)) user.setAge(reader.nextInt());
            else if ("active".equals(name)) user.setActive(reader.nextBoolean());
            else if ("verified".equals(name)) user.setVerified(reader.nextBoolean());
            else if ("admin".equals(name)) user.setAdmin(reader.nextBoolean());
            else if ("suspended".equals(name)) user.setSuspended(reader.nextBoolean());
            else if ("score".equals(name)) user.setScore(reader.nextDouble());
            else if ("latitude".equals(name)) user.setLatitude(reader.nextDouble());
            else if ("longitude".equals(name)) user.setLongitude(reader.nextDouble());
            else if ("username".equals(name)) user.setUsername(readString(reader));
            else if ("email".equals(name)) user.setEmail(readString(reader));
            else if ("displayName".equals(name)) user.setDisplayName(readString(reader));
            else if ("passwordHash".equals(name)) user.setPasswordHash(readString(reader));
            else if ("bio".equals(name)) user.setBio(readString(reader));
            else if ("website".equals(name)) user.setWebsite(readString(reader));
            else if ("department".equals(name)) user.setDepartment(readString(reader));
            else if ("address".equals(name)) user.setAddress(readAddress(reader));
            else if ("tags".equals(name)) user.setTags(readStrings(reader));
            else if ("friends".equals(name)) user.setFriends(readFriends(reader));
            else reader.skipValue();
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
            String name = reader.nextName();
            if ("street".equals(name)) address.setStreet(readString(reader));
            else if ("city".equals(name)) address.setCity(readString(reader));
            else if ("state".equals(name)) address.setState(readString(reader));
            else if ("zip".equals(name)) address.setZip(readString(reader));
            else if ("country".equals(name)) address.setCountry(readString(reader));
            else reader.skipValue();
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
        while (reader.hasNext()) strings.add(readString(reader));
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
        while (reader.hasNext()) friends.add(readFriend(reader));
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
            String name = reader.nextName();
            if ("id".equals(name)) friend.setId(reader.nextLong());
            else if ("name".equals(name)) friend.setName(readString(reader));
            else if ("since".equals(name)) friend.setSince(reader.nextLong());
            else if ("close".equals(name)) friend.setClose(reader.nextBoolean());
            else reader.skipValue();
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
