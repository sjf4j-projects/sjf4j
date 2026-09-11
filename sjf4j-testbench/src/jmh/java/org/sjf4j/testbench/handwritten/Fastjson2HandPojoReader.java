package org.sjf4j.testbench.handwritten;

import com.alibaba.fastjson2.JSONReader;

import java.util.ArrayList;
import java.util.List;

/** Direct Fastjson2 baseline for {@link HandReadBenchmark.UserPojo}. */
public final class Fastjson2HandPojoReader {

    private Fastjson2HandPojoReader() {}

    public static HandReadBenchmark.UserPojo readUser(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfObjectStart()) {
            throw new IllegalStateException("expected object, but was " + reader.current());
        }

        HandReadBenchmark.UserPojo user = new HandReadBenchmark.UserPojo();
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

    private static List<HandReadBenchmark.UserPojo> readUsers(JSONReader reader) {
        if (reader.nextIfNull()) return null;
        if (!reader.nextIfArrayStart()) {
            throw new IllegalStateException("expected array, but was " + reader.current());
        }

        List<HandReadBenchmark.UserPojo> users = new ArrayList<>();
        while (!reader.nextIfArrayEnd()) {
            users.add(readUser(reader));
        }
        return users;
    }
}
