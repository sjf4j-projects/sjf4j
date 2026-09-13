package org.sjf4j.testbench.handwritten;

import com.google.gson.stream.JsonWriter;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;
import org.sjf4j.testbench.model.Users;

import java.io.IOException;
import java.util.List;

/** Direct Gson UTF-8 writer for {@link Users}. */
public final class GsonHandPojoWriter {
    private GsonHandPojoWriter() {}

    public static void writeUsers(JsonWriter w, Users v) throws IOException {
        if (v == null) {
            w.nullValue();
            return;
        }
        w.beginObject();
        w.name("users");
        users(w, v.getUsers());
        w.name("total").value(v.getTotal());
        w.name("page").value(v.getPage());
        w.name("generatedAt").value(v.getGeneratedAt());
        w.endObject();
    }

    private static void users(JsonWriter w, List<User> v) throws IOException {
        if (v == null) {
            w.nullValue();
            return;
        }
        w.beginArray();
        for (int i = 0, n = v.size(); i < n; i++) writeUser(w, v.get(i));
        w.endArray();
    }

    public static void writeUser(JsonWriter w, User v) throws IOException {
        if (v == null) {
            w.nullValue();
            return;
        }
        w.beginObject();
        w.name("id").value(v.getId());
        str(w, "username", v.getUsername());
        str(w, "email", v.getEmail());
        str(w, "displayName", v.getDisplayName());
        str(w, "passwordHash", v.getPasswordHash());
        str(w, "bio", v.getBio());
        str(w, "website", v.getWebsite());
        str(w, "department", v.getDepartment());
        w.name("createdAt").value(v.getCreatedAt());
        w.name("updatedAt").value(v.getUpdatedAt());
        w.name("loginCount").value(v.getLoginCount());
        w.name("reputation").value(v.getReputation());
        w.name("active").value(v.isActive());
        w.name("verified").value(v.isVerified());
        w.name("admin").value(v.isAdmin());
        w.name("suspended").value(v.isSuspended());
        w.name("score").value(v.getScore());
        w.name("latitude").value(v.getLatitude());
        w.name("longitude").value(v.getLongitude());
        w.name("age").value(v.getAge());
        w.name("address");
        address(w, v.getAddress());
        w.name("tags");
        tags(w, v.getTags());
        w.name("friends");
        friends(w, v.getFriends());
        w.endObject();
    }

    private static void address(JsonWriter w, Address v) throws IOException {
        if (v == null) {
            w.nullValue();
            return;
        }
        w.beginObject();
        str(w, "street", v.getStreet());
        str(w, "city", v.getCity());
        str(w, "state", v.getState());
        str(w, "zip", v.getZip());
        str(w, "country", v.getCountry());
        w.endObject();
    }

    private static void friends(JsonWriter w, List<Friend> v)
            throws IOException {
        if (v == null) {
            w.nullValue();
            return;
        }
        w.beginArray();
        for (int i = 0, n = v.size(); i < n; i++) {
            Friend f = v.get(i);
            if (f == null) {
                w.nullValue();
                continue;
            }
            w.beginObject();
            w.name("id").value(f.getId());
            str(w, "name", f.getName());
            w.name("since").value(f.getSince());
            w.name("close").value(f.isClose());
            w.endObject();
        }
        w.endArray();
    }

    private static void tags(JsonWriter w, List<String> v) throws IOException {
        if (v == null) {
            w.nullValue();
            return;
        }
        w.beginArray();
        for (int i = 0, n = v.size(); i < n; i++) {
            String s = v.get(i);
            if (s == null) w.nullValue();
            else w.value(s);
        }
        w.endArray();
    }

    private static void str(JsonWriter w, String n, String v) throws IOException {
        w.name(n);
        if (v == null) w.nullValue();
        else w.value(v);
    }
}