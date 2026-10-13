package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class NodeMapperFixtures {
    private NodeMapperFixtures() {}

    static Profile profile() {
        Profile profile = new Profile();
        profile.name = "Ada";
        profile.age = 32;
        profile.aliases = new ArrayList<>(Arrays.asList("Ada", "A"));
        profile.scores = new LinkedHashSet<>(Arrays.asList(1, 2));
        profile.ranks = new int[]{3, 5};
        profile.details = JsonObject.of("address", JsonObject.of("city", "Oslo"));
        profile.status = Status.OK;
        return profile;
    }

    static class IntJajo extends JsonArray {
        @Override
        public Class<?> elementClass() { return Integer.class; }
    }

    enum Status { OK, FAILED }

    static class GenericUser {
        public String name;
        public Object meta;
    }

    static class Box<T> {
        public T value;
    }

    static class Profile {
        public String name;
        public int age;
        public List<String> aliases;
        public Set<Integer> scores;
        public int[] ranks;
        public JsonObject details;
        public Status status;
    }
}
