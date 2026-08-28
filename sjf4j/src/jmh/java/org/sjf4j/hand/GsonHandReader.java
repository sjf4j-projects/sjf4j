package org.sjf4j.hand;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.sjf4j.ReadBenchmark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Direct Gson streaming baseline for {@link ReadBenchmark.UserPojo}. */
public final class GsonHandReader {

    private GsonHandReader() {}

    public static ReadBenchmark.UserPojo readUser(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }

        ReadBenchmark.UserPojo user = new ReadBenchmark.UserPojo();
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

    private static List<ReadBenchmark.UserPojo> readUsers(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }

        List<ReadBenchmark.UserPojo> users = new ArrayList<ReadBenchmark.UserPojo>();
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
