package org.sjf4j.compiled;

import org.sjf4j.exception.JsonException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class CompiledNodes {

    private static final String IMPL_SUFFIX = "_Impl";
    private static final String COMPILED_ANNOTATIONS =
            "@CompiledPath, @CompiledMapper, or @CompiledSchemaValidator";

    private static final ClassValue<Object> NODES_CACHE = new ClassValue<Object>() {
        @Override
        protected Object computeValue(Class<?> type) {
            return create(type);
        }
    };

    private CompiledNodes() {}

    @SuppressWarnings("unchecked")
    public static <T> T of(Class<T> type) {
        if (type == null) {
            throw new JsonException("CompiledNodes.of requires a non-null interface type");
        }
        return (T) NODES_CACHE.get(type);
    }

    private static Object create(Class<?> type) {
        if (!type.isInterface()) {
            throw new JsonException("CompiledNodes.of requires an interface type generated from "
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
            Field instance = implClass.getField("INSTANCE");
            int modifiers = instance.getModifiers();
            if (!Modifier.isStatic(modifiers)) {
                throw new JsonException("Generated SJF4J implementation " + implName
                        + " must expose public static INSTANCE");
            }
            Object value = instance.get(null);
            if (value == null) {
                throw new JsonException("Generated SJF4J implementation " + implName
                        + " has null INSTANCE");
            }
            return type.cast(value);
        } catch (NoSuchFieldException e) {
            throw new JsonException("Generated SJF4J implementation " + implName
                    + " must expose public static INSTANCE", e);
        } catch (IllegalAccessException e) {
            throw new JsonException("Cannot access generated SJF4J implementation " + implName
                    + " INSTANCE for interface " + type.getName(), e);
        }
    }

}
