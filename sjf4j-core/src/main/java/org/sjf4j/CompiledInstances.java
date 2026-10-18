package org.sjf4j;

import org.sjf4j.exception.NodeException;
import org.sjf4j.util.Asserts;

import java.lang.reflect.InvocationTargetException;

/**
 * Creates and caches runtime instances of annotation-processor generated interfaces.
 *
 * <p>For an interface annotated with {@code @CompiledNavigator}, {@code @CompiledMapper},
 * {@code @CompiledJdbcMapper}, or {@code @CompiledBinder}, the processor
 * generates an implementation named by appending {@code _Impl} to the interface's
 * binary name. {@link #global(Class)} loads that implementation with
 * the interface's class loader and invokes its public no-argument constructor.
 *
 * <p>{@link #global(Class)} caches one immutable-default-context instance per interface.
 * {@link #create(Class, RuntimeContext)} creates independent, configured Mapper/Binder instances.
 * Generated implementations must be safe to share across callers once constructed.
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

    /**
     * Returns the cached instance configured with {@link RuntimeContext#EMPTY}.
     */
    @SuppressWarnings("unchecked")
    public static <T> T global(Class<T> type) {
        Asserts.notNull(type, "type");
        return (T) INSTANCES_CACHE.get(type);
    }

    /**
     * Creates an independent compiled implementation with the supplied runtime settings.
     * The generated implementation must expose a public RuntimeContext constructor.
     */
    public static <T> T create(Class<T> type, RuntimeContext context) {
        Asserts.notNull(type, "type");
        Asserts.notNull(context, "context");
        Class<?> implClass = _implementationClass(type);
        try {
            return type.cast(implClass.getConstructor(RuntimeContext.class).newInstance(context));
        } catch (NoSuchMethodException e) {
            throw new NodeException("Generated SJF4J implementation " + implClass.getName()
                    + " must expose a public RuntimeContext constructor; recompile using the current processor", e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new NodeException("Cannot instantiate generated SJF4J implementation " + implClass.getName()
                    + " for interface " + type.getName(), e);
        } catch (InvocationTargetException e) {
            throw new NodeException("Generated SJF4J implementation " + implClass.getName()
                    + " constructor failed for interface " + type.getName(), e.getCause());
        }
    }

    /** Compatibility alias for {@link #global(Class)}. */
    @Deprecated
    public static <T> T of(Class<T> type) {
        return global(type);
    }

    private static Object _create(Class<?> type) {
        Class<?> implClass = _implementationClass(type);
        try {
            return type.cast(implClass.getConstructor().newInstance());
        } catch (NoSuchMethodException e) {
            throw new NodeException("Generated SJF4J implementation " + implClass.getName()
                    + " must expose a public no-arg constructor", e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new NodeException("Cannot instantiate generated SJF4J implementation " + implClass.getName()
                    + " for interface " + type.getName(), e);
        } catch (InvocationTargetException e) {
            throw new NodeException("Generated SJF4J implementation " + implClass.getName()
                    + " constructor failed for interface " + type.getName(), e.getCause());
        }
    }

    private static Class<?> _implementationClass(Class<?> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("CompiledInstances.of requires an interface type, but got " +
                    type.getName());
        }

        String implName = type.getName() + IMPL_SUFFIX;
        Class<?> implClass;
        try {
            implClass = Class.forName(implName, true, type.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new NodeException("Cannot find generated SJF4J implementation " + implName
                    + " for interface " + type.getName()
                    + "; ensure the interface is annotated with @CompiledXxx"
                    + " (@CompiledNavigator, @CompiledMapper, @CompiledJdbcMapper, or @CompiledBinder)"
                    + ", annotation processing is enabled, and generated sources are compiled", e);
        } catch (LinkageError e) {
            throw new NodeException("Generated SJF4J implementation " + implName
                    + " for interface " + type.getName() + " failed to load", e);
        }

        if (!type.isAssignableFrom(implClass)) {
            throw new NodeException("Generated SJF4J implementation " + implName
                    + " does not implement " + type.getName());
        }

        return implClass;
    }

}
