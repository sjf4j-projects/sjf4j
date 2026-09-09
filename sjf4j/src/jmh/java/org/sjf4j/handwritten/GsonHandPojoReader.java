package org.sjf4j.handwritten;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Direct Gson streaming baseline for {@link HandReadBenchmark.UserPojo}. */
public final class GsonHandPojoReader {

    private GsonHandPojoReader() {}

    public static HandReadBenchmark.UserPojo readUser(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }

        HandReadBenchmark.UserPojo user = new HandReadBenchmark.UserPojo();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("name".equals(name)) {
                user.setName(reader.peek() == JsonToken.NULL ? nextNull(reader) : reader.nextString());
            } else if ("friends".equals(name)) {
                user.setFriends(readUsers(reader));
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return user;
    }

    private static List<HandReadBenchmark.UserPojo> readUsers(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }

        List<HandReadBenchmark.UserPojo> users = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            users.add(readUser(reader));
        }
        reader.endArray();
        return users;
    }

    private static String nextNull(JsonReader reader) throws IOException {
        reader.nextNull();
        return null;
    }
}
