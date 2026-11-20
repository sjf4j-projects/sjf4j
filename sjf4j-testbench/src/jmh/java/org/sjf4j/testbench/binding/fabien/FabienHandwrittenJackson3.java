package org.sjf4j.testbench.binding.fabien;

import org.sjf4j.testbench.model.fabien.Users;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.sym.BinaryNameMatcher;
import tools.jackson.core.sym.PropertyNameMatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Diagnostic-only, handwritten direct Jackson 3 POJO reader.
 *
 * <p>No SJF4J reader, cursor state, reflection or annotation processor on the
 * hot path. Uses the same default-capacity ArrayList policy as the control.
 * Never use this as a new library backend without separate semantic tests.
 */
final class FabienHandwrittenJackson3 {

    private static final JsonFactory FACTORY = new JsonFactory().rebuild()
            .disable(TokenStreamFactory.Feature.CHARSET_DETECTION)
            .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .build();
    private static final ObjectReadContext READ_CONTEXT = ObjectReadContext.empty();

    private static final BinaryNameMatcher ROOT = names("users");
    private static final BinaryNameMatcher USER = names(
            "id", "index", "guid", "isActive", "balance", "picture", "age", "eyeColor", "name", "gender", "company", "email", "phone", "address", "about", "registered", "latitude", "longitude", "tags", "friends", "greeting", "favoriteFruit");
    private static final BinaryNameMatcher FRIEND = names("id", "name");

    private FabienHandwrittenJackson3() {}

    private static BinaryNameMatcher names(String... values) {
        return BinaryNameMatcher.construct(Arrays.asList(values), false);
    }

    static Users readUsers(byte[] input) throws IOException {
        try (JsonParser p = FACTORY.createParser(READ_CONTEXT, input)) {
            require(p.nextToken(), JsonToken.START_OBJECT);
            Users result = new Users();
            int field;
            while ((field = p.nextNameMatchAndToken(ROOT))
                    != PropertyNameMatcher.MATCH_END_OBJECT) {
                switch (field) {
                    case 0: result.setUsers(readUsersArray(p)); break;
                    default: skipUnknown(p, field);
                }
            }
            if (p.nextToken() != null) {
                throw new IOException("Trailing JSON after Users object");
            }
            return result;
        }
    }

