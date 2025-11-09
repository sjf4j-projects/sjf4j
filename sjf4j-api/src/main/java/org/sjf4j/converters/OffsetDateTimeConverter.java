package org.sjf4j.converters;

import org.sjf4j.ObjectConverter;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;

public class OffsetDateTimeConverter implements ObjectConverter<OffsetDateTime, String> {


    @Override
    public Class<OffsetDateTime> getObjectType() {
        return OffsetDateTime.class;
    }

    @Override
    public Class<String> getNodeType() {
        return String.class;
    }

    @Override
    public String object2Node(OffsetDateTime object) {
        return object.toString();
    }

    @Override
    public OffsetDateTime node2Object(String  value) {
        return OffsetDateTime.parse(value);
    }

}
