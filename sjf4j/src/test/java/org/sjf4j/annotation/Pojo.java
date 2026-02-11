package org.sjf4j.annotation;

import org.sjf4j.JsonObject;
import org.sjf4j.annotation.jojo.Jojo;
import org.sjf4j.annotation.jojo.Property;


@Jojo({
        @Property(name = "name", type = String.class),
        @Property(name = "age", type = int.class),
})
public class Pojo extends JsonObject {

    public void method1() {

    }

}

