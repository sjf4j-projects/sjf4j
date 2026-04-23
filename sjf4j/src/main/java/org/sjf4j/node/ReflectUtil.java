package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;

import java.lang.annotation.Annotation;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Reflection helpers for POJO and @NodeValue analysis.
 */
@SuppressWarnings("unchecked")
public final class ReflectUtil {

    /**
     * Flag indicating if the current JVM is running JDK 8.
     */
    public static final boolean IS_JDK8 = System.getProperty("java.version").startsWith("1.");


    /// POJO

    /**
     * Returns true when class can be treated as a POJO node type.
     */
    public static boolean isPojoCandidate(Class<?> clazz) {
        if (clazz == null || clazz == Object.class || clazz.isPrimitive() || clazz == String.class ||
                Number.class.isAssignableFrom(clazz) || clazz == Boolean.class) {
            return false;
        }
        if (clazz == JsonObject.class || clazz == JsonArray.class) {
            return false;
        }
        if (Map.class.isAssignableFrom(clazz) || Collection.class.isAssignableFrom(clazz) ||
                clazz.isEnum() || clazz.isInterface() || clazz.isArray()) {
            return false;
        }

        return !_isFrameworkPackage(clazz.getName());
    }


    public static NodeRegistry.ContainerInfo analyzeContainer(Class<?> clazz) {
        if (clazz == null || clazz == Object.class || clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            return null;
        }

        NodeKind kind;
        if (Map.class.isAssignableFrom(clazz)) {
            kind = NodeKind.OBJECT_MAP;
        } else if (List.class.isAssignableFrom(clazz)) {
            kind = NodeKind.ARRAY_LIST;
        } else if (Set.class.isAssignableFrom(clazz)) {
            kind = NodeKind.ARRAY_SET;
        } else {
            return null;
        }

        MethodHandles.Lookup lookup = _resolveLookup(clazz);
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            try { ctor.setAccessible(true); } catch (RuntimeException ignored) {}
            MethodHandle noArgsCtor = lookup.unreflectConstructor(ctor);
            Supplier<?> noArgsLambdaCtor = createLambdaConstructor(lookup, clazz, noArgsCtor);
            return new NodeRegistry.ContainerInfo(clazz, kind, noArgsCtor, noArgsLambdaCtor);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }

    public static NodeRegistry.AnyOfInfo resolveAnyOfInfo(Class<?> clazz) {
        if (clazz == null || clazz == Object.class) {
            return null;
        }
        AnyOf ann = clazz.getAnnotation(AnyOf.class);
        return ann == null ? null : analyzeAnyOf(clazz, ann);
    }

