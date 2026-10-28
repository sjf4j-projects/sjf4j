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
            throw new IllegalArgumentException("expected SJF4J Binder, but was '"
                    + backendBinder.getClass().getName() + "'");
        }
        return _instantiate(type, backendBinder.getClass(), backendBinder);
    }

    private static <T> T _instantiate(Class<T> type, Class<?> parameterType, Object argument) {
        Asserts.notNull(type, "type");
        Class<?> implClass = _implementationClass(type);
        try {
            return type.cast(implClass.getConstructor(parameterType).newInstance(argument));
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("no constructor accepting '" + parameterType.getName()
                    + "' in generated implementation '" + implClass.getName() + "'", e);
        } catch (ReflectiveOperationException e) {
            throw new NodeException("failed to instantiate generated implementation '"
                    + implClass.getName() + "' for interface '" + type.getName() + "'", e);
        }
    }

    private static Class<?> _implementationClass(Class<?> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("expected interface type, but was '" + type.getName() + "'");
        }

        String implName = type.getName() + IMPL_SUFFIX;
        Class<?> implClass;
        try {
            implClass = Class.forName(implName, true, type.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new NodeException("cannot find generated implementation '" + implName
                    + "' for interface '" + type.getName()
                    + "'; ensure a supported @Compiled annotation is present and annotation processing is enabled", e);
        } catch (LinkageError e) {
            throw new NodeException("failed to load generated implementation '" + implName
                    + "' for interface '" + type.getName() + "'", e);
        }

        if (!type.isAssignableFrom(implClass)) {
            throw new NodeException("generated implementation '" + implName
                    + "' does not implement '" + type.getName() + "'");
        }

        return implClass;
    }

}
