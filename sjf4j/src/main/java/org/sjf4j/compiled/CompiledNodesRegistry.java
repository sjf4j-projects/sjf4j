package org.sjf4j.compiled;

import org.sjf4j.exception.JsonException;

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
        try {
            Class<?> implClass = Class.forName(implName, true, type.getClassLoader());
            return implClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new JsonException("Cannot load compiled nodes implementation: " + implName, e);
        }
    }

}
