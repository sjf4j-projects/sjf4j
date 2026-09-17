package org.sjf4j.node;

import org.sjf4j.exception.BindingException;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Compiles method-handle accessors into functional interfaces.
 */
public final class PojoAccess {

    private PojoAccess() {}

    static final boolean IS_JDK8 = System.getProperty("java.version").startsWith("1.");

    private static final MethodHandles.Lookup ROOT_LOOKUP = MethodHandles.lookup();
    private static final Method PRIVATE_LOOKUP_IN;
    static {
        Method privateLookupIn = null;
        try {
            privateLookupIn = MethodHandles.class.getMethod("privateLookupIn", Class.class,
                    MethodHandles.Lookup.class);
        } catch (Exception ignored) {}
        PRIVATE_LOOKUP_IN = privateLookupIn;
    }

    public static MethodHandles.Lookup resolveLookup(Class<?> clazz) {
        MethodHandles.Lookup lookup = ROOT_LOOKUP;
        if (!IS_JDK8 && PRIVATE_LOOKUP_IN != null) {
            try {
                lookup = (MethodHandles.Lookup) PRIVATE_LOOKUP_IN.invoke(null, clazz, ROOT_LOOKUP);
            } catch (Exception e) {
                // log.debug("Failed to get 'privateLookupIn'", e);
            }
        }
        return lookup;
    }

    @SuppressWarnings("unchecked")
    public static <T> Supplier<T> createConstructorLambda(MethodHandles.Lookup lookup,
                                                          Class<T> clazz,
                                                          MethodHandle constructor) {
        if (constructor == null) return null;
        try {
            return (Supplier<T>) LambdaMetafactory.metafactory(
                    lookup,
                    "get",
                    MethodType.methodType(Supplier.class),
                    MethodType.methodType(Object.class), // erased SAM (Object)get()
                    constructor,
                    constructor.type().changeReturnType(clazz)
            ).getTarget().invoke();
        } catch (Throwable e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T createArgsCreatorLambda(MethodHandles.Lookup lookup, MethodHandle creator,
                                                Class<T> funcType, int arity) {
        if (creator == null || funcType == null || arity <= 0 || arity > 5) {
            return null;
        }
        Class<?>[] params = new Class<?>[arity];
        Arrays.fill(params, Object.class);
        MethodType erasedSamType = MethodType.methodType(Object.class, params);
        try {
            MethodType instantiatedSamType = creator.type().changeReturnType(Object.class);
            return (T) LambdaMetafactory.metafactory(
                    lookup,
                    "apply",
                    MethodType.methodType(funcType),
                    erasedSamType,
                    creator,
                    instantiatedSamType
            ).getTarget().invoke();
        } catch (Throwable e) {
            try {
                MethodHandle adapted = creator.asType(erasedSamType);
                return MethodHandleProxies.asInterfaceInstance(funcType, adapted);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T createGetterLambda(MethodHandles.Lookup lookup, MethodHandle getter,
                                           Class<T> functionType, Class<?> returnType) {
        if (getter == null) return null;
        try {
            MethodType invokedType = MethodType.methodType(functionType);
            MethodType samMethodType = MethodType.methodType(returnType, Object.class);

            return (T) LambdaMetafactory.metafactory(
                    lookup,
                    "apply",
                    invokedType,
                    samMethodType,
                    getter,
                    getter.type()
            ).getTarget().invoke();
        } catch (Throwable e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T createSetterLambda(MethodHandles.Lookup lookup, MethodHandle setter,
                                           Class<T> functionType, Class<?> valueType) {
        if (setter == null || setter.type().parameterCount() < 2) return null;
        try {
            MethodType invokedType = MethodType.methodType(functionType);
            MethodType samMethodType = MethodType.methodType(void.class, Object.class, valueType);

            return (T) LambdaMetafactory.metafactory(
                    lookup,
                    "accept",
                    invokedType,
                    samMethodType,
                    setter,
                    setter.type()
            ).getTarget().invoke();
        } catch (Throwable e) {
            return null;
        }
    }


    public static Object invokeGetter(String name, MethodHandle getterHandle, Function<Object, Object> getterLambda,
                                      Object receiver) {
        Objects.requireNonNull(receiver, "receiver");
        try {
            if (getterLambda != null) {
                return getterLambda.apply(receiver);
            }
            if (getterHandle != null) {
                return getterHandle.invoke(receiver);
            }
        } catch (Throwable e) {
            throw new BindingException("failed to invoke getter for property '" + name + "' (node type: " +
                    Types.name(receiver) + ")", e);
        }
        throw new BindingException("no getter available for property '" + name + "' of " + Types.name(receiver));
    }


    public static void invokeSetter(String name, MethodHandle setterHandle, BiConsumer<Object, Object> setterLambda,
                                    Object receiver, Object value) {
        Objects.requireNonNull(receiver, "receiver");
        try {
            if (setterLambda != null) {
                setterLambda.accept(receiver, value);
                return;
            }
            if (setterHandle != null) {
                setterHandle.invoke(receiver, value);
                return;
            }
        } catch (Throwable e) {
            throw new BindingException("failed to invoke setter for property '" + name + "' of type '" +
                    Types.name(value) + "' (node type: " + Types.name(receiver) + ")", e);
        }
        throw new BindingException("no setter available for property '" + name + "' of " + Types.name(receiver));
    }


}
