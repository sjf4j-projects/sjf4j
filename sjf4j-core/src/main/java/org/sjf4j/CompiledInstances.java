package org.sjf4j;

import org.sjf4j.binding.Binder;
import org.sjf4j.exception.NodeException;
import org.sjf4j.util.Asserts;

import java.lang.reflect.Constructor;

/**
 * Factory for annotation-processor generated implementations of
 * {@code @CompiledNavigator}, {@code @CompiledMapper},
 * {@code @CompiledJdbcMapper}, {@code @CompiledBinder}, and
 * {@code @CompiledValidator} interfaces.
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
            Constructor<?> constructor;
            try {
                constructor = implClass.getConstructor(parameterType);
            } catch (NoSuchMethodException e) {
                if (!(argument instanceof Binder)) {
                    throw new IllegalArgumentException("no constructor accepting '" + parameterType.getName()
                            + "' in generated implementation '" + implClass.getName() + "'", e);
                }
                constructor = _compatibleBinderConstructor(implClass, parameterType);
            }
            return type.cast(constructor.newInstance(argument));
        } catch (ReflectiveOperationException e) {
            throw new NodeException("failed to instantiate generated implementation '"
                    + implClass.getName() + "' for interface '" + type.getName() + "'", e);
        }
    }

    /** Matches an injected Binder against the declared backend constructor, not its exact runtime class. */
    private static Constructor<?> _compatibleBinderConstructor(Class<?> implClass, Class<?> actualType) {
        Constructor<?> selected = null;
        String expected = null;
        for (Constructor<?> candidate : implClass.getConstructors()) {
            Class<?>[] parameters = candidate.getParameterTypes();
            if (parameters.length != 1 || !Binder.class.isAssignableFrom(parameters[0])) continue;
            Class<?> acceptedType = parameters[0];
            expected = acceptedType.getName();
            if (!acceptedType.isAssignableFrom(actualType)) continue;
            if (selected != null) {
                Class<?> previousType = selected.getParameterTypes()[0];
                if (previousType.isAssignableFrom(acceptedType)) {
                    selected = candidate;
                } else if (!acceptedType.isAssignableFrom(previousType)) {
                    throw new IllegalArgumentException("ambiguous backend Binder constructors in '"
                            + implClass.getName() + "' for '" + actualType.getName() + "'");
                }
            } else {
                selected = candidate;
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("backend Binder '" + actualType.getName()
                    + "' is incompatible with generated implementation '" + implClass.getName()
                    + "'" + (expected == null ? " (no backend Binder constructor)" :
                    " (expected '" + expected + "')"));
        }
        return selected;
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
