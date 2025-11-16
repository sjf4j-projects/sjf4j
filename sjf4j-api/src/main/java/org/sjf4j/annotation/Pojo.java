package org.sjf4j.annotation;


import org.sjf4j.JsonObject;

@Jojo({
        @Field(name = "name", type = String.class),
        @Field(name = "age", type = int.class, defaultValue = "0", comment = "Age > 0", deprecated = true),
        @Field(name = "sex", type = boolean.class, defaultValue = "true", setter = false),
        @Field(name = "friends", type = Pojo.class)
})
public class Pojo extends JsonObject {

}
