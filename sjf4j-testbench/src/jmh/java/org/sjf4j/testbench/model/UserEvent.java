package org.sjf4j.testbench.model;

import org.sjf4j.annotation.node.OneOf;

@OneOf(key = "type", value = {
        @OneOf.Mapping(value = LoginEvent.class, when = "login"),
        @OneOf.Mapping(value = CommentEvent.class, when = "comment")
})
public interface UserEvent {
}
