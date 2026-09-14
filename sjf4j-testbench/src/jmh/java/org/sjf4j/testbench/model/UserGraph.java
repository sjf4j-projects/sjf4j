package org.sjf4j.testbench.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UserGraph {
    private User owner;
    private Users users;
    private List<UserEvent> events;
    private Map<String, StringValue> labels;
    private StringValue primaryLabel;
}
