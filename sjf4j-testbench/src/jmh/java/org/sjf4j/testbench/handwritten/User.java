package org.sjf4j.testbench.handwritten;

import lombok.Data;

import java.util.List;

@Data
public class User {
    private long id;
    private long createdAt;
    private long updatedAt;
    private long reputation;
    private int loginCount;
    private int age;
    private boolean active;
    private boolean verified;
    private boolean admin;
    private boolean suspended;
    private double score;
    private double latitude;
    private double longitude;
    private String username;
    private String email;
    private String displayName;
    private String passwordHash;
    private String bio;
    private String website;
    private String department;
    private Address address;
    private List<String> tags;
    private List<Friend> friends;

}
