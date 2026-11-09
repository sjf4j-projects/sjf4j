package org.sjf4j.testbench.binding.fabien;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Write;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Generator;
import org.sjf4j.binding.PooledByteArrayOutputStream;
import org.sjf4j.testbench.model.fabien.Users;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.SerializableString;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Field-group elimination with matched Jackson3 generator and Pooled sink:
 * manual native JsonGenerator vs compile-time generated binder vs runtime.
 * For EACH variant the two/three writers receive the SAME immutable POJO
 * instance and emit the same JSON members. All data is prepared at trial setup.
 *
 * Group-only outputs are smaller than the original; interpret only WITHIN
 * each variant's manual/runtime/compiled comparison, not across groups.
 * No changes to SJF4J production output implementation.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
public class Jackson3UserFieldAblationBenchmark {

    @Param({"manual", "runtime", "compiled"})
    public String writer;

    @Param({"original", "full", "noStrings", "noNumeric", "noTags", "noFriends",
            "stringsOnly", "numericOnly", "tagsOnly", "friendsOnly"})
    public String fields;

    @Param({"12345", "324"})
    public long seed;

    @Param({"100"})
    public int sizeKb;

    @FunctionalInterface
    private interface Operation {
        byte[] write() throws Exception;
    }
    @FunctionalInterface
    private interface StreamWriter {
        void write(OutputStream stream) throws Exception;
    }
    private Operation operation;
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final SerializableString USERS_NAME = new SerializedString("users");
    private static final SerializableString ID_NAME = new SerializedString("id");
    private static final SerializableString INDEX_NAME = new SerializedString("index");
    private static final SerializableString GUID_NAME = new SerializedString("guid");
    private static final SerializableString ISACTIVE_NAME = new SerializedString("isActive");
    private static final SerializableString BALANCE_NAME = new SerializedString("balance");
    private static final SerializableString PICTURE_NAME = new SerializedString("picture");
    private static final SerializableString AGE_NAME = new SerializedString("age");
    private static final SerializableString EYECOLOR_NAME = new SerializedString("eyeColor");
    private static final SerializableString NAME_NAME = new SerializedString("name");
    private static final SerializableString GENDER_NAME = new SerializedString("gender");
    private static final SerializableString COMPANY_NAME = new SerializedString("company");
    private static final SerializableString EMAIL_NAME = new SerializedString("email");
    private static final SerializableString PHONE_NAME = new SerializedString("phone");
    private static final SerializableString ADDRESS_NAME = new SerializedString("address");
    private static final SerializableString ABOUT_NAME = new SerializedString("about");
    private static final SerializableString REGISTERED_NAME = new SerializedString("registered");
    private static final SerializableString LATITUDE_NAME = new SerializedString("latitude");
    private static final SerializableString LONGITUDE_NAME = new SerializedString("longitude");
    private static final SerializableString TAGS_NAME = new SerializedString("tags");
    private static final SerializableString FRIENDS_NAME = new SerializedString("friends");
    private static final SerializableString GREETING_NAME = new SerializedString("greeting");
    private static final SerializableString FAVORITEFRUIT_NAME = new SerializedString("favoriteFruit");

    private static byte[] pooled(StreamWriter write) throws Exception {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try {
            write.write(output);
            return output.toByteArray();
        } finally {
            output.recycle();
        }
    }

    private static void nullableString(JsonGenerator g, SerializableString name, String value) {
        g.writeName(name);
        if (value == null) g.writeNull();
        else g.writeString(value);
    }

    private static void writeTags(JsonGenerator g, List<String> tags) {
        if (tags == null) { g.writeNull(); return; }
        g.writeStartArray();
        for (int i = 0; i < tags.size(); i++) {
            String tag = tags.get(i);
            if (tag == null) g.writeNull();
            else g.writeString(tag);
        }
        g.writeEndArray();
    }

    private static void writeFriends(JsonGenerator g, List<Users.Friend> friends) {
        if (friends == null) { g.writeNull(); return; }
        g.writeStartArray();
        for (int i = 0; i < friends.size(); i++) {
            Users.Friend friend = friends.get(i);
            if (friend == null) { g.writeNull(); continue; }
            g.writeStartObject();
            nullableString(g, ID_NAME, friend.getId());
            nullableString(g, NAME_NAME, friend.getName());
            g.writeEndObject();
        }
        g.writeEndArray();
    }

