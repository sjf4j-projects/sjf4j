package org.sjf4j.testbench.model.fabien;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Users {

    private List<User> users;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Users)) {
            return false;
        }

        Users that = (Users) o;

        return Objects.equals(users, that.users);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(users);
    }

    @Override
    public String toString() {
        return "Users{" + "users=" + users + '}';
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public static Users sample() {
        User first = new User();
        first.setId("user-001");
        first.setIndex(1);
        first.setGuid("b4c1-001");
        first.setIsActive(true);
        first.setBalance("$1,234.50");
        first.setPicture("https://example.test/avatar/1");
        first.setAge(29);
        first.setEyeColor("blue");
        first.setName("Zoë \"Z\" 李");
        first.setGender("female");
        first.setCompany("Acme & Co.");
        first.setEmail("zoe@example.test");
        first.setPhone("+1-555-0101");
        first.setAddress("42 Main St\nApt 2");
        first.setAbout("Hello\\world\tこんにちは");
        first.setRegistered("2024-02-03T10:20:30Z");
        first.setLatitude(37.7749);
        first.setLongitude(-122.4194);
        first.setTags(Arrays.asList("coffee", "雪", null));
        first.setFriends(Arrays.asList(
                Friend.create("friend-1", "Renée"),
                Friend.create("friend-2", "Sam \"S\"")));
        first.setGreeting("Hi, Zoë!\n");
        first.setFavoriteFruit(null);

        User second = new User();
        second.setId("user-002");
        second.setIndex(2);
        second.setIsActive(false);
        second.setName("Miyuki 美雪");
        second.setAge(34);
        second.setEmail(null);
        second.setTags(Arrays.asList("music", "hiking"));
        second.setFriends(Arrays.asList(Friend.create("friend-3", "Zoë")));
        second.setGreeting("Welcome 🌿");

        Users users = new Users();
        users.setUsers(Arrays.asList(first, second));
        return users;
    }

    public static final class User {

        private String id;
        private int index;
        private String guid;
        private boolean isActive;
        private String balance;
        private String picture;
        private int age;
        private String eyeColor;
        private String name;
        private String gender;
        private String company;
        private String email;
        private String phone;
        private String address;
        private String about;
        private String registered;
        private double latitude;
        private double longitude;
        private List<String> tags;
        private List<Friend> friends;
        private String greeting;
        private String favoriteFruit;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof User)) {
                return false;
            }

            User user = (User) o;

            return index == user.index &&
                    isActive == user.isActive &&
                    age == user.age &&
                    Math.abs(Double.doubleToLongBits(user.latitude) - Double.doubleToLongBits(latitude)) < 3 &&
                    Math.abs(Double.doubleToLongBits(user.longitude) - Double.doubleToLongBits(longitude)) < 3 &&
                    Objects.equals(id, user.id) &&
                    Objects.equals(guid, user.guid) &&
                    Objects.equals(balance, user.balance) &&
                    Objects.equals(picture, user.picture) &&
                    Objects.equals(eyeColor, user.eyeColor) &&
                    Objects.equals(name, user.name) &&
                    Objects.equals(gender, user.gender) &&
                    Objects.equals(company, user.company) &&
                    Objects.equals(email, user.email) &&
                    Objects.equals(phone, user.phone) &&
                    Objects.equals(address, user.address) &&
                    Objects.equals(about, user.about) &&
                    Objects.equals(registered, user.registered) &&
                    Objects.equals(tags, user.tags) &&
                    Objects.equals(friends, user.friends) &&
                    Objects.equals(greeting, user.greeting) &&
                    Objects.equals(favoriteFruit, user.favoriteFruit);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, index, guid, isActive, balance, picture, age, eyeColor, name, gender, company, email, phone, address, about, registered, tags, friends, greeting, favoriteFruit);
        }

        @Override
        public String toString() {
            return "JsonDataObj{" + "id=" + id + ", index=" + index + ", guid=" + guid + ", isActive=" + isActive + ", balance=" + balance + ", picture=" + picture + ", age=" + age + ", eyeColor=" + eyeColor + ", name=" + name + ", gender=" + gender + ", company=" + company + ", email=" + email + ", phone=" + phone + ", address=" + address + ", about=" + about + ", registered=" + registered + ", latitude=" + latitude + ", longitude=" + longitude + ", tags=" + tags + ", friends=" + friends + ", greeting=" + greeting + ", favoriteFruit=" + favoriteFruit + '}';
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getGuid() {
            return guid;
        }

        public void setGuid(String guid) {
            this.guid = guid;
        }

        public boolean getIsActive() {
            return isActive;
        }

        public void setIsActive(boolean isActive) {
            this.isActive = isActive;
        }

        public String getBalance() {
            return balance;
        }

        public void setBalance(String balance) {
            this.balance = balance;
        }

        public String getPicture() {
            return picture;
        }

        public void setPicture(String picture) {
            this.picture = picture;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public String getEyeColor() {
            return eyeColor;
        }

        public void setEyeColor(String eyeColor) {
            this.eyeColor = eyeColor;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        public String getCompany() {
            return company;
        }

        public void setCompany(String company) {
            this.company = company;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getAbout() {
            return about;
        }

        public void setAbout(String about) {
            this.about = about;
        }

        public String getRegistered() {
            return registered;
        }

        public void setRegistered(String registered) {
            this.registered = registered;
        }

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public List<Friend> getFriends() {
            return friends;
        }

        public void setFriends(List<Friend> friends) {
            this.friends = friends;
        }

        public String getGreeting() {
            return greeting;
        }

        public void setGreeting(String greeting) {
            this.greeting = greeting;
        }

        public String getFavoriteFruit() {
            return favoriteFruit;
        }

        public void setFavoriteFruit(String favoriteFruit) {
            this.favoriteFruit = favoriteFruit;
        }
    }



    public static final class Friend {

        private String id;
        private String name;
        public Friend() {
        }

        public static Friend create(String id, String name) {
            Friend friend = new Friend();
            friend.id = id;
            friend.name = name;
            return friend;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Friend)) {
                return false;
            }

            Friend friend = (Friend) o;

            return Objects.equals(id, friend.id) &&
                    Objects.equals(name, friend.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name);
        }

        @Override
        public String toString() {
            return "Friend{" + "id=" + id + ", name=" + name + '}';
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

}
