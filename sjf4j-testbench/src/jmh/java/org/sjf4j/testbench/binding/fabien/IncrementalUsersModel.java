package org.sjf4j.testbench.binding.fabien;

import org.sjf4j.testbench.model.fabien.Users;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Diagnostic projections of the Fabien Users POJO. Every stage preserves the
 * root users list and increases only User property complexity. No Clients.
 * Properties retain their original declaration order; Stage09 has all fields.
 */
public final class IncrementalUsersModel {
    private IncrementalUsersModel() {}

    private static <T> List<T> entries(Users source, Function<Users.User,T> projection) {
        List<T> list = new ArrayList<>(source.getUsers().size());
        for (Users.User user : source.getUsers()) list.add(projection.apply(user));
        return list;
    }

    public static Object project(int stage, Users source) {
        switch (stage) {
            case 1: return new Stage01(entries(source, u -> new Entry01(
                    u.getId())));
            case 2: return new Stage02(entries(source, u -> new Entry02(
                    u.getId(), u.getIndex())));
            case 3: return new Stage03(entries(source, u -> new Entry03(
                    u.getId(), u.getIndex(), u.getIsActive())));
            case 4: return new Stage04(entries(source, u -> new Entry04(
                    u.getId(), u.getIndex(), u.getGuid(), u.getIsActive(), u.getBalance(), u.getPicture())));
            case 5: return new Stage05(entries(source, u -> new Entry05(
                    u.getId(), u.getIndex(), u.getGuid(), u.getIsActive(), u.getBalance(), u.getPicture(), u.getEyeColor(), u.getName(), u.getGender(), u.getCompany())));
            case 6: return new Stage06(entries(source, u -> new Entry06(
                    u.getId(), u.getIndex(), u.getGuid(), u.getIsActive(), u.getBalance(), u.getPicture(), u.getEyeColor(), u.getName(), u.getGender(), u.getCompany(), u.getEmail(), u.getPhone(), u.getAddress(), u.getAbout(), u.getRegistered(), u.getGreeting(), u.getFavoriteFruit())));
            case 7: return new Stage07(entries(source, u -> new Entry07(
                    u.getId(), u.getIndex(), u.getGuid(), u.getIsActive(), u.getBalance(), u.getPicture(), u.getAge(), u.getEyeColor(), u.getName(), u.getGender(), u.getCompany(), u.getEmail(), u.getPhone(), u.getAddress(), u.getAbout(), u.getRegistered(), u.getLatitude(), u.getLongitude(), u.getGreeting(), u.getFavoriteFruit())));
            case 8: return new Stage08(entries(source, u -> new Entry08(
                    u.getId(), u.getIndex(), u.getGuid(), u.getIsActive(), u.getBalance(), u.getPicture(), u.getAge(), u.getEyeColor(), u.getName(), u.getGender(), u.getCompany(), u.getEmail(), u.getPhone(), u.getAddress(), u.getAbout(), u.getRegistered(), u.getLatitude(), u.getLongitude(), u.getTags(), u.getGreeting(), u.getFavoriteFruit())));
            case 9: return new Stage09(entries(source, u -> new Entry09(
                    u.getId(), u.getIndex(), u.getGuid(), u.getIsActive(), u.getBalance(), u.getPicture(), u.getAge(), u.getEyeColor(), u.getName(), u.getGender(), u.getCompany(), u.getEmail(), u.getPhone(), u.getAddress(), u.getAbout(), u.getRegistered(), u.getLatitude(), u.getLongitude(), u.getTags(), u.getFriends(), u.getGreeting(), u.getFavoriteFruit())));
            default: throw new IllegalArgumentException("stage: " + stage);
        }
    }

    public static final class Stage01 {
        private final List<Entry01> users;
        public Stage01(List<Entry01> users) { this.users = users; }
        public List<Entry01> getUsers() { return users; }
    }

    public static final class Entry01 {
        private final String id;
        public Entry01(String id) {
            this.id = id;
        }
        public String getId() { return id; }
    }

    public static final class Stage02 {
        private final List<Entry02> users;
        public Stage02(List<Entry02> users) { this.users = users; }
        public List<Entry02> getUsers() { return users; }
    }

    public static final class Entry02 {
        private final String id;
        private final int index;
        public Entry02(String id, int index) {
            this.id = id;
            this.index = index;
        }
        public String getId() { return id; }
        public int getIndex() { return index; }
    }

    public static final class Stage03 {
        private final List<Entry03> users;
        public Stage03(List<Entry03> users) { this.users = users; }
        public List<Entry03> getUsers() { return users; }
    }

    public static final class Entry03 {
        private final String id;
        private final int index;
        private final boolean isActive;
        public Entry03(String id, int index, boolean isActive) {
            this.id = id;
            this.index = index;
            this.isActive = isActive;
        }
        public String getId() { return id; }
        public int getIndex() { return index; }
        public boolean getIsActive() { return isActive; }
    }

    public static final class Stage04 {
        private final List<Entry04> users;
        public Stage04(List<Entry04> users) { this.users = users; }
        public List<Entry04> getUsers() { return users; }
    }

    public static final class Entry04 {
        private final String id;
        private final int index;
        private final String guid;
        private final boolean isActive;
        private final String balance;
        private final String picture;
        public Entry04(String id, int index, String guid, boolean isActive, String balance, String picture) {
            this.id = id;
            this.index = index;
            this.guid = guid;
            this.isActive = isActive;
            this.balance = balance;
            this.picture = picture;
        }
        public String getId() { return id; }
        public int getIndex() { return index; }
        public String getGuid() { return guid; }
        public boolean getIsActive() { return isActive; }
        public String getBalance() { return balance; }
        public String getPicture() { return picture; }
    }

