package org.sjf4j.testbench.model;

import lombok.Data;

@Data
public class LoginEvent implements UserEvent {
    private String type;
    private long userId;
    private long occurredAt;
    private String ipAddress;
    private String device;
}
