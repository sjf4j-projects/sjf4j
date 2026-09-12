package org.sjf4j.testbench.handwritten;

import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.util.List;

/** Direct Fastjson2 streaming writer using String field names for {@link User}. */
public final class Fastjson2HandPojoStringedWriter {
    private Fastjson2HandPojoStringedWriter() {
    }

    public static void writeUser(JSONWriter writer, User user) {
        user(writer, user);
    }

    private static void user(JSONWriter writer, User value) {
        if (value == null) {
            writer.writeNull();
            return;
        }
        writer.startObject();
        name(writer, "id");
        writer.writeInt64(value.getId());
        string(writer, "username", value.getUsername());
        string(writer, "email", value.getEmail());
        string(writer, "displayName", value.getDisplayName());
        string(writer, "passwordHash", value.getPasswordHash());
        string(writer, "bio", value.getBio());
        string(writer, "website", value.getWebsite());
        string(writer, "department", value.getDepartment());
        name(writer, "createdAt");
        writer.writeInt64(value.getCreatedAt());
        name(writer, "updatedAt");
        writer.writeInt64(value.getUpdatedAt());
        name(writer, "loginCount");
        writer.writeInt32(value.getLoginCount());
        name(writer, "reputation");
        writer.writeInt64(value.getReputation());
        name(writer, "active");
        writer.writeBool(value.isActive());
        name(writer, "verified");
        writer.writeBool(value.isVerified());
        name(writer, "admin");
        writer.writeBool(value.isAdmin());
        name(writer, "suspended");
        writer.writeBool(value.isSuspended());
        name(writer, "score");
        writer.writeDouble(value.getScore());
        name(writer, "latitude");
        writer.writeDouble(value.getLatitude());
        name(writer, "longitude");
        writer.writeDouble(value.getLongitude());
        name(writer, "age");
        writer.writeInt32(value.getAge());
        name(writer, "address");
        address(writer, value.getAddress());
        name(writer, "tags");
        strings(writer, value.getTags());
        name(writer, "friends");
        friends(writer, value.getFriends());
        writer.endObject();
    }

    private static void address(JSONWriter writer, Address value) {
        if (value == null) {
            writer.writeNull();
            return;
        }
        writer.startObject();
        string(writer, "street", value.getStreet());
        string(writer, "city", value.getCity());
        string(writer, "state", value.getState());
        string(writer, "zip", value.getZip());
        string(writer, "country", value.getCountry());
        writer.endObject();
    }

    private static void strings(JSONWriter writer, List<String> values) {
        if (values == null) {
            writer.writeNull();
            return;
        }
        writer.startArray();
        int i = 0;
        int size = values.size();
        for (; i < size; i++) {
            if (i != 0) {
                writer.writeComma();
            }
            String value = values.get(i);
            if (value == null) {
                writer.writeNull();
            } else {
                writer.writeString(value);
            }
        }
        writer.endArray();
    }

    private static void friends(JSONWriter writer, List<Friend> values) {
        if (values == null) {
            writer.writeNull();
            return;
        }
        writer.startArray();
        int i = 0;
        int size = values.size();
        for (; i < size; i++) {
            if (i != 0) {
                writer.writeComma();
            }
            Friend value = values.get(i);
            if (value == null) {
                writer.writeNull();
                continue;
            }
            writer.startObject();
            name(writer, "id");
            writer.writeInt64(value.getId());
            string(writer, "name", value.getName());
            name(writer, "since");
            writer.writeInt64(value.getSince());
            name(writer, "close");
            writer.writeBool(value.isClose());
            writer.endObject();
        }
        writer.endArray();
    }

    private static void name(JSONWriter writer, String name) {
        writer.writeName(name);
        writer.writeColon();
    }

    private static void string(JSONWriter writer, String name, String value) {
        name(writer, name);
        if (value == null) {
            writer.writeNull();
        } else {
            writer.writeString(value);
        }
    }
}
