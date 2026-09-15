package org.sjf4j;

import org.sjf4j.exception.JsonException;

import java.lang.reflect.InvocationTargetException;

/**
 * Creates and caches runtime instances of annotation-processor generated interfaces.
 *
 * <p>For an interface annotated with {@code @CompiledNavigator}, {@code @CompiledMapper}, or
 * {@code @CompiledJdbcMapper}, the processor generates an implementation named by appending
 * {@code _Impl} to the interface's binary name. {@link #of(Class)} loads that implementation with
 * the interface's class loader and invokes its public no-argument constructor.
 *
 * <p>One instance is cached per interface class. The generated implementation must therefore be
 * stateless or safe to share between callers.
 */
public final class CompiledInstances {

    private static final String IMPL_SUFFIX = "_Impl";

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
            throw new JsonException("CompiledInstances.of requires an interface type, but got " +
                    type.getName());
        }

        String implName = type.getName() + IMPL_SUFFIX;
        Class<?> implClass;
        try {
            implClass = Class.forName(implName, true, type.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new JsonException("Cannot find generated SJF4J implementation " + implName
                    + " for interface " + type.getName()
                    + "; ensure the interface is annotated with @CompiledXxx"
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
