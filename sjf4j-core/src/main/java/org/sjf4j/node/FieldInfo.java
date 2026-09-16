package org.sjf4j.node;

import org.sjf4j.exception.BindingException;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Cached binding metadata and accessors for one object property.
 */
public class FieldInfo {

    public enum ContainerKind {
        NONE,
        LIST,
        SET,
        MAP,
        ARRAY
    }

    public final String name;
    public final Field publicField;

    public final Type type;
    public final boolean genericDependent;
    public final Class<?> boxed;

    public final ContainerKind containerKind;
    public final Type argType;
    public final Class<?> argClazz;
    public final Class<?> argBoxed;
    public final OneOfInfo argOneOfInfo;

    public final Method publicGetter;
    public final MethodHandle getterHandle;
    public final Function<Object, Object> getterLambda;

    public final Method publicSetter;
    public final MethodHandle setterHandle;
    public final BiConsumer<Object, Object> setterLambda;

    public final OneOfInfo oneOfInfo;
    public final String codecName;
    public final ValueCodecInfo resolvedValueCodec;

    /**
     * Creates property binding metadata and resolves its container element type.
     */
    public FieldInfo(String name, Type type, Field publicField,
                     Method publicGetter, MethodHandle getterHandle, Function<Object, Object> getterLambda,
                     Method publicSetter, MethodHandle setterHandle, BiConsumer<Object, Object> setterLambda,
                     OneOfInfo oneOfInfo, String codecName, ValueCodecInfo resolvedValueCodec) {
        this.name = name;
        this.publicField = publicField;

        this.type = type;
        if (Types.containsTypeVariable(type)) {
            this.genericDependent = true;
            this.boxed = Object.class;
        } else {
            this.genericDependent = false;
            this.boxed = Types.rawBox(type);
        }

        ContainerKind kind = ContainerKind.NONE;
        Type argType = null;
        Class<?> argClazz = null;
        if (List.class.isAssignableFrom(this.boxed)) {
            kind = ContainerKind.LIST;
            argType = Types.resolveTypeArgument(type, List.class, 0);
            argClazz = Types.rawBox(argType);
        } else if (Set.class.isAssignableFrom(this.boxed)) {
            kind = ContainerKind.SET;
            argType = Types.resolveTypeArgument(type, Set.class, 0);
            argClazz = Types.rawBox(argType);
        } else if (Map.class.isAssignableFrom(this.boxed)) {
            kind = ContainerKind.MAP;
            argType = Types.resolveTypeArgument(type, Map.class, 1);
            argClazz = Types.rawBox(argType);
        } else if (this.boxed.isArray()) {
            kind = ContainerKind.ARRAY;
            argType = this.boxed.getComponentType();
            argClazz = Types.box((Class<?>) argType);
        }
        this.containerKind = kind;
        this.argType = argType;
        this.argClazz = argClazz;
        this.argBoxed = Types.rawBox(argClazz);
        this.argOneOfInfo = argBoxed == null ? null : ReflectUtil.resolveOneOfInfo(argBoxed);

        this.publicGetter = publicGetter;
        this.getterHandle = getterHandle;
        this.getterLambda = getterLambda;

        this.publicSetter = publicSetter;
        this.setterHandle = setterHandle;
        this.setterLambda = setterLambda;

        this.oneOfInfo = oneOfInfo;
        this.codecName = codecName;
        this.resolvedValueCodec = resolvedValueCodec;
    }

    /**
     * Returns true when a getter is available.
     */
    public boolean hasGetter() {
        return getterHandle != null || getterLambda != null;
    }

    /**
     * Returns true when a setter is available.
     */
    public boolean hasSetter() {
        return setterHandle != null || setterLambda != null;
    }


    /**
     * Invokes this property's getter.
     */
    public Object invokeGetter(Object receiver) {
        Objects.requireNonNull(receiver, "receiver");
        if (getterLambda != null) {
            return getterLambda.apply(receiver);
        }
        if (getterHandle == null) {
            throw new BindingException("no getter available for property '" + name + "' of " + type);
        }
        try {
            return getterHandle.invoke(receiver);
        } catch (Throwable e) {
            throw new BindingException("failed to invoke getter for property '" + name + "' of " + type, e);
        }
    }

    /**
     * Invokes this property's setter when present and reports success.
     */
    public boolean invokeSetterIfPresent(Object receiver, Object value) {
        if (setterHandle == null && setterLambda == null) return false;
        invokeSetter(receiver, value);
        return true;
    }

    /**
     * Invokes this property's setter.
     */
    public void invokeSetter(Object receiver, Object value) {
        Objects.requireNonNull(receiver, "receiver");
        try {
            if (setterLambda != null) {
                setterLambda.accept(receiver, value);
                return;
            }
            if (setterHandle == null)
                throw new BindingException("no setter available for property '" + name + "' of " + type);
            setterHandle.invoke(receiver, value);
        } catch (Throwable e) {
            throw new BindingException("failed to invoke setter for property '" + name + "' of type '" + type +
                    "' with value '" + Types.name(value) + "' (node type: " + Types.name(receiver) + ")", e);
        }
    }

}