    private static List<Users.User> readUsersArray(JsonParser p) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NULL) return null;
        require(p.currentToken(), JsonToken.START_ARRAY);
        List<Users.User> list = new ArrayList<>();
        JsonToken token;
        while ((token = p.nextToken()) != JsonToken.END_ARRAY) {
            if (token == null) throw new IOException("Unexpected EOF in users array");
            list.add(token == JsonToken.VALUE_NULL ? null : readUser(p));
        }
        return list;
    }

    private static Users.User readUser(JsonParser p) throws IOException {
        require(p.currentToken(), JsonToken.START_OBJECT);
        Users.User value = new Users.User();
        int field;
        while ((field = p.nextNameMatchAndToken(USER))
                != PropertyNameMatcher.MATCH_END_OBJECT) {
            switch (field) {
                case 0: value.setId(text(p)); break;
                case 1: value.setIndex(p.getIntValue()); break;
                case 2: value.setGuid(text(p)); break;
                case 3: value.setIsActive(p.getBooleanValue()); break;
                case 4: value.setBalance(text(p)); break;
                case 5: value.setPicture(text(p)); break;
                case 6: value.setAge(p.getIntValue()); break;
                case 7: value.setEyeColor(text(p)); break;
                case 8: value.setName(text(p)); break;
                case 9: value.setGender(text(p)); break;
                case 10: value.setCompany(text(p)); break;
                case 11: value.setEmail(text(p)); break;
                case 12: value.setPhone(text(p)); break;
                case 13: value.setAddress(text(p)); break;
                case 14: value.setAbout(text(p)); break;
                case 15: value.setRegistered(text(p)); break;
                case 16: value.setLatitude(p.getDoubleValue()); break;
                case 17: value.setLongitude(p.getDoubleValue()); break;
                case 18: value.setTags(readTags(p)); break;
                case 19: value.setFriends(readFriends(p)); break;
                case 20: value.setGreeting(text(p)); break;
                case 21: value.setFavoriteFruit(text(p)); break;
                default: skipUnknown(p, field);
            }
        }
        return value;
    }

    private static List<String> readTags(JsonParser p) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NULL) return null;
        require(p.currentToken(), JsonToken.START_ARRAY);
        List<String> list = new ArrayList<>();
        JsonToken token;
        while ((token = p.nextToken()) != JsonToken.END_ARRAY) {
            if (token == null) throw new IOException("Unexpected EOF in tags array");
            list.add(text(p));
        }
        return list;
    }

    private static List<Users.Friend> readFriends(JsonParser p) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NULL) return null;
        require(p.currentToken(), JsonToken.START_ARRAY);
        List<Users.Friend> list = new ArrayList<>();
        JsonToken token;
        while ((token = p.nextToken()) != JsonToken.END_ARRAY) {
            if (token == null) throw new IOException("Unexpected EOF in friends array");
            list.add(token == JsonToken.VALUE_NULL ? null : readFriend(p));
        }
        return list;
    }

    private static Users.Friend readFriend(JsonParser p) throws IOException {
        require(p.currentToken(), JsonToken.START_OBJECT);
        String id = null;
        String name = null;
        int field;
        while ((field = p.nextNameMatchAndToken(FRIEND))
                != PropertyNameMatcher.MATCH_END_OBJECT) {
            switch (field) {
                case 0: id = text(p); break;
                case 1: name = text(p); break;
                default: skipUnknown(p, field);
            }
        }
        return Users.Friend.create(id, name);
    }

    private static String text(JsonParser p) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NULL) return null;
        require(p.currentToken(), JsonToken.VALUE_STRING);
        return p.getString();
    }

    private static void skipUnknown(JsonParser p, int match) throws IOException {
        if (match != PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
            throw new IOException("Unexpected property matcher result " + match);
        }
        p.nextToken();
        p.skipChildren();
    }

    private static void require(JsonToken actual, JsonToken expected) throws IOException {
        if (actual != expected) {
            throw new IOException("Expected " + expected + ", got " + actual);
        }
    }

    /** Run outside JMH to validate fixtures and unusual property ordering. */
    public static void main(String[] args) throws Exception {
        for (int size : new int[]{1, 10, 100}) {
            for (long seed : new long[]{12345L, 324L, 43201L}) {
                FabienFixtures.Fixture fixture =
                        FabienFixtures.create("jackson3", "users", "pojo", size, seed);
                FabienFixtures.verify("handwritten/" + size + "/" + seed,
                        fixture.value, readUsers(fixture.input));
            }
        }
        String shuffled = "{\"ignored\":{\"nested\":[1,{\"x\":2}]},"
                + "\"users\":[{\"friends\":[{\"name\":\"Alice\","
                + "\"unknown\":{\"a\":[true,null]},\"id\":\"42\"}],"
                + "\"tags\":[null,\"tag\"],\"name\":\"Bob\","
                + "\"index\":7,\"isActive\":true,\"latitude\":1.5,"
                + "\"longitude\":-2.25,\"favoriteFruit\":null,"
                + "\"anotherUnknown\":[{\"y\":1}]}]}";
        Users result = readUsers(shuffled.getBytes(StandardCharsets.UTF_8));
        if (result.getUsers().size() != 1) throw new AssertionError("root list");
        Users.User user = result.getUsers().get(0);
        if (!"Bob".equals(user.getName()) || user.getIndex() != 7
                || !user.getIsActive() || user.getLatitude() != 1.5
                || user.getLongitude() != -2.25 || user.getTags().size() != 2
                || user.getTags().get(0) != null
                || !"42".equals(user.getFriends().get(0).getId())
                || !"Alice".equals(user.getFriends().get(0).getName())) {
            throw new AssertionError("Unknown or shuffled property handling failed");
        }
        System.out.println("Handwritten Jackson3 smoke tests: PASS");
    }
}
