package org.sjf4j.annotation;

import org.sjf4j.JsonObject;


@Jojo({
        @Prop(name = "name", type = String.class),
        @Prop(name = "age", type = int.class, defaultValue = "0", comment = "Age > 0", deprecated = true),
        @Prop(name = "sex", type = boolean.class, defaultValue = "true", setter = false),
        @Prop(name = "friends", type = Pojo.class)
})
public class Pojo extends JsonObject {

}

