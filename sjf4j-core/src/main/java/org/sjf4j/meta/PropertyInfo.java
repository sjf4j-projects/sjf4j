package org.sjf4j.meta;

import org.sjf4j.binding.PropertyParser;
import org.sjf4j.util.Asserts;
import org.sjf4j.util.Invokers;
import org.sjf4j.util.Types;
import org.sjf4j.value.ValueInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Cached binding metadata and accessors for one object property.
 */
public final class PropertyInfo {

    public final String name;
    public final String[] alias;

    public final Field publicField;
    public final Type type;
    public final boolean genericDependent;
    public final Class<?> boxed;
    /** Lazily resolved declared type metadata, when runtime registration is useful. */
    public final LazyTypeInfo typeInfo;

    /** Container value (Map) or element (List, Set, array) declaration. */
    public final Type contentType;
    public final Class<?> contentBoxed;
    /** Lazily resolved content class metadata; generic-dependent declarations resolve at runtime. */
    public final LazyTypeInfo contentTypeInfo;

    public final Method publicGetter;
    public final MethodHandle getterHandle;
    public final Function<Object, Object> getterLambda;

    public final Method publicSetter;
    public final MethodHandle setterHandle;
    public final BiConsumer<Object, Object> setterLambda;

    public final OneOfInfo oneOfInfo;
    public final String valueFormat;
    public final ValueInfo valueInfo;

    public final boolean gettable;
    public final boolean settable;

    /** Binding-only directional visibility; structural property access is unaffected. */
    public final boolean bindingRead;
    public final boolean bindingWrite;

    public final PropertyParser parser;

    /**
     * Creates property binding metadata and resolves its container element type.
     */
    public PropertyInfo(String name, String[] alias, Field publicField, Type type, boolean genericDependent, Class<?> boxed,
                        Method publicGetter, MethodHandle getterHandle, Function<Object, Object> getterLambda,
                        Method publicSetter, MethodHandle setterHandle, BiConsumer<Object, Object> setterLambda,
                        OneOfInfo explicitOneOfInfo,
                        String valueFormat, ValueInfo valueInfo, MethodHandles.Lookup lookup,
                        boolean bindingRead, boolean bindingWrite) {
        this.name = name;
        this.bindingRead = bindingRead;
        this.bindingWrite = bindingWrite;
        this.alias = alias;
        this.publicField = publicField;
        this.type = type;
        this.genericDependent = genericDependent;
        this.boxed = boxed;
        this.typeInfo = genericDependent ? null : LazyTypeInfo.of(boxed);

        Type contentType = null;
        if (List.class.isAssignableFrom(this.boxed)) {
            contentType = Types.resolveTypeArgument(type, List.class, 0);
        } else if (Set.class.isAssignableFrom(this.boxed)) {
            contentType = Types.resolveTypeArgument(type, Set.class, 0);
        } else if (Map.class.isAssignableFrom(this.boxed)) {
            contentType = Types.resolveTypeArgument(type, Map.class, 1);
        } else if (this.boxed.isArray()) {
            contentType = this.boxed.getComponentType();
        }
        this.contentType = contentType;
        this.contentBoxed = contentType == null ? null : Types.rawBox(contentType);
        this.contentTypeInfo = genericDependent ? null : LazyTypeInfo.of(contentBoxed);

        this.publicGetter = publicGetter;
        this.getterHandle = getterHandle;
        this.getterLambda = getterLambda;

        this.publicSetter = publicSetter;
        this.setterHandle = setterHandle;
        this.setterLambda = setterLambda;

        // Expose the effective OneOf mapping while parsing only explicit property-level overrides.
        this.oneOfInfo = explicitOneOfInfo != null ? explicitOneOfInfo : ReflectUtil.resolveOneOfInfo(boxed);
        this.valueFormat = valueFormat;
        this.valueInfo = valueInfo;
        this.gettable = getterHandle != null || getterLambda != null;
        this.settable = setterHandle != null || setterLambda != null;

        this.parser = PropertyParser.create(name, type, boxed, genericDependent, explicitOneOfInfo,
                setterHandle, setterLambda, valueInfo, contentType, contentBoxed, typeInfo, contentTypeInfo, lookup);
    }

    /**
     * Invokes this property's getter.
     */
    public Object invokeGetter(Object receiver) {
        Asserts.notNull(receiver, "receiver");
        return Invokers.invokeGetter(name, getterHandle, getterLambda, receiver);
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
        Invokers.invokeSetter(name, setterHandle, setterLambda, receiver, value);
    }

}
