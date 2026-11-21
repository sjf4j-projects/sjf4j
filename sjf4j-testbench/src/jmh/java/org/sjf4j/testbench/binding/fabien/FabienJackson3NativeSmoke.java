package org.sjf4j.testbench.binding.fabien;

import org.sjf4j.testbench.model.fabien.Users;

import java.nio.charset.StandardCharsets;

/** Correctness smoke suite for generated native Jackson3 compiled readers. */
public final class FabienJackson3NativeSmoke {

    private FabienJackson3NativeSmoke() {}

    public static void main(String[] args) throws Exception {
        FabienBindings.Reader reader =
                FabienBindings.reader("jackson3", "compiled", "users", "pojo");

        for (int kb : new int[]{1, 10, 100}) {
            for (long seed : new long[]{12345L, 324L, 43201L}) {
                FabienFixtures.Fixture fixture =
                        FabienFixtures.create("jackson3", "users", "pojo", kb, seed);
                FabienFixtures.verify("native/" + kb + "/" + seed,
                        fixture.value, reader.read(fixture.input));
            }
        }

        String reordered = "{\"ignored\":{\"nested\":[1,{\"x\":2}]},"
                + "\"users\":[null,{\"friends\":[{\"name\":\"Alice\","
                + "\"unknown\":{\"a\":[true,null]},\"id\":\"42\"}],"
                + "\"tags\":[null,\"tag\"],\"name\":\"Bob\","
                + "\"index\":7,\"isActive\":true,\"latitude\":1.5,"
                + "\"longitude\":-2.25,\"favoriteFruit\":null,"
                + "\"anotherUnknown\":[{\"y\":1}]}]}";
        Users users = parse(reader, reordered);
        if (users.getUsers().size() != 2 || users.getUsers().get(0) != null) {
            throw new AssertionError("Array null element");
        }
        Users.User user = users.getUsers().get(1);
        if (!"Bob".equals(user.getName()) || user.getIndex() != 7
                || !user.getIsActive() || user.getLatitude() != 1.5
                || user.getLongitude() != -2.25 || user.getTags().size() != 2
                || user.getTags().get(0) != null
                || !"42".equals(user.getFriends().get(0).getId())
                || !"Alice".equals(user.getFriends().get(0).getName())) {
            throw new AssertionError("Unknown or reordered properties");
        }

        if (parse(reader, "null") != null) throw new AssertionError("Root null");
        if (parse(reader, "{\"users\":null}").getUsers() != null) {
            throw new AssertionError("List null");
        }
        if (!parse(reader, "{\"users\":[]}").getUsers().isEmpty()) {
            throw new AssertionError("Empty list");
        }
        if (parse(reader, "{\"users\":[{}]}").getUsers().get(0) == null) {
            throw new AssertionError("Empty properties");
        }
        assertReject(reader, "{\"users\":[]} true");
        assertReject(reader, "{\"users\":[{\"id\":2}]}");
        assertReject(reader, "{\"users\":[{\"name\":\"unfinished");
        System.out.println("Native compiled Jackson3 smoke tests: PASS");
    }

    private static Users parse(FabienBindings.Reader reader, String json) throws Exception {
        return (Users) reader.read(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertReject(FabienBindings.Reader reader, String json) throws Exception {
        try {
            parse(reader, json);
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError("Accepted invalid JSON: " + json);
    }
}
