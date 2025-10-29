package org.sjf4j.converters;

import org.sjf4j.ValueConverter;

import java.time.OffsetDateTime;

public class OffsetDateTimeConverter implements ValueConverter {


    @Override
    public Class<OffsetDateTime> getObjectClass() {
        return OffsetDateTime.class;
    }

    @Override
    public Class<String> getValueClass() {
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
