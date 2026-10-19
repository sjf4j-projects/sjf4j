package org.sjf4j.testbench.model;

import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;

@OneOf(key = "type", mappings = {
        @OneOfMapping(type = LoginEvent.class, when = "login"),
        @OneOfMapping(type = CommentEvent.class, when = "comment")
})
public interface UserEvent {
}
