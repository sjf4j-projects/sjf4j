package org.sjf4j.converters;

import org.sjf4j.ObjectConverter;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;

public class OffsetDateTimeConverter implements ObjectConverter {


    @Override
    public Type getObjectType() {
        return OffsetDateTime.class;
    }

    @Override
    public Type getValueType() {
        return String.class;
    }

    @Override
    public Object object2Value(Object object) {
        return object.toString();
    }

    @Override
    public Object value2Object(Object value) {
        return OffsetDateTime.parse((String) value);
    }

}
