package org.sjf4j.node;

import org.sjf4j.exception.BindingException;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Cached construction metadata for an object type.
 *
 * <p>It supports no-argument construction and argument-based constructors or
 * factory methods, including their JSON property bindings.</p>
 */
public class CreatorInfo {
    public final Class<?> clazz;
    public final MethodHandle noArgsCtorHandle;
    public final Supplier<?> noArgsCtorLambda;
    public final Executable argsCreator;
    public final MethodHandle argsCreatorHandle;
    public final TypeRegistry.Func1 argsCreatorLambda1;
    public final TypeRegistry.Func2 argsCreatorLambda2;
    public final TypeRegistry.Func3 argsCreatorLambda3;
    public final TypeRegistry.Func4 argsCreatorLambda4;
    public final TypeRegistry.Func5 argsCreatorLambda5;
    public final String[] argNames;
    public final Type[] argTypes;
    public final String[] argCodecNames;
    public final NodeValueInfo[] argValueCodecs;
    public final Map<String, Integer> argIndexes;
    public final Map<String, String> aliasMap;
    public final boolean hasCodecNameBinding;

    /**
     * Creates creator metadata for an object type.
     */
    public CreatorInfo(Class<?> clazz, MethodHandle noArgsCtorHandle, Supplier<?> noArgsCtorLambda,
                       Executable argsCreator, MethodHandle argsCreatorHandle,
                       TypeRegistry.Func1 argsCreatorLambda1, TypeRegistry.Func2 argsCreatorLambda2,
                       TypeRegistry.Func3 argsCreatorLambda3, TypeRegistry.Func4 argsCreatorLambda4, TypeRegistry.Func5 argsCreatorLambda5,
                       String[] argNames, Type[] argTypes,
                       String[] argCodecNames, NodeValueInfo[] argValueCodecs,
                       Map<String, Integer> argIndexes,
                       Map<String, String> aliasMap) {
        this.clazz = clazz;
        this.noArgsCtorHandle = noArgsCtorHandle;
        this.noArgsCtorLambda = noArgsCtorLambda;
        this.argsCreator = argsCreator;
        this.argsCreatorHandle = argsCreatorHandle;
        this.argsCreatorLambda1 = argsCreatorLambda1;
        this.argsCreatorLambda2 = argsCreatorLambda2;
        this.argsCreatorLambda3 = argsCreatorLambda3;
        this.argsCreatorLambda4 = argsCreatorLambda4;
        this.argsCreatorLambda5 = argsCreatorLambda5;
        this.argNames = argNames;
        this.argTypes = argTypes;
        this.argCodecNames = argCodecNames;
        this.argValueCodecs = argValueCodecs;
        this.argIndexes = argIndexes;
        this.aliasMap = aliasMap;
        boolean hasCodecNameBinding = false;
        if (argValueCodecs != null) {
            for (NodeValueInfo vci : argValueCodecs) {
                if (vci != null) {
                    hasCodecNameBinding = true;
                    break;
                }
            }
        }
        this.hasCodecNameBinding = hasCodecNameBinding;
    }

    /**
     * Returns the creator argument index for a JSON property name, or {@code -1}.
     */
    public int getArgIndex(String name) {
        if (argIndexes != null) {
            Integer idx = argIndexes.get(name);
            if (idx != null) return idx;
        }
        return -1;
    }

    public int getArgIndexOrAlias(String name) {
        int idx = getArgIndex(name);
        if (idx >= 0) return idx;
        if (aliasMap != null) {
            String origin = aliasMap.get(name);
            if (origin != null) {
                return getArgIndex(origin);
            }
        }
        return -1;
    }

    /**
     * Returns whether no-argument construction is available.
     */
    public boolean hasNoArgsCreator() {
        return noArgsCtorLambda != null || noArgsCtorHandle != null;
    }

    /**
     * Creates an object through its no-argument constructor.
     */
    public Object newPojoNoArgs() {
        if (noArgsCtorLambda != null) {
            return noArgsCtorLambda.get();
        } else if (noArgsCtorHandle != null) {
            try {
                return noArgsCtorHandle.invoke();
            } catch (Throwable e) {
                throw new BindingException("failed to invoke constructor of " + clazz, e);
            }
        }
        throw new BindingException("failed to create instance of " + clazz + ": Not found no-args constructor");
    }


    /**
     * Creates an object through its argument-based creator.
     */
    public Object newPojoWithArgs(Object[] args) {
        Objects.requireNonNull(args, "args");
        if (argsCreatorHandle == null) {
            throw new BindingException("failed to create instance of " + clazz + ": No creator constructor");
        }
        try {
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    Class<?> argClazz = Types.rawClazz(argTypes[i]);
                    args[i] = _missingValueOfClass(argClazz);
                }
            }

            if (args.length == 1 && argsCreatorLambda1 != null) {
                return argsCreatorLambda1.apply(args[0]);
            }
            if (args.length == 2 && argsCreatorLambda2 != null) {
                return argsCreatorLambda2.apply(args[0], args[1]);
            }
            if (args.length == 3 && argsCreatorLambda3 != null) {
                return argsCreatorLambda3.apply(args[0], args[1], args[2]);
            }
            if (args.length == 4 && argsCreatorLambda4 != null) {
                return argsCreatorLambda4.apply(args[0], args[1], args[2], args[3]);
            }
            if (args.length == 5 && argsCreatorLambda5 != null) {
                return argsCreatorLambda5.apply(args[0], args[1], args[2], args[3], args[4]);
            }

            return argsCreatorHandle.invokeWithArguments(args);
        } catch (Throwable e) {
            throw new BindingException("failed to invoke creator constructor of " + clazz, e);
        }
    }

    /**
     * Creates an object, preferring its no-argument constructor.
     */
    public Object forceNewPojo() {
        if (noArgsCtorHandle != null) return newPojoNoArgs();
        Object[] args = new Object[argNames.length];
        return newPojoWithArgs(args);
    }

    /**
     * Returns default missing value for primitive classes.
     */
    private static Object _missingValueOfClass(Class<?> clazz) {
        if (clazz == null) return null;
        if (!clazz.isPrimitive()) return null;
        if (clazz == boolean.class) return false;
        if (clazz == byte.class) return (byte) 0;
        if (clazz == short.class) return (short) 0;
        if (clazz == int.class) return 0;
        if (clazz == long.class) return 0L;
        if (clazz == float.class) return 0f;
        if (clazz == double.class) return 0d;
        if (clazz == char.class) return '\0';
        return null;
    }
}
    /**
     * Returns the creator argument index for a name or its alias, or {@code -1}.
     */
