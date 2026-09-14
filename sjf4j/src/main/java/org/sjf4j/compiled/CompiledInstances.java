package org.sjf4j.compiled;

import org.sjf4j.exception.JsonException;

import java.lang.reflect.InvocationTargetException;

public final class CompiledInstances {

    private static final String IMPL_SUFFIX = "_Impl";
    private static final String COMPILED_ANNOTATIONS =
            "@CompiledNavigator or @CompiledMapper (or @CompiledJdbcMapper)";

    private static final ClassValue<Object> INSTANCES_CACHE = new ClassValue<Object>() {
        @Override
        protected Object computeValue(Class<?> type) {
            return _create(type);
        }
    };

    private CompiledInstances() {}

    @SuppressWarnings("unchecked")
    public static <T> T of(Class<T> type) {
        if (type == null) {
            throw new JsonException("CompiledInstances.of requires a non-null interface type");
        }
        return (T) INSTANCES_CACHE.get(type);
    }

    private static Object _create(Class<?> type) {
        if (!type.isInterface()) {
            throw new JsonException("CompiledInstances.of requires an interface type generated from "
                    + COMPILED_ANNOTATIONS + ", but got " + type.getName());
        }

        String implName = type.getName() + IMPL_SUFFIX;
        Class<?> implClass;
        try {
            implClass = Class.forName(implName, true, type.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new JsonException("Cannot find generated SJF4J implementation " + implName
                    + " for interface " + type.getName()
                    + "; ensure the interface is annotated with " + COMPILED_ANNOTATIONS
                    + ", annotation processing is enabled, and generated sources are compiled", e);
        } catch (LinkageError e) {
            throw new JsonException("Generated SJF4J implementation " + implName
                    + " for interface " + type.getName() + " failed to load", e);
        }

        if (!type.isAssignableFrom(implClass)) {
            throw new JsonException("Generated SJF4J implementation " + implName
                    + " does not implement " + type.getName());
        }

        try {
            return type.cast(implClass.getConstructor().newInstance());
        } catch (NoSuchMethodException e) {
            throw new JsonException("Generated SJF4J implementation " + implName
                    + " must expose a public no-arg constructor", e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new JsonException("Cannot instantiate generated SJF4J implementation " + implName
                    + " for interface " + type.getName(), e);
        } catch (InvocationTargetException e) {
            throw new JsonException("Generated SJF4J implementation " + implName
                    + " constructor failed for interface " + type.getName(), e.getCause());
        }
    }

}
