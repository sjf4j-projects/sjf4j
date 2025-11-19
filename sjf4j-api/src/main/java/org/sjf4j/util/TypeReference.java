package org.sjf4j.util;

import lombok.Getter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

@Getter
public abstract class TypeReference<T> {
    private final Type type;

    protected TypeReference() {
        Type superclass = getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType) {
            this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
        } else {
            throw new IllegalArgumentException("Missing type parameter.");
        }
    }

    @Override
    public String toString() {
        return type.toString();
    }

}