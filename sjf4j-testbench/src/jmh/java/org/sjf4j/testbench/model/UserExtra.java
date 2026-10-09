package org.sjf4j.testbench.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Extra-map counterpart of {@link UserJojo}. */
public class UserExtra {
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
    public List<UserExtra> friends;

    @JsonAnyGetter
    @JsonAnySetter
    @JSONField(unwrapped = true)
    public Map<String, Object> extra = new LinkedHashMap<>();
}
