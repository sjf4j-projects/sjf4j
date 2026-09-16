package org.sjf4j.binding;


import java.io.IOException;
import java.lang.reflect.Type;

@FunctionalInterface
public interface FieldReader {

    Object read(StreamingReader reader, Type ownerType, Class<?> ownerRawClazz, StreamingContext context)
            throws IOException;

}