    public static NodeRegistry.PojoInfo analyzePojo(Class<?> clazz, boolean orElseThrow) {
        if (!isPojoCandidate(clazz)) {
            if (orElseThrow) throw new JsonException("Class " + clazz.getName() + " cannot be a POJO candidate");
            else return null;
        }

        MethodHandles.Lookup lookup = _resolveLookup(clazz);

        // Creator constructor (for final fields / record-style)
        NodeRegistry.CreatorInfo creatorInfo = null;
        try {
            creatorInfo = analyzeCreator(clazz, lookup);
        } catch (Exception e) {
            if (orElseThrow) throw e instanceof JsonException ? (JsonException) e : new JsonException(e);
            else return null;
        }

        NodeBinding nodeBinding = clazz.getAnnotation(NodeBinding.class);
        NamingStrategy namingStrategy = null;
        AccessStrategy accessStrategy = AccessStrategy.BEAN_BASED;
        boolean readDynamic = true;
        boolean writeDynamic = true;
        if (nodeBinding != null) {
            namingStrategy = nodeBinding.naming();
            if (namingStrategy == NamingStrategy.IDENTITY) {
                namingStrategy = null;
            }
            accessStrategy = nodeBinding.access();
            readDynamic = nodeBinding.readDynamic();
            writeDynamic = nodeBinding.writeDynamic();
        }

        // Fields
        Map<String, NodeRegistry.FieldInfo> fields = new LinkedHashMap<>();
        Map<String, String> aliasMap = creatorInfo.aliasMap;
        boolean hasExplicitBinding = false;
        boolean hasNonPublicFields = false;
        boolean hasNonPublicReaderGap = false;
        boolean hasNonPublicWriterGap = false;
        boolean allowPlainPojoFieldAccess = accessStrategy == AccessStrategy.FIELD_BASED;
        Class<?> curClazz = clazz;
        Type curType = clazz;
        do {
            Field[] fds = curClazz.getDeclaredFields();
            try { AccessibleObject.setAccessible(fds, true); } catch (RuntimeException ignored) {}
            for (Field field : fds) {
                int mod = field.getModifiers();
                NodeProperty nodeProperty = field.getAnnotation(NodeProperty.class);
                if (Modifier.isStatic(mod)) { continue; }
                if (Modifier.isTransient(mod)) {
                    if (nodeProperty != null) {
                        throw new JsonException("Transient field '" + field.getName() + "' in " + clazz.getName() +
                                " cannot use @NodeProperty");
                    }
                    continue;
                }
                boolean forceFieldBinding = nodeProperty != null;
                boolean nonPublicField = !Modifier.isPublic(mod);
                Method publicGetterMethod = null;
                Method publicSetterMethod = null;
                boolean fieldReaderGap = false;
                boolean fieldWriterGap = false;
                if (nonPublicField) {
                    publicGetterMethod = _findPublicGetterMethod(clazz, field);
                    publicSetterMethod = _findPublicSetterMethod(clazz, field);
                    fieldReaderGap = publicSetterMethod == null;
                    fieldWriterGap = publicGetterMethod == null;
                }
                if (forceFieldBinding) {
                    hasExplicitBinding = true;
                }
                if (nonPublicField) {
                    hasNonPublicFields = true;
                }
                if (fieldReaderGap) {
                    hasNonPublicReaderGap = true;
                }
                if (fieldWriterGap) {
                    hasNonPublicWriterGap = true;
                }
                if (nonPublicField && !forceFieldBinding && !allowPlainPojoFieldAccess
                        && fieldReaderGap && fieldWriterGap) {
                    continue;
                }

                MethodHandle getter = null;
                if (!nonPublicField || forceFieldBinding || allowPlainPojoFieldAccess) {
                    try {
                        getter = lookup.unreflectGetter(field);
                    } catch (Exception e) {
                        // log.warn("Failed to get getter for '{}' of {}", field.getName(), curClazz, e);
                    }
                } else {
                    if (publicGetterMethod != null) {
                        try {
                            getter = lookup.unreflect(publicGetterMethod);
                        } catch (Exception e) {
                            // log.warn("Failed to get method getter for '{}' of {}", field.getName(), curClazz, e);
                        }
                    }
                }
                Function<Object, Object> lambdaGetter = createLambdaGetter(lookup, curClazz, field);

                MethodHandle setter = null;
                BiConsumer<Object, Object> lambdaSetter = null;
                if (!Modifier.isFinal(mod)) {
                    if (!nonPublicField || forceFieldBinding || allowPlainPojoFieldAccess) {
                        try {
                            setter = lookup.unreflectSetter(field);
                        } catch (Exception e) {
                            // log.warn("Failed to get setter for '{}' of {}", field.getName(), curClazz, e);
                        }
                    } else {
                        if (publicSetterMethod != null) {
                            try {
                                setter = lookup.unreflect(publicSetterMethod);
                            } catch (Exception e) {
                                // log.warn("Failed to get method setter for '{}' of {}", field.getName(), curClazz, e);
                            }
                        }
                    }
                    lambdaSetter = createLambdaSetter(lookup, curClazz, field);

                }

                if (getter == null && lambdaGetter == null && setter == null && lambdaSetter == null) {
                    // log.warn("No accessible getter or setter found for field '{}' of {}", field.getName(), curClazz);
                } else {
                    Type fieldType = Types.fieldType(curType, field);
                    Class<?> fieldClazz = Types.rawClazz(fieldType);
                    NodeRegistry.AnyOfInfo anyOfInfo = null;
                    AnyOf ann = field.getAnnotation(AnyOf.class);
                    if (ann != null) {
                        anyOfInfo = ReflectUtil.analyzeAnyOf(fieldClazz, ann);
                    } else {
                        anyOfInfo = resolveAnyOfInfo(fieldClazz);
                    }
                    NodeRegistry.ValueCodecInfo resolvedValueCodec = null;
                    String valueFormat = getValueFormat(field);
                    if (valueFormat != null) {
                        resolvedValueCodec = NodeRegistry.resolveValueCodecOrElseThrow(fieldClazz, valueFormat);
                    }
                    NodeRegistry.FieldInfo fi = new NodeRegistry.FieldInfo(field.getName(),
                            fieldType, getter, lambdaGetter, setter, lambdaSetter,
                            anyOfInfo, valueFormat, resolvedValueCodec);
                    String fieldName = _getFieldName(field, curClazz);
                    NodeRegistry.FieldInfo oldFi = fields.putIfAbsent(fieldName, fi);
                    if (oldFi != null) {
                        continue;
                    }

                    String[] aliases = getAliases(field);
                    if (aliases != null) {
                        for (String alias : aliases) {
                            if (alias == null || alias.isEmpty() || alias.equals(fieldName)) continue;
                            if (aliasMap == null) aliasMap = new HashMap<>();
                            String old = aliasMap.put(alias, fieldName);
                            if (old != null)
                                throw new JsonException("Alias '" + alias + "' is mapped to multiple fields in " +
                                        clazz.getName());
                        }
                    }
                }
            }
            curType = curClazz.getGenericSuperclass();
            curClazz = curClazz.getSuperclass();
        } while (isPojoCandidate(curClazz));

        // Make aliasFields
        Map<String, NodeRegistry.FieldInfo> aliasFields = null;
        if (aliasMap != null ) {
            aliasFields = new HashMap<>(fields);
            for (Map.Entry<String, String> alias : aliasMap.entrySet()) {
                NodeRegistry.FieldInfo fi = fields.get(alias.getValue());
                if (fi != null) aliasFields.put(alias.getKey(), fi);
            }
        }

        return new NodeRegistry.PojoInfo(clazz, creatorInfo, namingStrategy, accessStrategy,
                readDynamic, writeDynamic, fields, aliasFields,
                hasExplicitBinding, hasNonPublicFields, hasNonPublicReaderGap, hasNonPublicWriterGap);
    }

