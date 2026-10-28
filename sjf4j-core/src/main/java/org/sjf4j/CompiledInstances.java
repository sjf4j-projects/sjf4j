package org.sjf4j;

import org.sjf4j.exception.NodeException;
import org.sjf4j.util.Asserts;

import java.lang.reflect.InvocationTargetException;

/**
 * Factory for annotation-processor generated implementations of
 * {@code @CompiledNavigator}, {@code @CompiledMapper},
 * {@code @CompiledJdbcMapper}, and {@code @CompiledBinder} interfaces.
 *
 * <p>Each {@link #create(Class)} invocation constructs a new instance of
 * the generated {@code _Impl} class. The factory does not cache instances
 * or manage their lifecycle; applications may retain or inject instances
 * according to their own requirements.
 */
public final class CompiledInstances {

    private static final String IMPL_SUFFIX = "_Impl";

    private CompiledInstances() {}

    /**
     * Creates a new compiled implementation with its default settings.
     * The returned instance is not cached.
     */
    public static <T> T create(Class<T> type) {
        Asserts.notNull(type, "type");
        return _create(type);
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

    private static <T> T _create(Class<T> type) {
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
