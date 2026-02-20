package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
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
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;


@SuppressWarnings("unchecked")
public class ReflectUtil {

    /**
     * Flag indicating if the current JVM is running JDK 8.
     */
    public static final boolean IS_JDK8 = System.getProperty("java.version").startsWith("1.");


    /// POJO

    public static boolean  isPojoCandidate(Class<?> clazz) {
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

        return !isFrameworkPackage(clazz.getName());
    }


    public static NodeRegistry.PojoInfo analyzePojo(Class<?> clazz, boolean orElseThrow) {
        if (!isPojoCandidate(clazz)) {
            if (orElseThrow) throw new JsonException("Class " + clazz.getName() + " cannot be a POJO candidate");
            else return null;
        }

        MethodHandles.Lookup lookup = ROOT_LOOKUP;
        if (!IS_JDK8 && PRIVATE_LOOKUP_IN != null) {
            try {
                lookup = (MethodHandles.Lookup) PRIVATE_LOOKUP_IN.invoke(null, clazz, ROOT_LOOKUP);
            } catch (Exception e) {
                // log.debug("Failed to get 'privateLookupIn'", e);
            }
        }

        // Creator constructor (for final fields / record-style)
        NodeRegistry.CreatorInfo creatorInfo = null;
        try {
            creatorInfo = analyzeCreator(clazz, lookup);
        } catch (Exception e) {
            if (orElseThrow) throw e instanceof JsonException ? (JsonException) e : new JsonException(e);
            else return null;
        }

        // Fields
        Map<String, NodeRegistry.FieldInfo> fields = Sjf4jConfig.global().mapSupplier.create();
        Map<String, String> aliasMap = creatorInfo.aliasMap;
        Class<?> curClazz = clazz;
        Type curType = clazz;
        do {
            Field[] fds = curClazz.getDeclaredFields();
            try { AccessibleObject.setAccessible(fds, true); } catch (RuntimeException ignored) {}
            for (Field field : fds) {
                int mod = field.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) { continue; }

                MethodHandle getter = null;
                try {
                    getter = lookup.unreflectGetter(field);
                } catch (Exception e) {
                    // log.warn("Failed to get getter for '{}' of {}", field.getName(), curClazz, e);
                }
                Function<Object, Object> lambdaGetter = createLambdaGetter(lookup, curClazz, field);

                MethodHandle setter = null;
                BiConsumer<Object, Object> lambdaSetter = null;
                if (!Modifier.isFinal(mod)) {
                    try {
                        setter = lookup.unreflectSetter(field);
                    } catch (Exception e) {
                        // log.warn("Failed to get setter for '{}' of {}", field.getName(), curClazz, e);
                    }
                    lambdaSetter = createLambdaSetter(lookup, curClazz, field);

                }

                if (getter == null && lambdaGetter == null) {
                    // log.warn("No accessible getter or setter found for field '{}' of {}", field.getName(), curClazz);
                } else {
                    Type fieldType = Types.fieldType(curType, field);
                    NodeRegistry.FieldInfo fi = new NodeRegistry.FieldInfo(field.getName(),
                            fieldType, getter, lambdaGetter, setter, lambdaSetter);
                    String fieldName = getFieldName(field);
                    fields.put(fieldName, fi);

                    String[] aliases = getFieldAliases(field);
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

        return new NodeRegistry.PojoInfo(clazz, creatorInfo, fields, aliasFields);
    }

    public static String getFieldName(Field field) {
        String fname = getNodeProperty(field);
        if (fname != null && !fname.isEmpty()) return fname;
        fname = getJacksonProperty(field);
        if (fname != null && !fname.isEmpty()) return fname;
        fname = getFastjson2Property(field);
        if (fname != null && !fname.isEmpty()) return fname;
        return field.getName();
    }

    public static String[] getFieldAliases(Field field) {
        String[] aliases = getNodeAliases(field);
        if (aliases != null && aliases.length > 0) return aliases;
        aliases = getJacksonAliases(field);
        if (aliases != null && aliases.length > 0) return aliases;
        aliases = getFastjson2Aliases(field);
        if (aliases != null && aliases.length > 0) return aliases;
        return null;
    }

    public static String getParameterName(Parameter parameter) {
        String fname = getNodeProperty(parameter);
        if (fname != null && !fname.isEmpty()) return fname;
        fname = getJacksonProperty(parameter);
        if (fname != null && !fname.isEmpty()) return fname;
        fname = getFastjson2Property(parameter);
        if (fname != null && !fname.isEmpty()) return fname;
        if (parameter.isNamePresent()) return parameter.getName();
        return null;
    }

    public static String[] getParameterAliases(Parameter parameter) {
        String[] aliases = getNodeAliases(parameter);
        if (aliases != null && aliases.length > 0) return aliases;
        aliases = getJacksonAliases(parameter);
        if (aliases != null && aliases.length > 0) return aliases;
        aliases = getFastjson2Aliases(parameter);
        if (aliases != null && aliases.length > 0) return aliases;
        return null;
    }

    private static String getNodeProperty(AnnotatedElement element) {
        NodeProperty ann = element.getAnnotation(NodeProperty.class);
        if (ann != null) return ann.value();
        return null;
    }

    private static String[] getNodeAliases(AnnotatedElement element) {
        NodeProperty ann = element.getAnnotation(NodeProperty.class);
        if (ann != null) return ann.aliases();
        return null;
    }


    private static final String[] FRAMEWORK_PREFIX = {
            "java.", "javax.", "jakarta.", "jdk.",
            "com.fasterxml.jackson.", "com.google.gson."
    };

    private static boolean isFrameworkPackage(String clazzName) {
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


    public static NodeRegistry.CreatorInfo analyzeCreator(Class<?> clazz,
                                                          MethodHandles.Lookup lookup) {
        Executable creator = null;
        MethodHandle creatorHandle = null;
        String[] argNames = null;
        Type[] argTypes = null;
        Map<String, Integer> argIndexes = null;
        Map<String, String> aliasMap = null;
        MethodHandle noArgsCtor = null;
        Supplier<?> noArgsLambdaCtor = null;

        // 1. Find defined creator
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            if (ctor.isAnnotationPresent(NodeCreator.class)
                    || hasJacksonCreator(ctor) || hasFastjson2Creator(ctor)) {
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
            if (method.isAnnotationPresent(NodeCreator.class)
                    || hasJacksonCreator(method) || hasFastjson2Creator(method)) {
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

        // 2. Find Record
        NodeRegistry.RecordInfo recordInfo = analyzeRecord(clazz, lookup);
        if (recordInfo != null && (creator == null || creator == recordInfo.compCtor)) {
            if (creator == null) creator = recordInfo.compCtor;
            if (creatorHandle == null) creatorHandle = recordInfo.compCtorHandle;
//            argNames = recordInfo.getCompNames();
//            argTypes = recordInfo.getCompTypes();
//            argIndexes = createArgIndexes(argNames);
        }
        if (creator != null && creator.getParameterCount() > 0) {
            Parameter[] params = creator.getParameters();
            argTypes = creator.getGenericParameterTypes();
            argNames = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                String name = getParameterName(params[i]);
                if (name == null || name.isEmpty())
                    throw new JsonException("Missing parameter name for creator in " + clazz.getName() +
                            ": parameter index " + i + " (from 0). Use @NodeProperty or @JsonProperty on parameters.");
                argNames[i] = name;
            }
            argIndexes = createArgIndexes(argNames);
        }
        if (creator != null && creator.getParameterCount() > 0) {
            Parameter[] params = creator.getParameters();
            for (int i = 0; i < params.length; i++) {
                String[] aliases = getParameterAliases(params[i]);
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
                creator, creatorHandle, argNames, argTypes, argIndexes, aliasMap);
    }

    /// NodeValue

    public static NodeRegistry.ValueCodecInfo analyzeNodeValue(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(NodeValue.class)) return null;

        MethodHandle encodeHandle = null, decodeHandle = null, copyHandle = null;
        MethodHandles.Lookup lookup = ROOT_LOOKUP;
        if (!IS_JDK8) {
            try {
                lookup = (MethodHandles.Lookup) PRIVATE_LOOKUP_IN.invoke(null, clazz, ROOT_LOOKUP);
            } catch (Exception ignored) {}
        }

        Class<?> current = clazz;
        while (current != null && current != Object.class &&
                (encodeHandle == null || decodeHandle == null || copyHandle == null)) {
            for (Constructor<?> ctor : current.getDeclaredConstructors()) {
                // Decode
                if (ctor.isAnnotationPresent(NodeValue.class)) {
                    if (decodeHandle != null)
                        throw new JsonException("Multiple @Deocde definitions found in " + clazz.getName());
                    try {
                        decodeHandle = lookup.unreflectConstructor(ctor);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            for (Method m : current.getDeclaredMethods()) {
                if (m.isBridge()) continue;
                // Encode
                if (m.isAnnotationPresent(ValueToRaw.class)) {
                    if (encodeHandle != null)
                        throw new JsonException("Multiple @Enocde definitions found in " + clazz.getName());
                    if (Modifier.isStatic(m.getModifiers()))
                        throw new JsonException("Cannot use @Encode on static methods in " + clazz.getName());
                    if (current != clazz) {
                        Method override = findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        encodeHandle = lookup.unreflect(m);
                        continue;
                    } catch (IllegalAccessException e) {
                        throw new JsonException(e);
                    }
                }
                // Decode
                if (m.isAnnotationPresent(RawToValue.class)) {
                    if (decodeHandle != null)
                        throw new JsonException("Multiple @Deocde definitions found in " + clazz.getName());
                    if (!Modifier.isStatic(m.getModifiers()))
                        throw new JsonException("Must use @Decode on constructor or static methods in " + clazz.getName());
                    if (current != clazz) {
                        Method override = findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        decodeHandle = lookup.unreflect(m);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
                // Copy
                if (m.isAnnotationPresent(ValueCopy.class)) {
                    if (copyHandle != null)
                        throw new JsonException("Multiple @Copy definitions found in " + clazz.getName());
                    if (Modifier.isStatic(m.getModifiers()))
                        throw new JsonException("Cannot use @Copy on static methods in " + clazz.getName());
                    if (current != clazz) {
                        Method override = findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        copyHandle = lookup.unreflect(m);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }// for
            current = current.getSuperclass();
        }

        if (encodeHandle == null)
            throw new JsonException("Missing @Encode method in " + clazz.getName());
        if (encodeHandle.type().parameterCount() != 1) {
            throw new JsonException("@Encode method must have no parameters, but found " +
                    (encodeHandle.type().parameterCount() - 1) + ", in " + clazz.getName());
        }
        Class<?> encodeReturnClazz = encodeHandle.type().returnType();
        if (!NodeKind.of(encodeReturnClazz).isRaw())
            throw new JsonException("@Encode method return invalid type " + encodeReturnClazz.getName() +
                    " in " + clazz.getName() +
                    ". The return type must be a supported raw type (String, Number, Boolean, null, Map, or List).");

        if (decodeHandle == null)
            throw new JsonException("Missing @Decode method in " + clazz.getName());
        if (decodeHandle.type().parameterCount() != 1)
            throw new JsonException("@Decode method must have exactly one parameter, but found " +
                    decodeHandle.type().parameterCount());
        Class<?> decodeParamClazz = decodeHandle.type().parameterType(0);
        Class<?> decodeReturnClazz = decodeHandle.type().returnType();
        if (decodeParamClazz != encodeReturnClazz)
            throw new JsonException("@Decode method parameter type must match @Encode return type. " +
                    "Expected: " + encodeReturnClazz.getName() + ", Found: " + decodeParamClazz.getName());
        if (decodeReturnClazz != clazz)
            throw new JsonException("@Decode method return type must be " + clazz.getName() +
                    ", but found " + decodeReturnClazz.getName());

        if (copyHandle != null) {
            if (copyHandle.type().parameterCount() != 1)
                throw new JsonException("@Copy method must have no parameters, but found " +
                        (copyHandle.type().parameterCount() + 1));
            Class<?> copyReturnClazz = copyHandle.type().returnType();
            if (copyReturnClazz != clazz)
                throw new JsonException("@Copy method return type must be " + clazz.getName() +
                        ", but found " + copyReturnClazz.getName());
        }

        return new NodeRegistry.ValueCodecInfo(clazz, encodeReturnClazz, null,
                encodeHandle, decodeHandle, copyHandle);
    }

    private static Method findOverride(Method baseMethod, Class<?> clazz) {
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
    private static final Class<? extends Annotation> CLASS_JACKSON_JSON_CREATOR;
    private static final Class<? extends Annotation> CLASS_JACKSON_JSON_PROPERTY;
    private static final Class<? extends Annotation> CLASS_JACKSON_JSON_ALIAS;
    private static final Class<? extends Annotation> CLASS_FASTJSON2_JSON_CREATOR;
    private static final Class<? extends Annotation> CLASS_FASTJSON2_JSON_FIELD;
    static {
        Class<?> jacksonJsonCreator = null;
        Class<?> jacksonJsonProperty = null;
        Class<?> jacksonJsonAlias = null;
        Class<?> fastjson2JsonCreator = null;
        Class<?> fastJson2JsonField = null;
        try {
            jacksonJsonCreator = Class.forName("com.fasterxml.jackson.annotation.JsonCreator");
        } catch (ClassNotFoundException ignore) {}
        try {
            jacksonJsonProperty = Class.forName("com.fasterxml.jackson.annotation.JsonProperty");
        } catch (ClassNotFoundException ignore) {}
        try {
            jacksonJsonAlias = Class.forName("com.fasterxml.jackson.annotation.JsonAlias");
        } catch (ClassNotFoundException ignore) {}
        try {
            fastjson2JsonCreator = Class.forName("com.alibaba.fastjson2.annotation.JSONCreator");
        } catch (ClassNotFoundException ignore) {}
        try {
            fastJson2JsonField = Class.forName("com.alibaba.fastjson2.annotation.JSONField");
        } catch (ClassNotFoundException ignore) {}

        CLASS_JACKSON_JSON_CREATOR = (Class<? extends Annotation>) jacksonJsonCreator;
        CLASS_JACKSON_JSON_PROPERTY = (Class<? extends Annotation>) jacksonJsonProperty;
        CLASS_JACKSON_JSON_ALIAS = (Class<? extends Annotation>) jacksonJsonAlias;
        CLASS_FASTJSON2_JSON_CREATOR = (Class<? extends Annotation>) fastjson2JsonCreator;
        CLASS_FASTJSON2_JSON_FIELD = (Class<? extends Annotation>) fastJson2JsonField;
    }

    private static boolean hasJacksonCreator(AccessibleObject obj) {
        if (CLASS_JACKSON_JSON_CREATOR == null) return false;
        try {
            return obj.isAnnotationPresent(CLASS_JACKSON_JSON_CREATOR);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasFastjson2Creator(AccessibleObject obj) {
        if (CLASS_FASTJSON2_JSON_CREATOR == null) return false;
        try {
            return obj.isAnnotationPresent(CLASS_FASTJSON2_JSON_CREATOR);
        } catch (Exception e) {
            return false;
        }
    }

    private static String getJacksonProperty(AnnotatedElement element) {
        if (CLASS_JACKSON_JSON_PROPERTY == null) return null;
        try {
            Annotation ann = element.getAnnotation(CLASS_JACKSON_JSON_PROPERTY);
            if (ann == null) return null;
            Method valueMethod = ann.annotationType().getMethod("value");
            Object value = valueMethod.invoke(ann);
            return value instanceof String ? (String) value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getFastjson2Property(AnnotatedElement element) {
        if (CLASS_FASTJSON2_JSON_FIELD  == null) return null;
        try {
            Annotation ann = element.getAnnotation(CLASS_FASTJSON2_JSON_FIELD);
            if (ann == null) return null;
            Method valueMethod = ann.annotationType().getMethod("name");
            Object value = valueMethod.invoke(ann);
            return value instanceof String ? (String) value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String[] getJacksonAliases(AnnotatedElement element) {
        if (CLASS_JACKSON_JSON_ALIAS  == null) return null;
        try {
            Annotation ann = element.getAnnotation(CLASS_JACKSON_JSON_ALIAS);
            if (ann == null) return null;
            Method valueMethod = ann.annotationType().getMethod("value");
            Object value = valueMethod.invoke(ann);
            return value instanceof String[] ? (String[]) value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String[] getFastjson2Aliases(AnnotatedElement element) {
        if (CLASS_FASTJSON2_JSON_FIELD  == null) return null;
        try {
            Annotation ann = element.getAnnotation(CLASS_FASTJSON2_JSON_FIELD);
            if (ann == null) return null;
            Method method = ann.annotationType().getMethod("alternateNames");
            Object aliases = method.invoke(ann);
            return aliases instanceof String[] ? (String[]) aliases : null;
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




}
