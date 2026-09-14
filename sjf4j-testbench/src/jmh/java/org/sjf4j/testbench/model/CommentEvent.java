package org.sjf4j.testbench.model;

import lombok.Data;

@Data
public class CommentEvent implements UserEvent {
    private String type;
    private long userId;
    private long occurredAt;
    private long commentId;
    private String body;
    private long replyTo;
}
