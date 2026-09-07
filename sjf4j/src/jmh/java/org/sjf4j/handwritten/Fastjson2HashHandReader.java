package org.sjf4j.handwritten;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.util.Fnv;
import org.sjf4j.ReadBenchmark;

import java.util.ArrayList;
import java.util.List;

/** Direct Fastjson2 baseline using its field-name hash reader API. */
public final class Fastjson2HashHandReader {

    private static final long NAME_HASH = Fnv.hashCode64("name");
    private static final long FRIENDS_HASH = Fnv.hashCode64("friends");

    private Fastjson2HashHandReader() {}

    public static ReadBenchmark.UserPojo readUser(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfObjectStart()) {
            throw new IllegalStateException("expected object, but was " + reader.current());
        }

        ReadBenchmark.UserPojo user = new ReadBenchmark.UserPojo();
        while (!reader.nextIfObjectEnd()) {
            long hash = reader.readFieldNameHashCode();
            if (hash == NAME_HASH) {
                user.setName(reader.nextIfNull() ? null : reader.readString());
            } else if (hash == FRIENDS_HASH) {
                user.setFriends(readUsers(reader));
            } else {
                reader.skipValue();
            }
        }
        return user;
    }

    private static List<ReadBenchmark.UserPojo> readUsers(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfArrayStart()) {
            throw new IllegalStateException("expected array, but was " + reader.current());
        }

        List<ReadBenchmark.UserPojo> users = new ArrayList<>();
        while (!reader.nextIfArrayEnd()) {
            users.add(readUser(reader));
        }
        return users;
    }
}
