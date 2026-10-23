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
 * {@link #create(Class, RuntimeContext)} creates independent instances for all four
 * compiled interface families. The context configures Mapper, Binder, and JDBC
 * conversions; Navigator currently has no context-dependent operations.
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
     * Supported interfaces: CompiledNavigator, CompiledMapper, CompiledJdbcMapper,
     * and CompiledBinder. Navigator currently does not use the supplied context.
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

    /**
     * Creates a CompiledBinder using an already configured concrete SJF4J Binder.
     * Backend-specific parser and generator configuration belongs to this Binder;
     * the generated class does not alter the caller's native configuration.
     *
     * <p>The generic argument B is inferred, while matching the compiled
     * interface's exact backend is verified during instance creation.
     */
    public static <T, B> T create(Class<T> type, B backend) {
        Asserts.notNull(type, "type");
        Asserts.notNull(backend, "backend");
        if (!(backend instanceof org.sjf4j.binding.Binder)) {
            throw new IllegalArgumentException("CompiledBinder backend must be an SJF4J Binder: "
                    + backend.getClass().getName());
        }
        Class<?> implClass = _implementationClass(type);
        java.lang.reflect.Constructor<?> match = null;
        for (java.lang.reflect.Constructor<?> constructor : implClass.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length != 1 || parameters[0] == RuntimeContext.class
                    || !parameters[0].isInstance(backend)) continue;
            if (match != null) {
                throw new IllegalArgumentException("Ambiguous backend constructors for "
                        + implClass.getName() + " accepting " + backend.getClass().getName());
            }
            match = constructor;
        }
        if (match == null) {
            throw new IllegalArgumentException("No compatible backend constructor for "
                    + implClass.getName() + " accepting " + backend.getClass().getName());
        }
        try {
            return type.cast(match.newInstance(backend));
        } catch (InstantiationException | IllegalAccessException e) {
            throw new NodeException("Cannot instantiate " + implClass.getName(), e);
        } catch (InvocationTargetException e) {
            throw new NodeException("Generated binder constructor failed for "
                    + implClass.getName(), e.getCause());
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
            throw new IllegalArgumentException("CompiledInstances requires an interface type, but got " +
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
