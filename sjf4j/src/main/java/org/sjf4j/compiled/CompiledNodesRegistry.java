package org.sjf4j.compiled;

import org.sjf4j.annotation.compiled.CompiledNodes;
import org.sjf4j.exception.JsonException;

import java.lang.reflect.InvocationTargetException;

public class CompiledNodesRegistry {

    private static final ClassValue<Object> NODES_CACHE = new ClassValue<Object>() {
        @Override
        protected Object computeValue(Class<?> type) {
            return create(type);
        }
    };


    @SuppressWarnings("unchecked")
    public static <T> T of(Class<T> type) {
        return (T) NODES_CACHE.get(type);
    }

    private static Object create(Class<?> type) {
        String implName = type.getName() + "_Impl";
        Class<?> implClass;
        try {
            implClass = Class.forName(implName, true, type.getClassLoader());
        } catch (Exception e) {
            if (!type.isInterface()) {
                throw new JsonException("@CompiledNodes target must be an interface: " + type.getName());
            }
            throw new JsonException("Cannot load generated @CompiledNodes implementation " + implName
                    + " for interface " + type.getName()
                    + "; ensure annotation processing is enabled and generated sources are compiled", e);
        }

        try {
            return implClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new JsonException("Cannot load generated @CompiledNodes implementation " + implName
                    + " for interface " + type.getName(), e);
        }
    }

}
