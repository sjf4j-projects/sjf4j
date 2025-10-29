package org.sjf4j.util;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.ValueConverter;
import org.sjf4j.ValueRegistry;

import java.util.List;
import java.util.Map;

public class ValueHandler {


    public static Object object2Value(Object object) {
        if (object == null) {
            return null;
        } else {
            ValueConverter converter = ValueRegistry.getConverter(object.getClass());
            if (converter != null) {
                return converter.object2Value(object);
            }
            return object;
        }
    }

}