    private static Method _findPublicGetterMethod(Class<?> ownerClass, Field field) {
        String capitalized = capitalize(field.getName());
        String getterName = "get" + capitalized;
        Method getter = _findPublicMethod(ownerClass, getterName);
        if (getter != null && getter.getReturnType() != void.class) {
            return getter;
        }
        if (isRecord(ownerClass)) {
            Method componentGetter = _findPublicMethod(ownerClass, field.getName());
            if (componentGetter != null && componentGetter.getReturnType() != void.class) {
                return componentGetter;
            }
        }
        Class<?> fieldClass = field.getType();
        if (fieldClass == boolean.class || fieldClass == Boolean.class) {
            Method booleanGetter = _findPublicMethod(ownerClass, "is" + capitalized);
            if (booleanGetter != null &&
                    (booleanGetter.getReturnType() == boolean.class || booleanGetter.getReturnType() == Boolean.class)) {
                return booleanGetter;
            }
        }
        return null;
    }

    private static Method _findPublicSetterMethod(Class<?> ownerClass, Field field) {
        if (Modifier.isFinal(field.getModifiers())) {
            return null;
        }
        return _findPublicMethod(ownerClass, "set" + capitalize(field.getName()), field.getType());
    }

    private static Method _findPublicMethod(Class<?> ownerClass, String name, Class<?>... parameterTypes) {
        try {
            Method method = ownerClass.getMethod(name, parameterTypes);
            return Modifier.isPublic(method.getModifiers()) ? method : null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static String _getFieldName(Field field, Class<?> ownerClass) {
        String fname = getExplicitName(field);
        if (fname != null) return fname;
        return getNamingStrategy(ownerClass).translate(field.getName());
    }

    public static NamingStrategy getNamingStrategy(Class<?> clazz) {
        if (clazz == null) return NamingStrategy.IDENTITY;
        NodeBinding nodeBinding = clazz.getAnnotation(NodeBinding.class);
        if (nodeBinding == null) return NamingStrategy.IDENTITY;
        return nodeBinding.naming();
    }

    public static String getExplicitName(AnnotatedElement element) {
        String fname = null;
        NodeProperty ann = element.getAnnotation(NodeProperty.class);
        if (ann != null) fname = ann.value();
        if (fname != null && !fname.isEmpty()) return fname;
        fname = _getAnnotationString(element, CLASS_JACKSON3_JSON_PROPERTY, "value");
        if (fname != null && !fname.isEmpty()) return fname;
        fname = _getAnnotationString(element, CLASS_JACKSON2_JSON_PROPERTY, "value");
        if (fname != null && !fname.isEmpty()) return fname;
        fname = _getAnnotationString(element, CLASS_FASTJSON2_JSON_FIELD, "name");
        if (fname != null && !fname.isEmpty()) return fname;
        return null;
    }

    public static String[] getAliases(AnnotatedElement element) {
        String[] aliases = null;
        NodeProperty ann = element.getAnnotation(NodeProperty.class);
        if (ann != null) aliases = ann.aliases();
        if (aliases != null && aliases.length > 0) return aliases;
        aliases = _getAnnotationStringArray(element, CLASS_JACKSON3_JSON_ALIAS, "value");
        if (aliases != null && aliases.length > 0) return aliases;
        aliases = _getAnnotationStringArray(element, CLASS_JACKSON2_JSON_ALIAS, "value");
        if (aliases != null && aliases.length > 0) return aliases;
        aliases = _getAnnotationStringArray(element, CLASS_FASTJSON2_JSON_FIELD, "alternateNames");
        if (aliases != null && aliases.length > 0) return aliases;
        return null;
    }

    public static String getValueFormat(AnnotatedElement element) {
        NodeProperty ann = element.getAnnotation(NodeProperty.class);
        if (ann == null) return null;
        String valueFormat = ann.valueFormat();
        return NodeProperty.VALUE_FORMAT_UNSET.equals(valueFormat) ? null : valueFormat;
    }


    private static final String[] FRAMEWORK_PREFIX = {
            "java.", "javax.", "jakarta.", "jdk.",
            "com.fasterxml.jackson.", "tools.jackson.", "com.google.gson."
    };

    private static boolean _isFrameworkPackage(String clazzName) {
        for (String prefix : FRAMEWORK_PREFIX) if (clazzName.startsWith(prefix)) return true;
        return false;
    }

    private static final MethodHandles.Lookup ROOT_LOOKUP = MethodHandles.lookup();
    private static final Method PRIVATE_LOOKUP_IN;
    static {
        Method privateLookupIn = null;
        try {
            privateLookupIn = MethodHandles.class.getMethod("privateLookupIn", Class.class,
                    MethodHandles.Lookup.class);
        } catch (Exception e) {
//            throw new RuntimeException(e);
        }
        PRIVATE_LOOKUP_IN = privateLookupIn;
    }

    private static MethodHandles.Lookup _resolveLookup(Class<?> clazz) {
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


    public static NodeRegistry.CreatorInfo analyzeCreator(Class<?> clazz,
                                                          MethodHandles.Lookup lookup) {
        Executable creator = null;
        MethodHandle creatorHandle = null;
        NodeRegistry.Func1 creatorLambda1 = null;
        NodeRegistry.Func2 creatorLambda2 = null;
        NodeRegistry.Func3 creatorLambda3 = null;
        NodeRegistry.Func4 creatorLambda4 = null;
        NodeRegistry.Func5 creatorLambda5 = null;
        String[] argNames = null;
        Type[] argTypes = null;
        Map<String, Integer> argIndexes = null;
        Map<String, String> aliasMap = null;
        MethodHandle noArgsCtor = null;
        Supplier<?> noArgsLambdaCtor = null;
        String[] argValueFormats = null;
        NodeRegistry.ValueCodecInfo[] argValueCodecs = null;

        // 1. Find defined creator
        Constructor<?>[] ctors = clazz.getDeclaredConstructors();
        for (Constructor<?> ctor : ctors) {
            if (ctor.isAnnotationPresent(NodeCreator.class) || hasCreatorAnnotation(ctor)) {
                if (creator != null) {
                    throw new JsonException("Multiple creator definitions found in " + clazz.getName());
                }
                try {
                    try { ctor.setAccessible(true); } catch (RuntimeException ignored) {}
                    creatorHandle = lookup.unreflectConstructor(ctor);
                    creator = ctor;
                } catch (IllegalAccessException e) {
                    throw new JsonException("Cannot access creator constructor of " + clazz.getName(), e);
                }
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) continue;
            if (method.isAnnotationPresent(NodeCreator.class) || hasCreatorAnnotation(method)) {
                if (creator != null) {
                    throw new JsonException("Multiple creator definitions found in " + clazz.getName());
                }
                if (!clazz.isAssignableFrom(method.getReturnType())) {
                    throw new JsonException("Creator method must return " + clazz.getName() + ": " + method);
                }
                try {
                    try { method.setAccessible(true); } catch (RuntimeException ignored) {}
                    creatorHandle = lookup.unreflect(method);
                    creator = method;
                } catch (IllegalAccessException e) {
                    throw new JsonException("Cannot access creator method '" + method.getName() +
                            "' of " + clazz.getName(), e);
                }
            }
        }

        if (creator == null && ctors.length == 1) {
            try {
                try { ctors[0].setAccessible(true); } catch (RuntimeException ignored) {}
                creatorHandle = lookup.unreflectConstructor(ctors[0]);
                creator = ctors[0];
            } catch (IllegalAccessException e) {
                throw new JsonException("Cannot access creator constructor of " + clazz.getName(), e);
            }
        }

        // 2. Find Record
        NodeRegistry.RecordInfo recordInfo = analyzeRecord(clazz, lookup);
        if (recordInfo != null && (creator == null || creator == recordInfo.compCtor)) {
            if (creator == null) creator = recordInfo.compCtor;
            if (creatorHandle == null) creatorHandle = recordInfo.compCtorHandle;
        }
        if (creator != null && creator.getParameterCount() > 0) {
            boolean hasPrimitiveArg = false;
            for (Class<?> argType : creator.getParameterTypes()) {
                if (argType.isPrimitive()) {
                    hasPrimitiveArg = true;
                    break;
                }
            }

            if (!hasPrimitiveArg) {
                switch (creator.getParameterCount()) {
                    case 1:
                        creatorLambda1 = createLambdaArgsCreator(lookup, creatorHandle, NodeRegistry.Func1.class, 1);
                        break;
                    case 2:
                        creatorLambda2 = createLambdaArgsCreator(lookup, creatorHandle, NodeRegistry.Func2.class, 2);
                        break;
                    case 3:
                        creatorLambda3 = createLambdaArgsCreator(lookup, creatorHandle, NodeRegistry.Func3.class, 3);
                        break;
                    case 4:
                        creatorLambda4 = createLambdaArgsCreator(lookup, creatorHandle, NodeRegistry.Func4.class, 4);
                        break;
                    case 5:
                        creatorLambda5 = createLambdaArgsCreator(lookup, creatorHandle, NodeRegistry.Func5.class, 5);
                        break;
                    default:
                        break;
                }
            }

            Parameter[] params = creator.getParameters();
            argTypes = creator.getGenericParameterTypes();
            argNames = new String[params.length];
            argValueFormats = new String[params.length];
            argValueCodecs = new NodeRegistry.ValueCodecInfo[params.length];
            for (int i = 0; i < params.length; i++) {
                String name = getExplicitName(params[i]);
                if (name == null) {
                    if (params[i].isNamePresent()) {
                        name = getNamingStrategy(creator.getDeclaringClass()).translate(params[i].getName());
                    }
                }
                if (name == null || name.isEmpty())
                    throw new JsonException("Missing parameter name for creator in " + clazz.getName() +
                            ": parameter index " + i + " (from 0). Use @NodeProperty on parameters.");
                argNames[i] = name;
                String valueFormat = getValueFormat(params[i]);
                argValueFormats[i] = valueFormat;
                if (valueFormat != null) {
                    argValueCodecs[i] = NodeRegistry.resolveValueCodecOrElseThrow(Types.rawBox(argTypes[i]), valueFormat);
                }
            }
            argIndexes = createArgIndexes(argNames);
        }
        if (creator != null && creator.getParameterCount() > 0) {
            Parameter[] params = creator.getParameters();
            for (int i = 0; i < params.length; i++) {
                String[] aliases = getAliases(params[i]);
                if (aliases != null && aliases.length > 0) {
                    if (aliasMap == null) aliasMap = new HashMap<>();
                    for (String alias : aliases) {
                        String old = aliasMap.put(alias, argNames[i]);
                        if (old != null)
                            throw new JsonException("Alias '" + alias + "' is mapped to multiple fields in " +
                                    clazz.getName());
                    }
                }
            }
        }

        // 3. Find no-args Constructor
        if (creator == null) {
            try {
                Constructor<?> ctor = clazz.getDeclaredConstructor();
                try { ctor.setAccessible(true); } catch (RuntimeException ignored) {}
                noArgsCtor = lookup.unreflectConstructor(ctor);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new JsonException("No defined creator or no-args constructor of " + clazz.getName(), e);
            }
            noArgsLambdaCtor = createLambdaConstructor(lookup, clazz, noArgsCtor);
        } else if (creator.getParameterCount() == 0) {
            // The defined creator is no-args Constructor
            noArgsCtor = creatorHandle;
            noArgsLambdaCtor = createLambdaConstructor(lookup, clazz, noArgsCtor);
            creator = null;
            creatorHandle = null;
        }

        return new NodeRegistry.CreatorInfo(clazz, noArgsCtor, noArgsLambdaCtor,
                creator, creatorHandle,
                creatorLambda1, creatorLambda2, creatorLambda3, creatorLambda4, creatorLambda5,
                argNames, argTypes, argValueFormats, argValueCodecs, argIndexes, aliasMap);
    }

    /// NodeValue

    public static NodeRegistry.ValueCodecInfo analyzeNodeValue(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(NodeValue.class)) return null;

        MethodHandle valueToRawHandle = null, rawToValueHandle = null, valueCopyHandle = null;
        MethodHandles.Lookup lookup = _resolveLookup(clazz);

        Class<?> current = clazz;
        while (current != null && current != Object.class &&
                (valueToRawHandle == null || rawToValueHandle == null || valueCopyHandle == null)) {
            for (Constructor<?> ctor : current.getDeclaredConstructors()) {
                // Decode
                if (ctor.isAnnotationPresent(NodeValue.class)) {
                    if (rawToValueHandle != null)
                        throw new JsonException("Multiple @" + NodeValue.class.getName() +
                                " definitions found in " + clazz.getName());
                    try {
                        rawToValueHandle = lookup.unreflectConstructor(ctor);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            for (Method m : current.getDeclaredMethods()) {
                if (m.isBridge()) continue;
                // Encode
                if (m.isAnnotationPresent(ValueToRaw.class)) {
                    if (valueToRawHandle != null)
                        throw new JsonException("Multiple @" + ValueToRaw.class.getName() +
                                " definitions found in " + clazz.getName());
                    if (Modifier.isStatic(m.getModifiers()))
                        throw new JsonException("Cannot use @" + ValueToRaw.class.getName() +
                                " on static methods in " + clazz.getName());
                    if (current != clazz) {
                        Method override = _findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        valueToRawHandle = lookup.unreflect(m);
                        continue;
                    } catch (IllegalAccessException e) {
                        throw new JsonException(e);
                    }
                }
                // Decode
                if (m.isAnnotationPresent(RawToValue.class)) {
                    if (rawToValueHandle != null)
                        throw new JsonException("Multiple @" + RawToValue.class.getName() +
                                " definitions found in " + clazz.getName());
                    if (!Modifier.isStatic(m.getModifiers()))
                        throw new JsonException("Must use @" + RawToValue.class.getName() +
                                " on constructor or static methods in " + clazz.getName());
                    if (current != clazz) {
                        Method override = _findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        rawToValueHandle = lookup.unreflect(m);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
                // Copy
                if (m.isAnnotationPresent(ValueCopy.class)) {
                    if (valueCopyHandle != null)
                        throw new JsonException("Multiple @" + ValueCopy.class.getName() +
                                " definitions found in " + clazz.getName());
                    if (Modifier.isStatic(m.getModifiers()))
                        throw new JsonException("Cannot use @" + ValueCopy.class.getName() +
                                " on static methods in " + clazz.getName());
                    if (current != clazz) {
                        Method override = _findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        valueCopyHandle = lookup.unreflect(m);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }// for
            current = current.getSuperclass();
        }

        if (valueToRawHandle == null)
            throw new JsonException("Missing @" + ValueToRaw.class.getName() + " method in " + clazz.getName());
        if (valueToRawHandle.type().parameterCount() != 1) {
            throw new JsonException("@" + ValueToRaw.class.getName() + " method must have no parameters, but found " +
                    (valueToRawHandle.type().parameterCount() - 1) + ", in " + clazz.getName());
        }
        Class<?> valueToRawReturnBox = Types.box(valueToRawHandle.type().returnType());
        if (!NodeKind.plainOf(valueToRawReturnBox).isRaw())
            throw new JsonException("@" + ValueToRaw.class.getName() + " method return invalid type " +
                    valueToRawReturnBox.getName() + " in " + clazz.getName() +
                    ". The return type must be a supported raw type (String, Number, Boolean, null, Map, or List).");

        if (rawToValueHandle == null)
            throw new JsonException("Missing @" + RawToValue.class.getName() + " method in " + clazz.getName());
        if (rawToValueHandle.type().parameterCount() != 1)
            throw new JsonException("@" + RawToValue.class.getName() +
                    " method must have exactly one parameter, but found " + rawToValueHandle.type().parameterCount());
        Class<?> rawToValueParamBox = Types.box(rawToValueHandle.type().parameterType(0));
        Class<?> rawToValueReturnClazz = rawToValueHandle.type().returnType();
        if (rawToValueParamBox != valueToRawReturnBox)
            throw new JsonException("@" + RawToValue.class.getName() + " method parameter type must match @" +
                    ValueToRaw.class.getName() + " return type. " + "Expected: " + valueToRawReturnBox.getName() +
                    ", Found: " + rawToValueParamBox.getName());
        if (rawToValueReturnClazz != clazz)
            throw new JsonException("@" + RawToValue.class.getName() + " method return type must be " +
                    clazz.getName() + ", but found " + rawToValueReturnClazz.getName());

        if (valueCopyHandle != null) {
            if (valueCopyHandle.type().parameterCount() != 1)
                throw new JsonException("@" + ValueCopy.class.getName() + " method must have no parameters, but found " +
                        (valueCopyHandle.type().parameterCount() + 1));
            Class<?> copyReturnClazz = valueCopyHandle.type().returnType();
            if (copyReturnClazz != clazz)
                throw new JsonException("@" + ValueCopy.class.getName() + " method return type must be " + clazz.getName() +
                        ", but found " + copyReturnClazz.getName());
        }

        return new NodeRegistry.ValueCodecInfo("", clazz, valueToRawReturnBox, null,
                valueToRawHandle, rawToValueHandle, valueCopyHandle);
    }

    private static Method _findOverride(Method baseMethod, Class<?> clazz) {
        try {
            Method m = clazz.getDeclaredMethod(
                    baseMethod.getName(),
                    baseMethod.getParameterTypes()
            );
            if (m.isBridge()) return null;
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /// Third part annotation
    private static final Class<? extends Annotation> CLASS_JACKSON3_JSON_CREATOR;
    private static final Class<? extends Annotation> CLASS_JACKSON3_JSON_PROPERTY;
    private static final Class<? extends Annotation> CLASS_JACKSON3_JSON_ALIAS;
    private static final Class<? extends Annotation> CLASS_JACKSON2_JSON_CREATOR;
    private static final Class<? extends Annotation> CLASS_JACKSON2_JSON_PROPERTY;
    private static final Class<? extends Annotation> CLASS_JACKSON2_JSON_ALIAS;
    private static final Class<? extends Annotation> CLASS_FASTJSON2_JSON_CREATOR;
    private static final Class<? extends Annotation> CLASS_FASTJSON2_JSON_FIELD;
    static {
        Class<?> jackson3JsonCreator = null;
        Class<?> jackson3JsonProperty = null;
        Class<?> jackson3JsonAlias = null;
        Class<?> jackson2JsonCreator = null;
        Class<?> jackson2JsonProperty = null;
        Class<?> jackson2JsonAlias = null;
        Class<?> fastjson2JsonCreator = null;
        Class<?> fastJson2JsonField = null;
        try {
            jackson3JsonCreator = Class.forName("tools.jackson.annotation.JsonCreator");
        } catch (ClassNotFoundException ignore) {}
        try {
            jackson3JsonProperty = Class.forName("tools.jackson.annotation.JsonProperty");
        } catch (ClassNotFoundException ignore) {}
        try {
            jackson3JsonAlias = Class.forName("tools.jackson.annotation.JsonAlias");
        } catch (ClassNotFoundException ignore) {}
        try {
            jackson2JsonCreator = Class.forName("com.fasterxml.jackson.annotation.JsonCreator");
        } catch (ClassNotFoundException ignore) {}
        try {
            jackson2JsonProperty = Class.forName("com.fasterxml.jackson.annotation.JsonProperty");
        } catch (ClassNotFoundException ignore) {}
        try {
            jackson2JsonAlias = Class.forName("com.fasterxml.jackson.annotation.JsonAlias");
        } catch (ClassNotFoundException ignore) {}
        try {
            fastjson2JsonCreator = Class.forName("com.alibaba.fastjson2.annotation.JSONCreator");
        } catch (ClassNotFoundException ignore) {}
        try {
            fastJson2JsonField = Class.forName("com.alibaba.fastjson2.annotation.JSONField");
        } catch (ClassNotFoundException ignore) {}

        CLASS_JACKSON3_JSON_CREATOR = (Class<? extends Annotation>) jackson3JsonCreator;
        CLASS_JACKSON3_JSON_PROPERTY = (Class<? extends Annotation>) jackson3JsonProperty;
        CLASS_JACKSON3_JSON_ALIAS = (Class<? extends Annotation>) jackson3JsonAlias;
        CLASS_JACKSON2_JSON_CREATOR = (Class<? extends Annotation>) jackson2JsonCreator;
        CLASS_JACKSON2_JSON_PROPERTY = (Class<? extends Annotation>) jackson2JsonProperty;
        CLASS_JACKSON2_JSON_ALIAS = (Class<? extends Annotation>) jackson2JsonAlias;
        CLASS_FASTJSON2_JSON_CREATOR = (Class<? extends Annotation>) fastjson2JsonCreator;
        CLASS_FASTJSON2_JSON_FIELD = (Class<? extends Annotation>) fastJson2JsonField;
    }

    private static boolean hasAnnotation(AnnotatedElement element, Class<? extends Annotation> annotationClass) {
        if (annotationClass == null) return false;
        try {
            return element.isAnnotationPresent(annotationClass);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasCreatorAnnotation(AccessibleObject obj) {
        return hasAnnotation(obj, CLASS_JACKSON3_JSON_CREATOR)
                || hasAnnotation(obj, CLASS_JACKSON2_JSON_CREATOR)
                || hasAnnotation(obj, CLASS_FASTJSON2_JSON_CREATOR);
    }

    private static String _getAnnotationString(AnnotatedElement element,
                                               Class<? extends Annotation> annotationClass,
                                               String methodName) {
        if (annotationClass == null) return null;
        try {
            Annotation ann = element.getAnnotation(annotationClass);
            if (ann == null) return null;
            Method valueMethod = ann.annotationType().getMethod(methodName);
            Object value = valueMethod.invoke(ann);
            return value instanceof String ? (String) value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String[] _getAnnotationStringArray(AnnotatedElement element,
                                                      Class<? extends Annotation> annotationClass,
                                                      String methodName) {
        if (annotationClass == null) return null;
        try {
            Annotation ann = element.getAnnotation(annotationClass);
            if (ann == null) return null;
            Method valueMethod = ann.annotationType().getMethod(methodName);
            Object value = valueMethod.invoke(ann);
            return value instanceof String[] ? (String[]) value : null;
        } catch (Exception e) {
            return null;
        }
    }


    /// Record
    private static final Method METHOD_IS_RECORD;
    private static final Method METHOD_GET_RECORD_COMPONENTS;
    private static final Method METHOD_RECORD_COMPONENT_GET_NAME;
    private static final Method METHOD_RECORD_COMPONENT_GET_TYPE;
    private static final Method METHOD_RECORD_COMPONENT_GET_GENERIC_TYPE;
    private static final Method METHOD_RECORD_COMPONENT_GET_ANNOTATION;

    static {
        Method isRecord = null;
        Method getRecordComponents = null;
        Method recordComponentGetName = null;
        Method recordComponentGetType = null;
        Method recordComponentGetGenericType = null;
        Method recordComponentGetAnnotation = null;
        try {
            isRecord = Class.class.getMethod("isRecord");
            Class<?> recordComponentClass = Class.forName("java.lang.reflect.RecordComponent");
            getRecordComponents = Class.class.getMethod("getRecordComponents");
            recordComponentGetName = recordComponentClass.getMethod("getName");
            recordComponentGetType = recordComponentClass.getMethod("getType");
            recordComponentGetGenericType = recordComponentClass.getMethod("getGenericType");
            recordComponentGetAnnotation = recordComponentClass.getMethod("getAnnotation", Class.class);
        } catch (Exception ignored) {}

        METHOD_IS_RECORD = isRecord;
        METHOD_GET_RECORD_COMPONENTS = getRecordComponents;
        METHOD_RECORD_COMPONENT_GET_NAME = recordComponentGetName;
        METHOD_RECORD_COMPONENT_GET_TYPE = recordComponentGetType;
        METHOD_RECORD_COMPONENT_GET_GENERIC_TYPE = recordComponentGetGenericType;
        METHOD_RECORD_COMPONENT_GET_ANNOTATION = recordComponentGetAnnotation;
    }

    /**
     * Returns true if class is a Java record.
     */
    public static boolean isRecord(Class<?> clazz) {
        if (METHOD_IS_RECORD == null) return false;
        try {
            return (Boolean) METHOD_IS_RECORD.invoke(clazz);
        } catch (Exception e) {
            return false;
        }
    }

    public static NodeRegistry.RecordInfo analyzeRecord(Class<?> clazz, MethodHandles.Lookup lookup) {
        if (METHOD_GET_RECORD_COMPONENTS == null || METHOD_RECORD_COMPONENT_GET_TYPE == null) return null;
        try {
            Object[] comps = (Object[]) METHOD_GET_RECORD_COMPONENTS.invoke(clazz);
            if (comps == null) return null;

            String[] compNames = new String[comps.length];
            Type[] compTypes = new Type[comps.length];
            Class<?>[] compClasses = new Class<?>[comps.length];
            for (int i = 0; i < comps.length; i++) {
                compNames[i] = (String) METHOD_RECORD_COMPONENT_GET_NAME.invoke(comps[i]);
                compClasses[i] = (Class<?>) METHOD_RECORD_COMPONENT_GET_TYPE.invoke(comps[i]);
                compTypes[i] = (Type) METHOD_RECORD_COMPONENT_GET_GENERIC_TYPE.invoke(comps[i]);
            }

            Constructor<?> compCtor = clazz.getDeclaredConstructor(compClasses);
            try { compCtor.setAccessible(true); } catch (RuntimeException ignored) {}
            MethodHandle compCtorHandle = lookup.unreflectConstructor(compCtor);
            return new NodeRegistry.RecordInfo(clazz, compCtor, compCtorHandle,
                    comps.length, compNames, compClasses, compTypes);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        } catch (NoSuchMethodException e) {
            // Print error
            return null;
        }
    }

    private static Map<String, Integer> createArgIndexes(String[] argNames) {
        Map<String, Integer> argIndexes = new HashMap<>();
        for (int i = 0; i < argNames.length; i++) {
            argIndexes.put(argNames[i], i);
        }
        return argIndexes;
    }


    /// Lambda

    @SuppressWarnings("unchecked")
    static <T> Supplier<T> createLambdaConstructor(MethodHandles.Lookup lookup,
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
    static <T> T createLambdaArgsCreator(MethodHandles.Lookup lookup,
                                         MethodHandle creator,
                                         Class<T> funcType,
                                         int arity) {
        if (creator == null || funcType == null || arity <= 0 || arity > 5) return null;
        Class<?>[] params = new Class<?>[arity];
        for (int i = 0; i < arity; i++) params[i] = Object.class;
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
                return (T) MethodHandleProxies.asInterfaceInstance(funcType, adapted);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    static String capitalize(String name) {
        return name.length() > 1
                ? Character.toUpperCase(name.charAt(0)) + name.substring(1)
                : name.toUpperCase();
    }

    @SuppressWarnings("unchecked")
    static Function<Object, Object> createLambdaGetter(MethodHandles.Lookup lookup,
                                                       Class<?> clazz,
                                                       Field field) {
        MethodHandle getter = null;
        Class<?> type = field.getType();
        if (type == boolean.class || type == Boolean.class) {
            try {
                getter = lookup.findVirtual(clazz, "is" + capitalize(field.getName()),
                        MethodType.methodType(field.getType()));
            } catch (Exception ignored) {}
            if (getter == null) {
                try {
                    getter = lookup.findVirtual(clazz, "get" + capitalize(field.getName()),
                            MethodType.methodType(field.getType()));
                } catch (Exception ignored) {}
            }
        } else {
            try {
                getter = lookup.findVirtual(clazz, "get" + capitalize(field.getName()),
                        MethodType.methodType(field.getType()));
            } catch (Exception ignored) {}
        }
        if (getter == null) {
//            log.warn("Failed to find lambda getter for '{}' of {}", field.getName(), clazz);
            return null;
        }

        try {
            MethodType invokedType = MethodType.methodType(Function.class);
            MethodType samMethodType = MethodType.methodType(Object.class, Object.class);

            return (Function<Object, Object>) LambdaMetafactory.metafactory(
                    lookup,
                    "apply",
                    invokedType,
                    samMethodType,
                    getter,
                    getter.type()
            ).getTarget().invoke();
        } catch (Throwable e) {
//            log.warn("Failed to create lambda getter for '{}' of {}", field.getName(), clazz, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static BiConsumer<Object, Object> createLambdaSetter(MethodHandles.Lookup lookup,
                                                                Class<?> clazz,
                                                                Field field) {
        // Lambda-based setter does NOT support primitive types.
        if (field.getType().isPrimitive()) return null;

        MethodHandle setter = null;
        try {
            setter = lookup.findVirtual(clazz, "set" + capitalize(field.getName()),
                    MethodType.methodType(void.class, field.getType()));
        } catch (Exception e) {
//            log.warn("Failed to find lambda setter for '{}' of {}", field.getName(), clazz);
            return null;
        }

        try {
            MethodType invokedType = MethodType.methodType(BiConsumer.class);
            MethodType samMethodType = MethodType.methodType(void.class, Object.class, Object.class);

            return (BiConsumer<Object, Object>) LambdaMetafactory.metafactory(
                    lookup,
                    "accept",   // BiConsumer.accept(T, V)
                    invokedType,
                    samMethodType,          // erased signature:  (Object, Object)void
                    setter,                 // (T, V)void
                    setter.type()           // implement signature
            ).getTarget().invoke();
        } catch (Throwable e) {
//            log.warn("Failed to create lambda setter for '{}' of {}", field.getName(), clazz, e);
            return null;
        }
    }


    /// AnyOf

    public static NodeRegistry.AnyOfInfo analyzeAnyOf(Class<?> clazz, AnyOf ann) {
        AnyOf.Mapping[] mappings = ann.value();
        if (mappings == null || mappings.length == 0) {
            throw new JsonException("Empty mappings in @" + AnyOf.class.getName() + " of class " + clazz.getName());
        }

        boolean hasDiscriminator = !ann.key().isEmpty() || !ann.path().isEmpty();
        EnumSet<JsonType> enumSet = hasDiscriminator ? null : EnumSet.noneOf(JsonType.class);
        for (AnyOf.Mapping mapping : mappings) {
            Class<?> subClazz = mapping.value();
            if (!clazz.isAssignableFrom(subClazz)) {
                throw new JsonException("Mapping class " + subClazz.getName() + " in @" + AnyOf.class.getName() +
                        " is not assignable from " + clazz.getName());
            }
            if (hasDiscriminator) {
                if (mapping.when().length == 0) {
                    throw new JsonException("Given a discriminator but has empty 'when' in mapping " +
                            subClazz.getName() + " in @" + AnyOf.class.getName() + " of class " + clazz.getName());
                }
            } else {
                JsonType jt = JsonType.rawOf(mapping.value());
                if (jt.isUnknown()) {
                    throw new JsonException("Mapping raw JsonType must not be UNKNOWN in class " + clazz.getName());
                }
                if (!enumSet.add(jt)) {
                    throw new JsonException("Mapping duplicated raw JsonType " + jt + " in class " + subClazz.getName());
                }
            }
        }
        return new NodeRegistry.AnyOfInfo(clazz, mappings, ann.key(), ann.path(), ann.scope(), ann.onNoMatch());
    }


}
