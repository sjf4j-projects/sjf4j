package org.sjf4j.annotation;


import org.sjf4j.JsonObject;

import java.util.List;

@Jo({
        @Property(name = "name", type = String.class),
        @Property(name = "age", type = int.class, defaultValue = "0", comment = "Age > 0", deprecated = true),
        @Property(name = "sex", type = boolean.class, defaultValue = "true", setter = false),
        @Property(name = "friends", type = Pojo.class)
})
public class Pojo extends JsonObject {

}
