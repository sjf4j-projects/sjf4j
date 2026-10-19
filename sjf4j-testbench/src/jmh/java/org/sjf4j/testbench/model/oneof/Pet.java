package org.sjf4j.testbench.model.oneof;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.testbench.binding.compiled.OneOfReadBenchmark;

@OneOf(key = "kind", value = {
        @OneOf.Mapping(value = OneOfReadBenchmark.Dog.class, when = "dog"),
        @OneOf.Mapping(value = OneOfReadBenchmark.Cat.class, when = "cat")
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY,
        property = "kind", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = OneOfReadBenchmark.Dog.class, name = "dog"),
        @JsonSubTypes.Type(value = OneOfReadBenchmark.Cat.class, name = "cat")
})
public class Pet {
    public String kind;
    public String name;
}