package org.sjf4j.testbench.model;

import org.sjf4j.JsonObject;

import java.util.List;

/** Dynamic-node counterpart of {@link User}. */
public class UserJojo extends JsonObject {
    public String name;
    public long id;
    public long createdAt;
    public long updatedAt;
    public long reputation;
    public int loginCount;
    public int age;
    public boolean active;
    public boolean verified;
    public boolean admin;
    public boolean suspended;
    public double score;
    public double latitude;
    public double longitude;
    public String username;
    public String email;
    public String displayName;
    public String passwordHash;
    public String bio;
    public String website;
    public String department;
    public Address address;
    public List<String> tags;
    public List<UserJojo> friends;
}
