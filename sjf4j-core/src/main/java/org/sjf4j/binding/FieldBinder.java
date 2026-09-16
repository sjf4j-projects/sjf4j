package org.sjf4j.binding;


import java.io.IOException;
import java.lang.reflect.Type;

@FunctionalInterface
public interface FieldBinder {

    Object read(StreamingReader reader, Type ownerType, Class<?> ownerRawClazz, StreamingContext context)
            throws IOException;


    @FunctionalInterface
    interface ObjBooleanConsumer<T> {
        void accept(T obj, boolean value);
    }

    @FunctionalInterface
    interface ObjByteConsumer<T> {
        void accept(T obj, byte value);
    }

    @FunctionalInterface
    interface ObjShortConsumer<T> {
        void accept(T obj, short value);
    }

    @FunctionalInterface
    interface ObjFloatConsumer<T> {
        void accept(T obj, float value);
    }

    @FunctionalInterface
    interface ObjCharConsumer<T> {
        void accept(T obj, char value);
    }
}