    private static void manualOriginal(JsonGenerator g, Users root) {
        if (root == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeName(USERS_NAME);
        List<Users.User> list = root.getUsers();
        if (list == null) g.writeNull();
        else {
            g.writeStartArray();
            for (int i = 0; i < list.size(); i++) {
                Users.User u = list.get(i);
                if (u == null) { g.writeNull(); continue; }
                g.writeStartObject();
        nullableString(g, ID_NAME, u.getId());
        g.writeName(INDEX_NAME);
        g.writeNumber(u.getIndex());
        nullableString(g, GUID_NAME, u.getGuid());
        g.writeName(ISACTIVE_NAME);
        g.writeBoolean(u.getIsActive());
        nullableString(g, BALANCE_NAME, u.getBalance());
        nullableString(g, PICTURE_NAME, u.getPicture());
        g.writeName(AGE_NAME);
        g.writeNumber(u.getAge());
        nullableString(g, EYECOLOR_NAME, u.getEyeColor());
        nullableString(g, NAME_NAME, u.getName());
        nullableString(g, GENDER_NAME, u.getGender());
        nullableString(g, COMPANY_NAME, u.getCompany());
        nullableString(g, EMAIL_NAME, u.getEmail());
        nullableString(g, PHONE_NAME, u.getPhone());
        nullableString(g, ADDRESS_NAME, u.getAddress());
        nullableString(g, ABOUT_NAME, u.getAbout());
        nullableString(g, REGISTERED_NAME, u.getRegistered());
        g.writeName(LATITUDE_NAME);
        g.writeNumber(u.getLatitude());
        g.writeName(LONGITUDE_NAME);
        g.writeNumber(u.getLongitude());
        g.writeName(TAGS_NAME);
        writeTags(g, u.getTags());
        g.writeName(FRIENDS_NAME);
        writeFriends(g, u.getFriends());
        nullableString(g, GREETING_NAME, u.getGreeting());
        nullableString(g, FAVORITEFRUIT_NAME, u.getFavoriteFruit());
                g.writeEndObject();
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    public static final class FullUser {
        private final String id;
        private final int index;
        private final String guid;
        private final boolean isActive;
        private final String balance;
        private final String picture;
        private final int age;
        private final String eyeColor;
        private final String name;
        private final String gender;
        private final String company;
        private final String email;
        private final String phone;
        private final String address;
        private final String about;
        private final String registered;
        private final double latitude;
        private final double longitude;
        private final List<String> tags;
        private final List<Users.Friend> friends;
        private final String greeting;
        private final String favoriteFruit;
        public FullUser(Users.User source) {
            this.id = source.getId();
            this.index = source.getIndex();
            this.guid = source.getGuid();
            this.isActive = source.getIsActive();
            this.balance = source.getBalance();
            this.picture = source.getPicture();
            this.age = source.getAge();
            this.eyeColor = source.getEyeColor();
            this.name = source.getName();
            this.gender = source.getGender();
            this.company = source.getCompany();
            this.email = source.getEmail();
            this.phone = source.getPhone();
            this.address = source.getAddress();
            this.about = source.getAbout();
            this.registered = source.getRegistered();
            this.latitude = source.getLatitude();
            this.longitude = source.getLongitude();
            this.tags = source.getTags();
            this.friends = source.getFriends();
            this.greeting = source.getGreeting();
            this.favoriteFruit = source.getFavoriteFruit();
        }
        public String getId() { return id; }
        public int getIndex() { return index; }
        public String getGuid() { return guid; }
        public boolean getIsActive() { return isActive; }
        public String getBalance() { return balance; }
        public String getPicture() { return picture; }
        public int getAge() { return age; }
        public String getEyeColor() { return eyeColor; }
        public String getName() { return name; }
        public String getGender() { return gender; }
        public String getCompany() { return company; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public String getAddress() { return address; }
        public String getAbout() { return about; }
        public String getRegistered() { return registered; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public List<String> getTags() { return tags; }
        public List<Users.Friend> getFriends() { return friends; }
        public String getGreeting() { return greeting; }
        public String getFavoriteFruit() { return favoriteFruit; }
    }

    public static final class FullUsers {
        private final List<FullUser> users;
        public FullUsers(Users original) {
            List<Users.User> source = original.getUsers();
            if (source == null) { users = null; return; }
            List<FullUser> items = new ArrayList<>(source.size());
            for (Users.User user : source) items.add(user == null ? null : new FullUser(user));
            this.users = items;
        }
        public List<FullUser> getUsers() { return users; }
    }

    private static void manualFull(JsonGenerator g, FullUsers root) {
        if (root == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeName(USERS_NAME);
        List<FullUser> list = root.getUsers();
        if (list == null) g.writeNull();
        else {
            g.writeStartArray();
            for (int i = 0; i < list.size(); i++) {
                FullUser u = list.get(i);
                if (u == null) { g.writeNull(); continue; }
                g.writeStartObject();
        nullableString(g, ID_NAME, u.getId());
        g.writeName(INDEX_NAME);
        g.writeNumber(u.getIndex());
        nullableString(g, GUID_NAME, u.getGuid());
        g.writeName(ISACTIVE_NAME);
        g.writeBoolean(u.getIsActive());
        nullableString(g, BALANCE_NAME, u.getBalance());
        nullableString(g, PICTURE_NAME, u.getPicture());
        g.writeName(AGE_NAME);
        g.writeNumber(u.getAge());
        nullableString(g, EYECOLOR_NAME, u.getEyeColor());
        nullableString(g, NAME_NAME, u.getName());
        nullableString(g, GENDER_NAME, u.getGender());
        nullableString(g, COMPANY_NAME, u.getCompany());
        nullableString(g, EMAIL_NAME, u.getEmail());
        nullableString(g, PHONE_NAME, u.getPhone());
        nullableString(g, ADDRESS_NAME, u.getAddress());
        nullableString(g, ABOUT_NAME, u.getAbout());
        nullableString(g, REGISTERED_NAME, u.getRegistered());
        g.writeName(LATITUDE_NAME);
        g.writeNumber(u.getLatitude());
        g.writeName(LONGITUDE_NAME);
        g.writeNumber(u.getLongitude());
        g.writeName(TAGS_NAME);
        writeTags(g, u.getTags());
        g.writeName(FRIENDS_NAME);
        writeFriends(g, u.getFriends());
        nullableString(g, GREETING_NAME, u.getGreeting());
        nullableString(g, FAVORITEFRUIT_NAME, u.getFavoriteFruit());
                g.writeEndObject();
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    public static final class NoStringsUser {
        private final int index;
        private final boolean isActive;
        private final int age;
        private final double latitude;
        private final double longitude;
        private final List<String> tags;
        private final List<Users.Friend> friends;
        public NoStringsUser(Users.User source) {
            this.index = source.getIndex();
            this.isActive = source.getIsActive();
            this.age = source.getAge();
            this.latitude = source.getLatitude();
            this.longitude = source.getLongitude();
            this.tags = source.getTags();
            this.friends = source.getFriends();
        }
        public int getIndex() { return index; }
        public boolean getIsActive() { return isActive; }
        public int getAge() { return age; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public List<String> getTags() { return tags; }
        public List<Users.Friend> getFriends() { return friends; }
    }

    public static final class NoStringsUsers {
        private final List<NoStringsUser> users;
        public NoStringsUsers(Users original) {
            List<Users.User> source = original.getUsers();
            if (source == null) { users = null; return; }
            List<NoStringsUser> items = new ArrayList<>(source.size());
            for (Users.User user : source) items.add(user == null ? null : new NoStringsUser(user));
            this.users = items;
        }
        public List<NoStringsUser> getUsers() { return users; }
    }

    private static void manualNoStrings(JsonGenerator g, NoStringsUsers root) {
        if (root == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeName(USERS_NAME);
        List<NoStringsUser> list = root.getUsers();
        if (list == null) g.writeNull();
        else {
            g.writeStartArray();
            for (int i = 0; i < list.size(); i++) {
                NoStringsUser u = list.get(i);
                if (u == null) { g.writeNull(); continue; }
                g.writeStartObject();
        g.writeName(INDEX_NAME);
        g.writeNumber(u.getIndex());
        g.writeName(ISACTIVE_NAME);
        g.writeBoolean(u.getIsActive());
        g.writeName(AGE_NAME);
        g.writeNumber(u.getAge());
        g.writeName(LATITUDE_NAME);
        g.writeNumber(u.getLatitude());
        g.writeName(LONGITUDE_NAME);
        g.writeNumber(u.getLongitude());
        g.writeName(TAGS_NAME);
        writeTags(g, u.getTags());
        g.writeName(FRIENDS_NAME);
        writeFriends(g, u.getFriends());
                g.writeEndObject();
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    public static final class NoNumericUser {
        private final String id;
        private final String guid;
        private final String balance;
        private final String picture;
        private final String eyeColor;
        private final String name;
        private final String gender;
        private final String company;
        private final String email;
        private final String phone;
        private final String address;
        private final String about;
        private final String registered;
        private final List<String> tags;
        private final List<Users.Friend> friends;
        private final String greeting;
        private final String favoriteFruit;
        public NoNumericUser(Users.User source) {
            this.id = source.getId();
            this.guid = source.getGuid();
            this.balance = source.getBalance();
            this.picture = source.getPicture();
            this.eyeColor = source.getEyeColor();
            this.name = source.getName();
            this.gender = source.getGender();
            this.company = source.getCompany();
            this.email = source.getEmail();
            this.phone = source.getPhone();
            this.address = source.getAddress();
            this.about = source.getAbout();
            this.registered = source.getRegistered();
            this.tags = source.getTags();
            this.friends = source.getFriends();
            this.greeting = source.getGreeting();
            this.favoriteFruit = source.getFavoriteFruit();
        }
        public String getId() { return id; }
        public String getGuid() { return guid; }
        public String getBalance() { return balance; }
        public String getPicture() { return picture; }
        public String getEyeColor() { return eyeColor; }
        public String getName() { return name; }
        public String getGender() { return gender; }
        public String getCompany() { return company; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public String getAddress() { return address; }
        public String getAbout() { return about; }
        public String getRegistered() { return registered; }
        public List<String> getTags() { return tags; }
        public List<Users.Friend> getFriends() { return friends; }
        public String getGreeting() { return greeting; }
        public String getFavoriteFruit() { return favoriteFruit; }
    }

    public static final class NoNumericUsers {
        private final List<NoNumericUser> users;
        public NoNumericUsers(Users original) {
            List<Users.User> source = original.getUsers();
            if (source == null) { users = null; return; }
            List<NoNumericUser> items = new ArrayList<>(source.size());
            for (Users.User user : source) items.add(user == null ? null : new NoNumericUser(user));
            this.users = items;
        }
        public List<NoNumericUser> getUsers() { return users; }
    }

    private static void manualNoNumeric(JsonGenerator g, NoNumericUsers root) {
        if (root == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeName(USERS_NAME);
        List<NoNumericUser> list = root.getUsers();
        if (list == null) g.writeNull();
        else {
            g.writeStartArray();
            for (int i = 0; i < list.size(); i++) {
                NoNumericUser u = list.get(i);
                if (u == null) { g.writeNull(); continue; }
                g.writeStartObject();
        nullableString(g, ID_NAME, u.getId());
        nullableString(g, GUID_NAME, u.getGuid());
        nullableString(g, BALANCE_NAME, u.getBalance());
        nullableString(g, PICTURE_NAME, u.getPicture());
        nullableString(g, EYECOLOR_NAME, u.getEyeColor());
        nullableString(g, NAME_NAME, u.getName());
        nullableString(g, GENDER_NAME, u.getGender());
        nullableString(g, COMPANY_NAME, u.getCompany());
        nullableString(g, EMAIL_NAME, u.getEmail());
        nullableString(g, PHONE_NAME, u.getPhone());
        nullableString(g, ADDRESS_NAME, u.getAddress());
        nullableString(g, ABOUT_NAME, u.getAbout());
        nullableString(g, REGISTERED_NAME, u.getRegistered());
        g.writeName(TAGS_NAME);
        writeTags(g, u.getTags());
        g.writeName(FRIENDS_NAME);
        writeFriends(g, u.getFriends());
        nullableString(g, GREETING_NAME, u.getGreeting());
        nullableString(g, FAVORITEFRUIT_NAME, u.getFavoriteFruit());
                g.writeEndObject();
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    public static final class NoTagsUser {
        private final String id;
        private final int index;
        private final String guid;
        private final boolean isActive;
        private final String balance;
        private final String picture;
        private final int age;
        private final String eyeColor;
        private final String name;
        private final String gender;
        private final String company;
        private final String email;
        private final String phone;
        private final String address;
        private final String about;
        private final String registered;
        private final double latitude;
        private final double longitude;
        private final List<Users.Friend> friends;
        private final String greeting;
        private final String favoriteFruit;
        public NoTagsUser(Users.User source) {
            this.id = source.getId();
            this.index = source.getIndex();
            this.guid = source.getGuid();
            this.isActive = source.getIsActive();
            this.balance = source.getBalance();
            this.picture = source.getPicture();
            this.age = source.getAge();
            this.eyeColor = source.getEyeColor();
            this.name = source.getName();
            this.gender = source.getGender();
            this.company = source.getCompany();
            this.email = source.getEmail();
            this.phone = source.getPhone();
            this.address = source.getAddress();
            this.about = source.getAbout();
            this.registered = source.getRegistered();
            this.latitude = source.getLatitude();
            this.longitude = source.getLongitude();
            this.friends = source.getFriends();
            this.greeting = source.getGreeting();
            this.favoriteFruit = source.getFavoriteFruit();
        }
        public String getId() { return id; }
        public int getIndex() { return index; }
        public String getGuid() { return guid; }
        public boolean getIsActive() { return isActive; }
        public String getBalance() { return balance; }
        public String getPicture() { return picture; }
        public int getAge() { return age; }
        public String getEyeColor() { return eyeColor; }
        public String getName() { return name; }
        public String getGender() { return gender; }
        public String getCompany() { return company; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public String getAddress() { return address; }
        public String getAbout() { return about; }
        public String getRegistered() { return registered; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public List<Users.Friend> getFriends() { return friends; }
        public String getGreeting() { return greeting; }
        public String getFavoriteFruit() { return favoriteFruit; }
    }

    public static final class NoTagsUsers {
        private final List<NoTagsUser> users;
        public NoTagsUsers(Users original) {
            List<Users.User> source = original.getUsers();
            if (source == null) { users = null; return; }
            List<NoTagsUser> items = new ArrayList<>(source.size());
            for (Users.User user : source) items.add(user == null ? null : new NoTagsUser(user));
            this.users = items;
        }
        public List<NoTagsUser> getUsers() { return users; }
    }

    private static void manualNoTags(JsonGenerator g, NoTagsUsers root) {
        if (root == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeName(USERS_NAME);
        List<NoTagsUser> list = root.getUsers();
        if (list == null) g.writeNull();
        else {
            g.writeStartArray();
            for (int i = 0; i < list.size(); i++) {
                NoTagsUser u = list.get(i);
                if (u == null) { g.writeNull(); continue; }
                g.writeStartObject();
        nullableString(g, ID_NAME, u.getId());
        g.writeName(INDEX_NAME);
        g.writeNumber(u.getIndex());
        nullableString(g, GUID_NAME, u.getGuid());
        g.writeName(ISACTIVE_NAME);
        g.writeBoolean(u.getIsActive());
        nullableString(g, BALANCE_NAME, u.getBalance());
        nullableString(g, PICTURE_NAME, u.getPicture());
        g.writeName(AGE_NAME);
        g.writeNumber(u.getAge());
        nullableString(g, EYECOLOR_NAME, u.getEyeColor());
        nullableString(g, NAME_NAME, u.getName());
        nullableString(g, GENDER_NAME, u.getGender());
        nullableString(g, COMPANY_NAME, u.getCompany());
        nullableString(g, EMAIL_NAME, u.getEmail());
        nullableString(g, PHONE_NAME, u.getPhone());
        nullableString(g, ADDRESS_NAME, u.getAddress());
        nullableString(g, ABOUT_NAME, u.getAbout());
        nullableString(g, REGISTERED_NAME, u.getRegistered());
        g.writeName(LATITUDE_NAME);
        g.writeNumber(u.getLatitude());
        g.writeName(LONGITUDE_NAME);
        g.writeNumber(u.getLongitude());
        g.writeName(FRIENDS_NAME);
        writeFriends(g, u.getFriends());
        nullableString(g, GREETING_NAME, u.getGreeting());
        nullableString(g, FAVORITEFRUIT_NAME, u.getFavoriteFruit());
                g.writeEndObject();
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    public static final class NoFriendsUser {
        private final String id;
        private final int index;
        private final String guid;
        private final boolean isActive;
        private final String balance;
        private final String picture;
        private final int age;
        private final String eyeColor;
        private final String name;
        private final String gender;
        private final String company;
        private final String email;
        private final String phone;
        private final String address;
        private final String about;
        private final String registered;
        private final double latitude;
        private final double longitude;
        private final List<String> tags;
        private final String greeting;
        private final String favoriteFruit;
        public NoFriendsUser(Users.User source) {
            this.id = source.getId();
            this.index = source.getIndex();
            this.guid = source.getGuid();
            this.isActive = source.getIsActive();
            this.balance = source.getBalance();
            this.picture = source.getPicture();
            this.age = source.getAge();
            this.eyeColor = source.getEyeColor();
            this.name = source.getName();
            this.gender = source.getGender();
            this.company = source.getCompany();
            this.email = source.getEmail();
            this.phone = source.getPhone();
            this.address = source.getAddress();
            this.about = source.getAbout();
            this.registered = source.getRegistered();
            this.latitude = source.getLatitude();
            this.longitude = source.getLongitude();
            this.tags = source.getTags();
            this.greeting = source.getGreeting();
            this.favoriteFruit = source.getFavoriteFruit();
        }
        public String getId() { return id; }
        public int getIndex() { return index; }
        public String getGuid() { return guid; }
        public boolean getIsActive() { return isActive; }
        public String getBalance() { return balance; }
        public String getPicture() { return picture; }
        public int getAge() { return age; }
        public String getEyeColor() { return eyeColor; }
        public String getName() { return name; }
        public String getGender() { return gender; }
        public String getCompany() { return company; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public String getAddress() { return address; }
        public String getAbout() { return about; }
        public String getRegistered() { return registered; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public List<String> getTags() { return tags; }
        public String getGreeting() { return greeting; }
        public String getFavoriteFruit() { return favoriteFruit; }
    }

    public static final class NoFriendsUsers {
        private final List<NoFriendsUser> users;
        public NoFriendsUsers(Users original) {
            List<Users.User> source = original.getUsers();
            if (source == null) { users = null; return; }
            List<NoFriendsUser> items = new ArrayList<>(source.size());
            for (Users.User user : source) items.add(user == null ? null : new NoFriendsUser(user));
            this.users = items;
        }
        public List<NoFriendsUser> getUsers() { return users; }
    }

    private static void manualNoFriends(JsonGenerator g, NoFriendsUsers root) {
        if (root == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeName(USERS_NAME);
        List<NoFriendsUser> list = root.getUsers();
        if (list == null) g.writeNull();
        else {
            g.writeStartArray();
            for (int i = 0; i < list.size(); i++) {
                NoFriendsUser u = list.get(i);
                if (u == null) { g.writeNull(); continue; }
                g.writeStartObject();
        nullableString(g, ID_NAME, u.getId());
        g.writeName(INDEX_NAME);
        g.writeNumber(u.getIndex());
        nullableString(g, GUID_NAME, u.getGuid());
        g.writeName(ISACTIVE_NAME);
        g.writeBoolean(u.getIsActive());
        nullableString(g, BALANCE_NAME, u.getBalance());
        nullableString(g, PICTURE_NAME, u.getPicture());
        g.writeName(AGE_NAME);
        g.writeNumber(u.getAge());
        nullableString(g, EYECOLOR_NAME, u.getEyeColor());
        nullableString(g, NAME_NAME, u.getName());
        nullableString(g, GENDER_NAME, u.getGender());
        nullableString(g, COMPANY_NAME, u.getCompany());
        nullableString(g, EMAIL_NAME, u.getEmail());
        nullableString(g, PHONE_NAME, u.getPhone());
        nullableString(g, ADDRESS_NAME, u.getAddress());
        nullableString(g, ABOUT_NAME, u.getAbout());
        nullableString(g, REGISTERED_NAME, u.getRegistered());
        g.writeName(LATITUDE_NAME);
        g.writeNumber(u.getLatitude());
        g.writeName(LONGITUDE_NAME);
        g.writeNumber(u.getLongitude());
        g.writeName(TAGS_NAME);
        writeTags(g, u.getTags());
        nullableString(g, GREETING_NAME, u.getGreeting());
        nullableString(g, FAVORITEFRUIT_NAME, u.getFavoriteFruit());
                g.writeEndObject();
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    public static final class StringsOnlyUser {
        private final String id;
        private final String guid;
        private final String balance;
        private final String picture;
        private final String eyeColor;
        private final String name;
        private final String gender;
        private final String company;
        private final String email;
        private final String phone;
        private final String address;
        private final String about;
        private final String registered;
        private final String greeting;
        private final String favoriteFruit;
        public StringsOnlyUser(Users.User source) {
            this.id = source.getId();
            this.guid = source.getGuid();
            this.balance = source.getBalance();
            this.picture = source.getPicture();
            this.eyeColor = source.getEyeColor();
            this.name = source.getName();
            this.gender = source.getGender();
            this.company = source.getCompany();
            this.email = source.getEmail();
            this.phone = source.getPhone();
            this.address = source.getAddress();
            this.about = source.getAbout();
            this.registered = source.getRegistered();
            this.greeting = source.getGreeting();
            this.favoriteFruit = source.getFavoriteFruit();
        }
        public String getId() { return id; }
        public String getGuid() { return guid; }
        public String getBalance() { return balance; }
        public String getPicture() { return picture; }
        public String getEyeColor() { return eyeColor; }
        public String getName() { return name; }
        public String getGender() { return gender; }
        public String getCompany() { return company; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public String getAddress() { return address; }
        public String getAbout() { return about; }
        public String getRegistered() { return registered; }
        public String getGreeting() { return greeting; }
        public String getFavoriteFruit() { return favoriteFruit; }
    }

    public static final class StringsOnlyUsers {
        private final List<StringsOnlyUser> users;
        public StringsOnlyUsers(Users original) {
            List<Users.User> source = original.getUsers();
            if (source == null) { users = null; return; }
            List<StringsOnlyUser> items = new ArrayList<>(source.size());
            for (Users.User user : source) items.add(user == null ? null : new StringsOnlyUser(user));
            this.users = items;
        }
        public List<StringsOnlyUser> getUsers() { return users; }
    }

    private static void manualStringsOnly(JsonGenerator g, StringsOnlyUsers root) {
        if (root == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeName(USERS_NAME);
        List<StringsOnlyUser> list = root.getUsers();
        if (list == null) g.writeNull();
        else {
            g.writeStartArray();
            for (int i = 0; i < list.size(); i++) {
                StringsOnlyUser u = list.get(i);
                if (u == null) { g.writeNull(); continue; }
                g.writeStartObject();
        nullableString(g, ID_NAME, u.getId());
        nullableString(g, GUID_NAME, u.getGuid());
        nullableString(g, BALANCE_NAME, u.getBalance());
        nullableString(g, PICTURE_NAME, u.getPicture());
        nullableString(g, EYECOLOR_NAME, u.getEyeColor());
        nullableString(g, NAME_NAME, u.getName());
        nullableString(g, GENDER_NAME, u.getGender());
        nullableString(g, COMPANY_NAME, u.getCompany());
        nullableString(g, EMAIL_NAME, u.getEmail());
        nullableString(g, PHONE_NAME, u.getPhone());
        nullableString(g, ADDRESS_NAME, u.getAddress());
        nullableString(g, ABOUT_NAME, u.getAbout());
        nullableString(g, REGISTERED_NAME, u.getRegistered());
        nullableString(g, GREETING_NAME, u.getGreeting());
        nullableString(g, FAVORITEFRUIT_NAME, u.getFavoriteFruit());
                g.writeEndObject();
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    public static final class NumericOnlyUser {
        private final int index;
        private final boolean isActive;
        private final int age;
        private final double latitude;
        private final double longitude;
        public NumericOnlyUser(Users.User source) {
            this.index = source.getIndex();
            this.isActive = source.getIsActive();
            this.age = source.getAge();
            this.latitude = source.getLatitude();
            this.longitude = source.getLongitude();
        }
        public int getIndex() { return index; }
        public boolean getIsActive() { return isActive; }
        public int getAge() { return age; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
    }

    public static final class NumericOnlyUsers {
        private final List<NumericOnlyUser> users;
        public NumericOnlyUsers(Users original) {
            List<Users.User> source = original.getUsers();
            if (source == null) { users = null; return; }
            List<NumericOnlyUser> items = new ArrayList<>(source.size());
            for (Users.User user : source) items.add(user == null ? null : new NumericOnlyUser(user));
            this.users = items;
        }
        public List<NumericOnlyUser> getUsers() { return users; }
    }

    private static void manualNumericOnly(JsonGenerator g, NumericOnlyUsers root) {
        if (root == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeName(USERS_NAME);
        List<NumericOnlyUser> list = root.getUsers();
        if (list == null) g.writeNull();
        else {
            g.writeStartArray();
            for (int i = 0; i < list.size(); i++) {
                NumericOnlyUser u = list.get(i);
                if (u == null) { g.writeNull(); continue; }
                g.writeStartObject();
        g.writeName(INDEX_NAME);
        g.writeNumber(u.getIndex());
        g.writeName(ISACTIVE_NAME);
        g.writeBoolean(u.getIsActive());
        g.writeName(AGE_NAME);
        g.writeNumber(u.getAge());
        g.writeName(LATITUDE_NAME);
        g.writeNumber(u.getLatitude());
        g.writeName(LONGITUDE_NAME);
        g.writeNumber(u.getLongitude());
                g.writeEndObject();
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    public static final class TagsOnlyUser {
        private final List<String> tags;
        public TagsOnlyUser(Users.User source) {
            this.tags = source.getTags();
        }
        public List<String> getTags() { return tags; }
    }

    public static final class TagsOnlyUsers {
        private final List<TagsOnlyUser> users;
        public TagsOnlyUsers(Users original) {
            List<Users.User> source = original.getUsers();
            if (source == null) { users = null; return; }
            List<TagsOnlyUser> items = new ArrayList<>(source.size());
            for (Users.User user : source) items.add(user == null ? null : new TagsOnlyUser(user));
            this.users = items;
        }
        public List<TagsOnlyUser> getUsers() { return users; }
    }

    private static void manualTagsOnly(JsonGenerator g, TagsOnlyUsers root) {
        if (root == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeName(USERS_NAME);
        List<TagsOnlyUser> list = root.getUsers();
        if (list == null) g.writeNull();
        else {
            g.writeStartArray();
            for (int i = 0; i < list.size(); i++) {
                TagsOnlyUser u = list.get(i);
                if (u == null) { g.writeNull(); continue; }
                g.writeStartObject();
        g.writeName(TAGS_NAME);
        writeTags(g, u.getTags());
                g.writeEndObject();
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    public static final class FriendsOnlyUser {
        private final List<Users.Friend> friends;
        public FriendsOnlyUser(Users.User source) {
            this.friends = source.getFriends();
        }
        public List<Users.Friend> getFriends() { return friends; }
    }

    public static final class FriendsOnlyUsers {
        private final List<FriendsOnlyUser> users;
        public FriendsOnlyUsers(Users original) {
            List<Users.User> source = original.getUsers();
            if (source == null) { users = null; return; }
            List<FriendsOnlyUser> items = new ArrayList<>(source.size());
            for (Users.User user : source) items.add(user == null ? null : new FriendsOnlyUser(user));
            this.users = items;
        }
        public List<FriendsOnlyUser> getUsers() { return users; }
    }

    private static void manualFriendsOnly(JsonGenerator g, FriendsOnlyUsers root) {
        if (root == null) { g.writeNull(); return; }
        g.writeStartObject();
        g.writeName(USERS_NAME);
        List<FriendsOnlyUser> list = root.getUsers();
        if (list == null) g.writeNull();
        else {
            g.writeStartArray();
            for (int i = 0; i < list.size(); i++) {
                FriendsOnlyUser u = list.get(i);
                if (u == null) { g.writeNull(); continue; }
                g.writeStartObject();
        g.writeName(FRIENDS_NAME);
        writeFriends(g, u.getFriends());
                g.writeEndObject();
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Compiled {
        @Write void original(Users value, OutputStream output);
        @Write void full(FullUsers value, OutputStream output);
        @Write void noStrings(NoStringsUsers value, OutputStream output);
        @Write void noNumeric(NoNumericUsers value, OutputStream output);
        @Write void noTags(NoTagsUsers value, OutputStream output);
        @Write void noFriends(NoFriendsUsers value, OutputStream output);
        @Write void stringsOnly(StringsOnlyUsers value, OutputStream output);
        @Write void numericOnly(NumericOnlyUsers value, OutputStream output);
        @Write void tagsOnly(TagsOnlyUsers value, OutputStream output);
        @Write void friendsOnly(FriendsOnlyUsers value, OutputStream output);
    }

    @Setup(Level.Trial)
    public void setup() throws Exception {
        Users original = (Users) FabienFixtures.create(
                "jackson3", "users", "pojo", sizeKb, seed).value;
        final Object model;
        final StreamWriter manual;
        final Compiled binder = CompiledInstances.create(Compiled.class);
        final StreamWriter compiled;

        switch (fields) {
            case "original":
                model = original;
                manual = out -> {
                    // Same factory/feature configuration as the compiled writer.
                    Jackson3Binder factory = new Jackson3Binder();
                    try (Jackson3Generator gen = factory.createGenerator(out)) {
                        manualOriginal(gen.nativeWriter(), original);
                    }
                };
                compiled = out -> binder.original(original, out);
                break;
            case "full": {
                FullUsers value = new FullUsers(original);
                model = value;
                manual = out -> {
                    Jackson3Binder factory = new Jackson3Binder();
                    try (Jackson3Generator gen = factory.createGenerator(out)) {
                        manualFull(gen.nativeWriter(), value);
                    }
                };
                compiled = out -> binder.full(value, out);
                break;
            }
            case "noStrings": {
                NoStringsUsers value = new NoStringsUsers(original);
                model = value;
                manual = out -> {
                    Jackson3Binder factory = new Jackson3Binder();
                    try (Jackson3Generator gen = factory.createGenerator(out)) {
                        manualNoStrings(gen.nativeWriter(), value);
                    }
                };
                compiled = out -> binder.noStrings(value, out);
                break;
            }
            case "noNumeric": {
                NoNumericUsers value = new NoNumericUsers(original);
                model = value;
                manual = out -> {
                    Jackson3Binder factory = new Jackson3Binder();
                    try (Jackson3Generator gen = factory.createGenerator(out)) {
                        manualNoNumeric(gen.nativeWriter(), value);
                    }
                };
                compiled = out -> binder.noNumeric(value, out);
                break;
            }
            case "noTags": {
                NoTagsUsers value = new NoTagsUsers(original);
                model = value;
                manual = out -> {
                    Jackson3Binder factory = new Jackson3Binder();
                    try (Jackson3Generator gen = factory.createGenerator(out)) {
                        manualNoTags(gen.nativeWriter(), value);
                    }
                };
                compiled = out -> binder.noTags(value, out);
                break;
            }
            case "noFriends": {
                NoFriendsUsers value = new NoFriendsUsers(original);
                model = value;
                manual = out -> {
                    Jackson3Binder factory = new Jackson3Binder();
                    try (Jackson3Generator gen = factory.createGenerator(out)) {
                        manualNoFriends(gen.nativeWriter(), value);
                    }
                };
                compiled = out -> binder.noFriends(value, out);
                break;
            }
            case "stringsOnly": {
                StringsOnlyUsers value = new StringsOnlyUsers(original);
                model = value;
                manual = out -> {
                    Jackson3Binder factory = new Jackson3Binder();
                    try (Jackson3Generator gen = factory.createGenerator(out)) {
                        manualStringsOnly(gen.nativeWriter(), value);
                    }
                };
                compiled = out -> binder.stringsOnly(value, out);
                break;
            }
            case "numericOnly": {
                NumericOnlyUsers value = new NumericOnlyUsers(original);
                model = value;
                manual = out -> {
                    Jackson3Binder factory = new Jackson3Binder();
                    try (Jackson3Generator gen = factory.createGenerator(out)) {
                        manualNumericOnly(gen.nativeWriter(), value);
                    }
                };
                compiled = out -> binder.numericOnly(value, out);
                break;
            }
            case "tagsOnly": {
                TagsOnlyUsers value = new TagsOnlyUsers(original);
                model = value;
                manual = out -> {
                    Jackson3Binder factory = new Jackson3Binder();
                    try (Jackson3Generator gen = factory.createGenerator(out)) {
                        manualTagsOnly(gen.nativeWriter(), value);
                    }
                };
                compiled = out -> binder.tagsOnly(value, out);
                break;
            }
            case "friendsOnly": {
                FriendsOnlyUsers value = new FriendsOnlyUsers(original);
                model = value;
                manual = out -> {
                    Jackson3Binder factory = new Jackson3Binder();
                    try (Jackson3Generator gen = factory.createGenerator(out)) {
                        manualFriendsOnly(gen.nativeWriter(), value);
                    }
                };
                compiled = out -> binder.friendsOnly(value, out);
                break;
            }
            default: throw new IllegalArgumentException("fields=" + fields);
        }
        Jackson3Binder runtimeBinder = new Jackson3Binder();
        StreamWriter runtime = out -> runtimeBinder.writeNode(out, model);

        byte[] expected = pooled(compiled);
        byte[] hand = pooled(manual);
        byte[] dynamic = pooled(runtime);
        verifySame("manual", expected, hand);
        verifySame("runtime", expected, dynamic);

        switch (writer) {
            case "compiled": operation = () -> pooled(compiled); break;
            case "manual": operation = () -> pooled(manual); break;
            case "runtime": operation = () -> pooled(runtime); break;
            default: throw new IllegalArgumentException("writer=" + writer);
        }
    }

    private static void verifySame(String name, byte[] expected, byte[] actual) throws Exception {
        if (Arrays.equals(expected, actual)) return;
        JsonNode left = JSON.readTree(expected);
        JsonNode right = JSON.readTree(actual);
        if (expected.length != actual.length || !left.equals(right)) {
            throw new IllegalStateException("Different field ablation output: " + name
                    + "; lengths " + expected.length + " vs " + actual.length);
        }
    }

    @Benchmark
    public byte[] write() throws Exception {
        return operation.write();
    }
}
