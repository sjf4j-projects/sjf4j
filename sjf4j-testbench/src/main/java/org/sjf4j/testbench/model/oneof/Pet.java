package org.sjf4j.testbench.model.oneof;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfCase;

@OneOf(discriminatorKey = "kind", cases = {
        @OneOfCase(type = Dog.class, when = "dog"),
        @OneOfCase(type = Cat.class, when = "cat")
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY,
        property = "kind", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Dog.class, name = "dog"),
        @JsonSubTypes.Type(value = Cat.class, name = "cat")
})
public class Pet {
    public String kind;
    public String name;
}