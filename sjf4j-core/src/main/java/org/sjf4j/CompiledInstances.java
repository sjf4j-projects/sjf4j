package org.sjf4j;

import org.sjf4j.binding.Binder;
import org.sjf4j.exception.NodeException;
import org.sjf4j.util.Asserts;

import java.lang.reflect.Constructor;

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
        return create(type, RuntimeContext.EMPTY);
    }

    /** Creates an independent compiled implementation with the supplied settings. */
    public static <T> T create(Class<T> type, RuntimeContext context) {
        return _instantiate(type, RuntimeContext.class, Asserts.notNull(context, "context"));
    }

    /**
     * Creates a CompiledBinder with an already configured backend Binder.
     * The Binder type must match the interface's compile-time selected backend.
     */
    public static <T, B> T create(Class<T> type, B backendBinder) {
        Asserts.notNull(backendBinder, "backendBinder");
        if (!(backendBinder instanceof Binder)) {
            throw new IllegalArgumentException("Backend must be an SJF4J Binder: "
                    + backendBinder.getClass().getName());
        }
        return _instantiate(type, backendBinder.getClass(), backendBinder);
    }

    private static <T> T _instantiate(Class<T> type, Class<?> parameterType, Object argument) {
        Asserts.notNull(type, "type");
        Class<?> implClass = _implementationClass(type);
        try {
            return type.cast(implClass.getConstructor(parameterType).newInstance(argument));
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("No constructor accepting " + parameterType.getName()
                    + " in generated implementation " + implClass.getName(), e);
        } catch (ReflectiveOperationException e) {
            throw new NodeException("Cannot instantiate generated SJF4J implementation "
                    + implClass.getName() + " for interface " + type.getName(), e);
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
