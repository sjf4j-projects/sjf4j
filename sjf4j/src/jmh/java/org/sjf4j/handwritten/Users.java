package org.sjf4j.handwritten;

import lombok.Data;

import java.util.List;

@Data
public class Users {
    private List<User> users;
    private int total;
    private int page;
    private long generatedAt;
}
