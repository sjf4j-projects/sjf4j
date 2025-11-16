package org.sjf4j.converters;

import org.sjf4j.NodeConverter;

import java.time.OffsetDateTime;

public class OffsetDateTimeConverter implements NodeConverter<OffsetDateTime, String> {


    @Override
    public Class<OffsetDateTime> getWrapType() {
        return OffsetDateTime.class;
    }

    @Override
    public Class<String> getPureType() {
        return String.class;
    }

    @Override
    public String wrap2Pure(OffsetDateTime object) {
        return object.toString();
    }

    @Override
    public OffsetDateTime pure2Wrap(String  value) {
        return OffsetDateTime.parse(value);
    }

}
