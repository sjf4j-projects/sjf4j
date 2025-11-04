package org.sjf4j;

import java.lang.reflect.Type;

public interface ObjectConverter {

    Type getObjectType();

    Type getValueType();

    Object object2Value(Object object);

    Object value2Object(Object value);

}
