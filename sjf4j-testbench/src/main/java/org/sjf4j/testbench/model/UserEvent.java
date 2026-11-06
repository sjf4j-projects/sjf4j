package org.sjf4j.testbench.model;

import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfCase;

@OneOf(discriminatorKey = "type", cases = {
        @OneOfCase(type = LoginEvent.class, when = "login"),
        @OneOfCase(type = CommentEvent.class, when = "comment")
})
public interface UserEvent {
}
