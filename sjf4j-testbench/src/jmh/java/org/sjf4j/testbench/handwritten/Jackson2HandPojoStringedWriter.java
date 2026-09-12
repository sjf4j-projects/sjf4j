package org.sjf4j.testbench.handwritten;

import com.fasterxml.jackson.core.JsonGenerator;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.io.IOException;

/** Direct Jackson2 streaming writer using String field names for comparison. */
public final class Jackson2HandPojoStringedWriter {

    private Jackson2HandPojoStringedWriter() {
    }

    public static void writeUser(JsonGenerator generator, User user) throws IOException {
        writeUserFields(generator, user);
    }

    private static void writeUserFields(JsonGenerator g, User v) throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        g.writeNumberField("id", v.getId());
        str(g, "username", v.getUsername());
        str(g, "email", v.getEmail());
        str(g, "displayName", v.getDisplayName());
        str(g, "passwordHash", v.getPasswordHash());
        str(g, "bio", v.getBio());
        str(g, "website", v.getWebsite());
        str(g, "department", v.getDepartment());
        g.writeNumberField("createdAt", v.getCreatedAt());
        g.writeNumberField("updatedAt", v.getUpdatedAt());
        g.writeNumberField("loginCount", v.getLoginCount());
        g.writeNumberField("reputation", v.getReputation());
        g.writeBooleanField("active", v.isActive());
        g.writeBooleanField("verified", v.isVerified());
        g.writeBooleanField("admin", v.isAdmin());
        g.writeBooleanField("suspended", v.isSuspended());
        g.writeNumberField("score", v.getScore());
        g.writeNumberField("latitude", v.getLatitude());
        g.writeNumberField("longitude", v.getLongitude());
        g.writeNumberField("age", v.getAge());
        g.writeFieldName("address");
        address(g, v.getAddress());
        g.writeFieldName("tags");
        tags(g, v);
        g.writeFieldName("friends");
        friends(g, v);
        g.writeEndObject();
    }

    private static void address(JsonGenerator g, Address v) throws IOException {
        if (v == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        str(g, "street", v.getStreet());
        str(g, "city", v.getCity());
        str(g, "state", v.getState());
        str(g, "zip", v.getZip());
        str(g, "country", v.getCountry());
        g.writeEndObject();
    }

    private static void tags(JsonGenerator g, User v) throws IOException {
        if (v.getTags() == null) {
            g.writeNull();
            return;
        }
        g.writeStartArray();
        for (String s : v.getTags()) {
            if (s == null) {
                g.writeNull();
            } else {
                g.writeString(s);
            }
        }
        g.writeEndArray();
    }

    private static void friends(JsonGenerator g, User v) throws IOException {
        if (v.getFriends() == null) {
            g.writeNull();
            return;
        }
        g.writeStartArray();
        for (Friend f : v.getFriends()) {
            if (f == null) {
                g.writeNull();
            } else {
                g.writeStartObject();
                g.writeNumberField("id", f.getId());
                str(g, "name", f.getName());
                g.writeNumberField("since", f.getSince());
                g.writeBooleanField("close", f.isClose());
                g.writeEndObject();
            }
        }
        g.writeEndArray();
    }

    private static void str(JsonGenerator g, String name, String value) throws IOException {
        g.writeFieldName(name);
        if (value == null) {
            g.writeNull();
        } else {
            g.writeString(value);
        }
    }
}
