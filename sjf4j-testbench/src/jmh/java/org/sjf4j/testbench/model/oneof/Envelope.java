package org.sjf4j.testbench.model.oneof;

import java.util.List;

public class Envelope {
    public long id;
    public long createdAt;
    public String username;
    public boolean verified;
    public Detail home;
    public List<String> roles;
    public Pet pet;
    public long lastLogin;
    public boolean active;
}