package org.sjf4j;

public interface ValueConverter {

    Class<?> getObjectClass();

    Class<?> getValueClass();

    Object object2Value(Object object);

    Object value2Object(Object value);

}
