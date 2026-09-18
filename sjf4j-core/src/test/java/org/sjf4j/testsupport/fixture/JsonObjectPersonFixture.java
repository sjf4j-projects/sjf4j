package org.sjf4j.testsupport.fixture;

import org.sjf4j.JsonObject;

import java.util.List;

/** Shared dynamic-person fixture for tests that exercise JsonObject binding. */
public final class JsonObjectPersonFixture {
    private JsonObjectPersonFixture() {}

    public static class Baby extends JsonObject {
        private String name;
        private int month;

        public Baby() {}
        public Baby(String name, int month) { this.name = name; this.month = month; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getMonth() { return month; }
        public void setMonth(int month) { this.month = month; }
    }

    public static class Person extends JsonObject {
        private String name;
        private int age;
        private JsonObject info;
        private List<Baby> babies;

        public Person() {}
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public JsonObject getInfo() { return info; }
        public void setInfo(JsonObject info) { this.info = info; }
        public List<Baby> getBabies() { return babies; }
        public void setBabies(List<Baby> babies) { this.babies = babies; }
    }
}
