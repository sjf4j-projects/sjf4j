package org.sjf4j.handwritten;

import com.alibaba.fastjson2.JSONReader;
import org.sjf4j.ReadBenchmark;

import java.util.ArrayList;
import java.util.List;

/** Direct Fastjson2 baseline for {@link ReadBenchmark.UserPojo}. */
public final class Fastjson2HandPojoReader {

    private Fastjson2HandPojoReader() {}

    public static ReadBenchmark.UserPojo readUser(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfObjectStart()) {
            throw new IllegalStateException("expected object, but was " + reader.current());
        }

        ReadBenchmark.UserPojo user = new ReadBenchmark.UserPojo();
        while (!reader.nextIfObjectEnd()) {
            String name = reader.readFieldName();
            if ("name".equals(name)) {
                user.setName(reader.nextIfNull() ? null : reader.readString());
            } else if ("friends".equals(name)) {
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

        List<ReadBenchmark.UserPojo> users = new ArrayList<ReadBenchmark.UserPojo>();
        while (!reader.nextIfArrayEnd()) {
            users.add(readUser(reader));
        }
        return users;
    }
}