    public static final class Stage05 {
        private final List<Entry05> users;
        public Stage05(List<Entry05> users) { this.users = users; }
        public List<Entry05> getUsers() { return users; }
    }

    public static final class Entry05 {
        private final String id;
        private final int index;
        private final String guid;
        private final boolean isActive;
        private final String balance;
        private final String picture;
        private final String eyeColor;
        private final String name;
        private final String gender;
        private final String company;
        public Entry05(String id, int index, String guid, boolean isActive, String balance, String picture, String eyeColor, String name, String gender, String company) {
            this.id = id;
            this.index = index;
            this.guid = guid;
            this.isActive = isActive;
            this.balance = balance;
            this.picture = picture;
            this.eyeColor = eyeColor;
            this.name = name;
            this.gender = gender;
            this.company = company;
        }
        public String getId() { return id; }
        public int getIndex() { return index; }
        public String getGuid() { return guid; }
        public boolean getIsActive() { return isActive; }
        public String getBalance() { return balance; }
        public String getPicture() { return picture; }
        public String getEyeColor() { return eyeColor; }
        public String getName() { return name; }
        public String getGender() { return gender; }
        public String getCompany() { return company; }
    }

    public static final class Stage06 {
        private final List<Entry06> users;
        public Stage06(List<Entry06> users) { this.users = users; }
        public List<Entry06> getUsers() { return users; }
    }

    public static final class Entry06 {
        private final String id;
        private final int index;
        private final String guid;
        private final boolean isActive;
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
        public Entry06(String id, int index, String guid, boolean isActive, String balance, String picture, String eyeColor, String name, String gender, String company, String email, String phone, String address, String about, String registered, String greeting, String favoriteFruit) {
            this.id = id;
            this.index = index;
            this.guid = guid;
            this.isActive = isActive;
            this.balance = balance;
            this.picture = picture;
            this.eyeColor = eyeColor;
            this.name = name;
            this.gender = gender;
            this.company = company;
            this.email = email;
            this.phone = phone;
            this.address = address;
            this.about = about;
            this.registered = registered;
            this.greeting = greeting;
            this.favoriteFruit = favoriteFruit;
        }
        public String getId() { return id; }
        public int getIndex() { return index; }
        public String getGuid() { return guid; }
        public boolean getIsActive() { return isActive; }
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

    public static final class Stage07 {
        private final List<Entry07> users;
        public Stage07(List<Entry07> users) { this.users = users; }
        public List<Entry07> getUsers() { return users; }
    }

    public static final class Entry07 {
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
        private final String greeting;
        private final String favoriteFruit;
        public Entry07(String id, int index, String guid, boolean isActive, String balance, String picture, int age, String eyeColor, String name, String gender, String company, String email, String phone, String address, String about, String registered, double latitude, double longitude, String greeting, String favoriteFruit) {
            this.id = id;
            this.index = index;
            this.guid = guid;
            this.isActive = isActive;
            this.balance = balance;
            this.picture = picture;
            this.age = age;
            this.eyeColor = eyeColor;
            this.name = name;
            this.gender = gender;
            this.company = company;
            this.email = email;
            this.phone = phone;
            this.address = address;
            this.about = about;
            this.registered = registered;
            this.latitude = latitude;
            this.longitude = longitude;
            this.greeting = greeting;
            this.favoriteFruit = favoriteFruit;
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
        public String getGreeting() { return greeting; }
        public String getFavoriteFruit() { return favoriteFruit; }
    }

    public static final class Stage08 {
        private final List<Entry08> users;
        public Stage08(List<Entry08> users) { this.users = users; }
        public List<Entry08> getUsers() { return users; }
    }

    public static final class Entry08 {
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
        public Entry08(String id, int index, String guid, boolean isActive, String balance, String picture, int age, String eyeColor, String name, String gender, String company, String email, String phone, String address, String about, String registered, double latitude, double longitude, List<String> tags, String greeting, String favoriteFruit) {
            this.id = id;
            this.index = index;
            this.guid = guid;
            this.isActive = isActive;
            this.balance = balance;
            this.picture = picture;
            this.age = age;
            this.eyeColor = eyeColor;
            this.name = name;
            this.gender = gender;
            this.company = company;
            this.email = email;
            this.phone = phone;
            this.address = address;
            this.about = about;
            this.registered = registered;
            this.latitude = latitude;
            this.longitude = longitude;
            this.tags = tags;
            this.greeting = greeting;
            this.favoriteFruit = favoriteFruit;
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

    public static final class Stage09 {
        private final List<Entry09> users;
        public Stage09(List<Entry09> users) { this.users = users; }
        public List<Entry09> getUsers() { return users; }
    }

    public static final class Entry09 {
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
        public Entry09(String id, int index, String guid, boolean isActive, String balance, String picture, int age, String eyeColor, String name, String gender, String company, String email, String phone, String address, String about, String registered, double latitude, double longitude, List<String> tags, List<Users.Friend> friends, String greeting, String favoriteFruit) {
            this.id = id;
            this.index = index;
            this.guid = guid;
            this.isActive = isActive;
            this.balance = balance;
            this.picture = picture;
            this.age = age;
            this.eyeColor = eyeColor;
            this.name = name;
            this.gender = gender;
            this.company = company;
            this.email = email;
            this.phone = phone;
            this.address = address;
            this.about = about;
            this.registered = registered;
            this.latitude = latitude;
            this.longitude = longitude;
            this.tags = tags;
            this.friends = friends;
            this.greeting = greeting;
            this.favoriteFruit = favoriteFruit;
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

}
