package org.sjf4j.testbench.handwritten;

import lombok.Data;
import org.sjf4j.testbench.handwritten.User;

import java.util.List;

@Data
public class Users {
    private List<User> users;
    private int total;
    private int page;
    private long generatedAt;
}
