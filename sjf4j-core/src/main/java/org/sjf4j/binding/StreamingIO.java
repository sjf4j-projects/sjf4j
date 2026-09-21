package org.sjf4j.binding;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.CreatorInfo;
import org.sjf4j.node.CreatorState;
import org.sjf4j.value.ValueInfo;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.OneOfInfo;
import org.sjf4j.node.FieldInfo;
import org.sjf4j.node.TypeInfo;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.Set;

/**
 * Streaming read/write helpers used by facade implementations.
 */
public final class StreamingIO {


    /*
     * --------------------------------------------------------------
     * Reading
     * --------------------------------------------------------------
     */

    static final Object UNSET = new Object();

    /**
     * Reads one node from streaming reader into target type using streaming context.
     */
    public static Object readNode(StreamingReader reader, Type nodeType, StreamingContext context) {
        Class<?> nodeBoxed = Types.rawBox(nodeType);
        TypeInfo ti = TypeRegistry.registerTypeInfo(nodeBoxed);
        return readNode(reader, nodeType, nodeBoxed, ti, context);
    }

    /**
     * Reads next token and dispatches to typed node readers.
     */
    static Object readNode(StreamingReader reader, Type nodeType, Class<?> nodeBoxed, TypeInfo ti,
                           StreamingContext context) {
        try {
            if (ti.oneOfInfo != null) {
                return OneOfIO.readOneOf(reader, ti.oneOfInfo, context);
            }
            if (nodeBoxed == Object.class) {
                return readRawNode(reader);
            }
            if (ti.isNodeValue()) {
                String valueFormat = context.defaultValueFormat(nodeBoxed);
                ValueInfo vci = ti.getNodeValueInfo(valueFormat);
                if (vci == null) {
                    throw new BindingException("no ValueCodec registered for type '" + nodeBoxed.getName() +
                            "' with format '" + valueFormat + "'");
                }
                return readValueWithCodec(reader, nodeType, nodeBoxed, vci, context);
            }

            StreamingReader.Token token = reader.peekToken();
            switch (token) {
                case START_OBJECT:
                    return readObject(reader, nodeType, nodeBoxed, ti, context);
                case START_ARRAY:
                    return readArray(reader, nodeType, nodeBoxed, ti, context);
                case STRING:
                    return readString(reader, nodeBoxed, ti, context);
                case NUMBER:
                    return readNumber(reader, nodeBoxed, ti, context);
                case BOOLEAN:
                    return readBoolean(reader, nodeBoxed, ti, context);
                case NULL:
                    return readNull(reader, nodeBoxed, ti, context);
                default:
                    throw new BindingException("unexpected token '" + token + "'");
            }
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to read streaming into '" + nodeType + "'", e);
        }
    }


    static Object readRawNode(StreamingReader reader) throws IOException {
        switch (reader.peekToken()) {
            case START_OBJECT:
                return readRawObject(reader);
            case START_ARRAY:
                return readRawArray(reader);
            case STRING:
                return reader.nextString();
            case NUMBER:
                return reader.nextNumber();
            case BOOLEAN:
                return reader.nextBoolean();
            case NULL:
                reader.nextNull();
                return null;
            default:
                throw new BindingException("unexpected token '" + reader.peekToken() + "'");
        }
    }


    static Map<String, Object> readRawObject(StreamingReader reader) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        reader.startObject();
        while (!reader.nextIfObjectEnd()) {
            String key = reader.nextName();
            Object value = readRawNode(reader);
            map.put(key, value);
        }
        return map;
    }

    static List<Object> readRawArray(StreamingReader reader) throws IOException {
        List<Object> list = new ArrayList<>();
        reader.startArray();
        while (!reader.nextIfArrayEnd()) {
            Object value = readRawNode(reader);
            list.add(value);
        }
        return list;
    }

    /**
     * Reads null token and decodes via value codec when needed.
     */
    static Object readNull(StreamingReader reader, Class<?> nodeBoxed, TypeInfo ti,
                           StreamingContext context) throws IOException {
        reader.nextNull();
        if (nodeBoxed == Optional.class) {
            return Optional.empty();
        }
        return null;
    }

    /**
     * Reads boolean token into target type.
     */
    static Object readBoolean(StreamingReader reader, Class<?> nodeBoxed, TypeInfo ti,
                              StreamingContext context) throws IOException {
        if (nodeBoxed == Boolean.class) {
            return reader.nextBooleanValue();
        }

        if (ti.isNodeValue()) {
            String valueFormat = context.defaultValueFormat(nodeBoxed);
            ValueInfo vci = ti.getNodeValueInfo(valueFormat);
            if (vci != null) {
                Boolean raw = reader.nextBooleanValue();
                return vci.rawToValue(raw);
            }
        }
        throw new BindingException("cannot read boolean value into type '" + nodeBoxed + "'");
    }

    /**
     * Reads number token into target numeric or codec type.
     */
    static Object readNumber(StreamingReader reader, Class<?> nodeBoxed, TypeInfo ti,
                             StreamingContext context) throws IOException {
        if (nodeBoxed == Number.class) {
            return reader.nextNumber();
        }
        if (nodeBoxed == Integer.class) return reader.nextIntValue();
        if (nodeBoxed == Long.class) return reader.nextLongValue();
        if (nodeBoxed == Float.class) return reader.nextFloatValue();
        if (nodeBoxed == Double.class) return reader.nextDoubleValue();
        if (nodeBoxed == Short.class) return reader.nextShortValue();
        if (nodeBoxed == Byte.class) return reader.nextByteValue();
        if (nodeBoxed == BigInteger.class) return reader.nextBigInteger();
        if (nodeBoxed == BigDecimal.class) return reader.nextBigDecimal();
        if (nodeBoxed.isEnum()) return enumByOrdinal(nodeBoxed, reader.nextIntValue());

        if (ti.isNodeValue()) {
            String valueFormat = context.defaultValueFormat(nodeBoxed);
            ValueInfo vci = ti.getNodeValueInfo(valueFormat);
            if (vci != null) {
                Number n = reader.nextNumber();
                return vci.rawToValue(n);
            }
        }
        throw new BindingException("cannot read number value into type '" + nodeBoxed + "'");
    }

    /**
     * Reads string token into target scalar or codec type.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object readString(StreamingReader reader, Class<?> nodeBoxed, TypeInfo ti,
                             StreamingContext context) throws IOException {
        if (nodeBoxed == String.class) {
            return reader.nextString();
        }
        if (nodeBoxed == Character.class) {
            String s = reader.nextString();
            return !s.isEmpty() ? s.charAt(0) : null;
        }
        if (nodeBoxed.isEnum()) {
            String s = reader.nextString();
            return Enum.valueOf((Class<? extends Enum>) nodeBoxed, s);
        }
        if (ti.isNodeValue()) {
            String valueFormat = context.defaultValueFormat(nodeBoxed);
            ValueInfo vci = ti.getNodeValueInfo(valueFormat);
            if (vci != null) {
                String raw = reader.nextString();
                return vci.rawToValue(raw);
            }
        }
        throw new BindingException("cannot read string value into type '" + nodeBoxed + "'");
    }

    /**
     * Reads object token into Map/JsonObject/POJO target.
     */
    static Object readObject(StreamingReader reader, Type nodeType, Class<?> nodeBoxed, TypeInfo ti,
                             StreamingContext context) throws IOException {
        if (Map.class.isAssignableFrom(nodeBoxed)) {
            Type valueType = Types.resolveTypeArgument(nodeType, Map.class, 1);
            Class<?> valueClazz = Types.rawBox(valueType);
            return readMap(reader, nodeBoxed, valueType, valueClazz,
                    TypeRegistry.registerTypeInfo(valueClazz), context);
        }

        if (nodeBoxed == JsonObject.class) {
            return new JsonObject(readRawObject(reader));
        }

        if (!nodeBoxed.isInterface() && Modifier.isAbstract(nodeBoxed.getModifiers())) {
            throw new BindingException("cannot read object value into abstract type '" + nodeBoxed.getName() + "'");
        }

        if (ti.isNodeValue()) {
            String valueFormat = context.defaultValueFormat(nodeBoxed);
            ValueInfo vci = ti.getNodeValueInfo(valueFormat);
            if (vci != null) {
                Map<String, Object> map = readRawObject(reader);
                return vci.rawToValue(map);
            }
        }

        PojoInfo pi = ti.pojoInfo;
        if (pi != null && !pi.isJajo) {
            return readPojo(reader, nodeType, nodeBoxed, pi, context);
        }

        throw new BindingException("cannot read object value into type '" + nodeBoxed + "'");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumByOrdinal(Class<?> enumClass, int ordinal) {
        Enum[] values = ((Class<? extends Enum>) enumClass).getEnumConstants();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new BindingException("enum ordinal '" + ordinal + "' out of range for type '" + enumClass.getName() + "'");
        }
        return values[ordinal];
    }


    static Object readPojo(StreamingReader reader, Type pojoType, Class<?> pojoBoxed,
                           PojoInfo pi, StreamingContext context) throws IOException {
        CreatorInfo ci = pi.creatorInfo;
        boolean hasParentOneOf = pi.hasParentScopeOneOf;

        // Fast path: no-args POJO + no parent-scope OneOf
        if (!hasParentOneOf && ci.hasNoArgsCreator()
                && (ci.argNames == null || ci.argNames.length == 0)) {

            Object pojo = ci.newPojoNoArgs();
            Map<String, Object> dynamicMap = null;

            reader.startObject();
            while (!reader.nextIfObjectEnd()) {
                String key = reader.nextName();
                FieldInfo fi = pi.aliasProperties != null ? pi.aliasProperties.get(key) : pi.properties.get(key);
                if (fi != null) {
                    fi.binder.bind(reader, pojo, pojoType, pojoBoxed, context);
                    continue;
                }

                if (pi.isJojo && pi.readDynamic) {
                    if (dynamicMap == null) {
                        dynamicMap = new LinkedHashMap<>();
                    }
                    dynamicMap.put(key, readRawNode(reader));
                    continue;
                }

                reader.skipNext();
            }

            if (pi.isJojo) {
                ((JsonObject) pojo)._dynamicMap(dynamicMap);
            }
            return pojo;
        }


        // Slow path: Creator / parent-OneOf path
        CreatorState state = new CreatorState(ci);

        FieldInfo deferredParentOneOfFi = null;
        Object deferredParentOneOfRaw = null;
        String parentOneOfKey = null;
        Object parentOneOfValue = UNSET;

        reader.startObject();
        while (!reader.nextIfObjectEnd()) {
            String key = reader.nextName();
            int argIdx = ci.getArgIndexOrAlias(key);
            if (argIdx >= 0) {
                Type argType = Types.resolveMemberType(pojoType, pojoBoxed, ci.argTypes[argIdx]);
                Class<?> argBoxed = Types.rawBox(argType);

                TypeInfo argTi = TypeRegistry.registerTypeInfo(argBoxed);
                ValueInfo argVci = ci.argValueCodecs[argIdx];
                if (argVci == null && argTi.isNodeValue()) {
                    String valueFormat = context.defaultValueFormat(argBoxed);
                    argVci = argTi.getNodeValueInfo(valueFormat);
                }

                Object argValue;
                if (argTi.oneOfInfo == null && argVci != null) {
                    argValue = readValueWithCodec(reader, argType, argBoxed, argVci, context);
                } else {
                    argValue = readNode(reader, argType, argBoxed, argTi, context);
                }
                state.acceptCtorArg(argIdx, argValue);

                // The constructor argument itself may be the discriminator of a previously encountered PARENT OneOf field.
                if (parentOneOfKey != null && parentOneOfKey.equals(key)) {
                    parentOneOfValue = argValue;
                }
                continue;
            }

            // Known field
            FieldInfo fi = pi.aliasProperties != null ? pi.aliasProperties.get(key) : pi.properties.get(key);
            if (fi != null) {
                OneOfInfo fieldOneOf = fi.oneOfInfo;

                // PARENT-scope OneOf field
                if (hasParentOneOf && fieldOneOf != null && fieldOneOf.scope == OneOf.Scope.PARENT) {
                    if (!fieldOneOf.path.isEmpty()) {
                        throw new BindingException("oneOf scope=PARENT does not support path discriminator");
                    }

                    String parentKey = fieldOneOf.key;
                    if (parentOneOfKey == null) {
                        parentOneOfKey = parentKey;
                    } else if (!parentOneOfKey.equals(parentKey)) {
                        throw new BindingException("at most one OneOf parent discriminator key is supported per class");
                    }
                    Class<?> targetClazz = fieldOneOf.matchByWhen(parentOneOfValue == UNSET ? null : parentOneOfValue);

                    if (targetClazz != null) {
                        Object value = readNode(reader, targetClazz, context);
                        if (state.isCreated()) {
                            fi.invokeSetterIfPresent(state.pojo(), value);
                        } else {
                            state.bufferProperty(fi, value);
                        }
                        continue;
                    }

                    // The discriminator has not appeared yet.
                    if (deferredParentOneOfFi != null) {
                        throw new BindingException("at most one OneOf field with scope=PARENT is supported per class");
                    }
                    deferredParentOneOfFi = fi;
                    deferredParentOneOfRaw = readRawNode(reader);

                    continue;
                }

                // Known PARENT discriminator field
                if (parentOneOfKey != null && parentOneOfKey.equals(key)) {
                    Object value = readFieldValue(reader, fi, pojoType, pojoBoxed, context);
                    parentOneOfValue = value;
                    if (state.isCreated()) {
                        fi.invokeSetter(state.pojo(), value);
                    } else {
                        state.bufferProperty(fi, value);
                    }
                    continue;
                }

                // POJO already exists
                if (state.isCreated()) {
                    fi.binder.bind(reader, state.pojo(), pojoType, pojoBoxed, context);
                    continue;
                }

                // POJO does not exist yet
                if (fi.hasSetter()) {
                    Object value = readFieldValue(reader, fi, pojoType, pojoBoxed, context);
                    state.bufferProperty(fi, value);
                } else {
                    reader.skipNext();
                }

                continue;
            }

            // Dynamic JsonObject property
            if (pi.isJojo && pi.readDynamic) {
                Object value = readRawNode(reader);
                state.acceptDynamic(key, value);
                if (parentOneOfKey != null && parentOneOfKey.equals(key)) {
                    parentOneOfValue = value;
                }
                continue;
            }

            // Unknown property
            reader.skipNext();

        }// while

        Object pojo = state.finish();
        if (deferredParentOneOfFi != null) {
            OneOfInfo oneOfInfo = deferredParentOneOfFi.oneOfInfo;
            String parentKey = oneOfInfo.key;
            if (parentOneOfValue == UNSET) {
                Object discriminator = null;
                FieldInfo parentFi = pi.aliasProperties != null
                        ? pi.aliasProperties.get(parentKey) : pi.properties.get(parentKey);
                if (parentFi != null) {
                    discriminator = parentFi.invokeGetter(pojo);
                } else if (pi.isJojo) {
                    discriminator = ((JsonObject) pojo).getNode(parentKey);
                }
                if (discriminator != null) {
                    parentOneOfValue = discriminator;
                }
            }

            Class<?> targetClazz = oneOfInfo.matchByWhen(parentOneOfValue == UNSET ? null : parentOneOfValue);
            Object value;
            if (targetClazz != null) {
                value = context.nodeBinder.readNode(deferredParentOneOfRaw, targetClazz);
            } else if (oneOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
                value = null;
            } else {
                throw new BindingException("oneOf discriminator has no matching mapping: key='" +
                        oneOfInfo.key + "', value='" + (parentOneOfValue == UNSET ? null : parentOneOfValue) + "'");
            }
            deferredParentOneOfFi.invokeSetterIfPresent(pojo, value);
        }

        return pojo;
    }



    static Object readFieldValue(StreamingReader reader, FieldInfo fi, Type ownerType,
                                 Class<?> ownerBoxed, StreamingContext context) throws IOException {
        Type fieldType = fi.type;
        Class<?> fieldBoxed = fi.boxed;
        if (fi.genericDependent) {
            fieldType = Types.resolveMemberType(ownerType, ownerBoxed, fi.type);
            fieldBoxed = Types.rawBox(fieldType);
        }

        if (fi.oneOfInfo != null) {
            return OneOfIO.readOneOf(reader, fi.oneOfInfo, context);
        }

        if (fi.valueInfo != null) {
            return readValueWithCodec(reader, fieldType, fieldBoxed, fi.valueInfo, context);
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(fieldBoxed);
        return readNode(reader, fieldType, fieldBoxed, ti, context);
    }


    /**
     * Reads array token into List/JsonArray/array/Set target.
     */
    static Object readArray(StreamingReader reader, Type nodeType, Class<?> nodeBoxed, TypeInfo ti,
                            StreamingContext context) throws IOException {
        if (List.class.isAssignableFrom(nodeBoxed)) {
            Type elementType = Types.resolveTypeArgument(nodeType, List.class, 0);
            Class<?> elementBoxed = Types.rawBox(elementType);
            return readList(reader, nodeBoxed, elementType, elementBoxed,
                    TypeRegistry.registerTypeInfo(elementBoxed), context);
        }

        if (nodeBoxed == JsonArray.class) {
            return new JsonArray(readRawArray(reader));
        }

        if (Set.class.isAssignableFrom(nodeBoxed)) {
            Type valueType = Types.resolveTypeArgument(nodeType, Set.class, 0);
            Class<?> valueBoxed = Types.rawBox(valueType);
            return readSet(reader, nodeBoxed, valueType, valueBoxed,
                    TypeRegistry.registerTypeInfo(valueBoxed), context);
        }

        if (nodeBoxed.isArray()) {
            Class<?> componentClazz = nodeBoxed.getComponentType();
            Class<?> componentBoxed = Types.box(componentClazz);
            return readJavaArray(reader, nodeBoxed, componentClazz, componentBoxed,
                    TypeRegistry.registerTypeInfo(componentClazz), context);
        }

        if (JsonArray.class.isAssignableFrom(nodeBoxed)) {
            JsonArray ja = (JsonArray) ti.pojoInfo.creatorInfo.forceNewPojo();
            Class<?> elementClazz = ja.elementClass();
            TypeInfo elementTi = TypeRegistry.registerTypeInfo(elementClazz);
            reader.startArray();
            while (!reader.nextIfArrayEnd()) {
                Object value = readNode(reader, elementClazz, elementClazz, elementTi, context);
                ja.add(value);
            }
            return ja;
        }

        if (ti.isNodeValue()) {
            String valueFormat = context.defaultValueFormat(nodeBoxed);
            ValueInfo vci = ti.getNodeValueInfo(valueFormat);
            if (vci != null) {
                List<Object> list = readRawArray(reader);
                return vci.rawToValue(list);
            }
        }

        throw new BindingException("cannot read array value into type '" + nodeBoxed + "'");
    }

    static Object readValueWithCodec(StreamingReader reader, Type valueType, Class<?> valueBoxed, ValueInfo valueInfo,
                                     StreamingContext context) throws IOException {
        if (reader.nextIfNull()) {
            if (valueBoxed == Optional.class) {
                return Optional.empty();
            }
            return null;
        }

        Class<?> rawClazz = valueInfo.rawClazz;
        if (rawClazz == Object.class) {
            return valueInfo.rawToValue(readRawNode(reader));
        }
        if (rawClazz == Map.class) {
            return valueInfo.rawToValue(readRawObject(reader));
        }
        if (rawClazz == List.class) {
            return valueInfo.rawToValue(readRawArray(reader));
        }
        if (rawClazz == String.class) {
            return valueInfo.rawToValue(reader.nextString());
        }
        if (rawClazz == Boolean.class) {
            return valueInfo.rawToValue(reader.nextBooleanValue());
        }
        if (rawClazz == Integer.class) {
            return valueInfo.rawToValue(reader.nextIntValue());
        }
        if (rawClazz == Long.class) {
            return valueInfo.rawToValue(reader.nextLongValue());
        }
        if (rawClazz == Double.class) {
            return valueInfo.rawToValue(reader.nextDoubleValue());
        }
        if (rawClazz == Float.class) {
            return valueInfo.rawToValue(reader.nextFloatValue());
        }
        if (rawClazz == Short.class) {
            return valueInfo.rawToValue(reader.nextShortValue());
        }
        if (rawClazz == Byte.class) {
            return valueInfo.rawToValue(reader.nextByteValue());
        }
        if (rawClazz == BigInteger.class) {
            return valueInfo.rawToValue(reader.nextBigInteger());
        }
        if (rawClazz == BigDecimal.class) {
            return valueInfo.rawToValue(reader.nextBigDecimal());
        }
        if (rawClazz == Number.class) {
            return valueInfo.rawToValue(reader.nextNumber());
        }
        throw new BindingException("cannot read value with ValueCodec into type '" + rawClazz.getName() + "'");

    }

    static Map<String, Object> readMapOrNull(StreamingReader reader, Class<?> mapClazz, Type valueType, Class<?> valueBoxed,
                                       TypeInfo ti, StreamingContext context) throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        return readMap(reader, mapClazz, valueType, valueBoxed, ti, context);
    }

    /**
     * Reads object token into map with typed values.
     */
    static Map<String, Object> readMap(StreamingReader reader, Class<?> mapClazz, Type valueType, Class<?> valueBoxed,
                                       TypeInfo ti, StreamingContext context) throws IOException {
        Map<String, Object> map = (mapClazz == Object.class || mapClazz == Map.class || mapClazz == LinkedHashMap.class)
                ? new LinkedHashMap<>()
                : TypeRegistry.newMapContainer(mapClazz, 0, false);
        reader.startObject();
        while (!reader.nextIfObjectEnd()) {
            String key = reader.nextName();
            Object value = readNode(reader, valueType, valueBoxed, ti, context);
            map.put(key, value);
        }
        return map;
    }


    static List<Object> readListOrNull(StreamingReader reader, Class<?> listClazz, Type elementType, Class<?> elementBoxed,
                                 TypeInfo ti, StreamingContext context) throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        return readList(reader, listClazz, elementType, elementBoxed, ti, context);
    }

    /**
     * Reads array token into list with typed elements.
     */
    static List<Object> readList(StreamingReader reader, Class<?> listClazz, Type elementType, Class<?> elementBoxed,
                                 TypeInfo ti, StreamingContext context) throws IOException {
        List<Object> list = (listClazz == Object.class || listClazz == List.class || listClazz == ArrayList.class)
                ? new ArrayList<>()
                : TypeRegistry.newListContainer(listClazz, 0, false);
        reader.startArray();
        while (!reader.nextIfArrayEnd()) {
            Object value = readNode(reader, elementType, elementBoxed, ti, context);
            list.add(value);
        }
        return list;
    }


    static Set<Object> readSetOrNull(StreamingReader reader, Class<?> setClazz, Type valueType, Class<?> valueClazz,
                                     TypeInfo ti, StreamingContext context) throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        return readSet(reader, setClazz, valueType, valueClazz, ti, context);
    }

    /**
     * Reads array token into set with typed elements.
     */
    static Set<Object> readSet(StreamingReader reader, Class<?> setClazz, Type valueType, Class<?> valueClazz,
                               TypeInfo ti, StreamingContext context) throws IOException {
        Set<Object> set = (setClazz == Object.class || setClazz == Set.class || setClazz == LinkedHashSet.class)
                ? new LinkedHashSet<>()
                : TypeRegistry.newSetContainer(setClazz, 0, false);
        reader.startArray();
        while (!reader.nextIfArrayEnd()) {
            Object value = readNode(reader, valueType, valueClazz, ti, context);
            set.add(value);
        }
        return set;
    }

    static Object readJavaArrayOrNull(StreamingReader reader, Class<?> arrClazz, Class<?> componentClazz, Class<?> componentBoxed,
                                      TypeInfo ti, StreamingContext context) throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        return readJavaArray(reader, arrClazz, componentClazz, componentBoxed, ti, context);
    }

    @SuppressWarnings("SuspiciousSystemArraycopy")
    static Object readJavaArray(StreamingReader reader, Class<?> arrClazz, Class<?> componentClazz, Class<?> componentBoxed,
                                TypeInfo ti, StreamingContext context) throws IOException {
        Object array = null;
        int size = 0;
        reader.startArray();
        while (!reader.nextIfArrayEnd()) {
            if (array == null) {
                array = Array.newInstance(componentClazz, 8);
            } else if (size == Array.getLength(array)) {
                int capacity = size << 1;
                Object expanded = Array.newInstance(componentClazz, capacity);
                System.arraycopy(array, 0, expanded, 0, size);
                array = expanded;
            }
            Array.set(array, size++, readNode(reader, componentClazz, componentBoxed, ti, context));
        }

        if (array == null) {
            return Array.newInstance(componentClazz, 0);
        }
        if (size == Array.getLength(array)) {
            return array;
        }

        Object exact = Array.newInstance(componentClazz, size);
        System.arraycopy(array, 0, exact, 0, size);
        return exact;
    }


    /*
     * --------------------------------------------------------------
     * Writing
     * --------------------------------------------------------------
     */


//    /**
//     * Writes one node to streaming writer using instance-level value formats.
//     */
//    public static void writeNode(StreamingWriter writer, Object node, StreamingContext context) throws IOException {
//        try {
//            if (node == null) {
//                writer.writeNull();
//                return;
//            }
//
//            if (node instanceof String) {
//                writer.writeStringValue((String) node);
//                return;
//            }
//            if (node instanceof Number) {
//                writer.writeNumberValue((Number) node);
//                return;
//            }
//            if (node instanceof Boolean) {
//                writer.writeBooleanValue((Boolean) node);
//                return;
//            }
//
//            if (node instanceof Map) {
//                writer.startObject();
//                int cnt = 0;
//                for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
//                    Object value = entry.getValue();
//                    if (value == null && !context.includeNulls) continue;
//                    if (cnt++ > 0) writer.separateProperty();
//                    String key = entry.getKey().toString();
//                    writer.writeName(key);
//                    writeNode(writer, value, context);
//                }
//                writer.endObject();
//                return;
//            }
//
//            if (node instanceof List) {
//                writer.startArray();
//                List<?> list = (List<?>) node;
//                if (list instanceof RandomAccess) {
//                    for (int i = 0, size = list.size(); i < size; i++) {
//                        if (i > 0) writer.separateElement();
//                        writeNode(writer, list.get(i), context);
//                    }
//                } else {
//                    boolean first = true;
//                    for (Object value : list) {
//                        if (first) {
//                            first = false;
//                        } else {
//                            writer.separateElement();
//                        }
//                        writeNode(writer, value, context);
//                    }
//                }
//                writer.endArray();
//                return;
//            }
//
//            Class<?> rawClazz = node.getClass();
//            if (rawClazz == JsonObject.class) {
//                writer.startObject();
//                int cnt = 0;
//                for (Map.Entry<String, Object> entry : ((JsonObject) node).entrySet()) {
//                    Object value = entry.getValue();
//                    if (value == null && !context.includeNulls) continue;
//                    if (cnt++ > 0) writer.separateProperty();
//                    writer.writeName(entry.getKey());
//                    writeNode(writer, value, context);
//                }
//                writer.endObject();
//                return;
//            }
//
//            if (node instanceof JsonArray) {
//                writer.startArray();
//                JsonArray ja = (JsonArray) node;
//                for (int i = 0, len = ja.size(); i < len; i++) {
//                    if (i > 0) writer.separateElement();
//                    writeNode(writer, ja.getNode(i), context);
//                }
//                writer.endArray();
//                return;
//            }
//
//            if (rawClazz == boolean[].class) {
//                writer.startArray();
//                boolean[] array = (boolean[]) node;
//                for (int i = 0; i < array.length; i++) {
//                    if (i > 0) writer.separateElement();
//                    writer.writeBooleanValue(array[i]);
//                }
//                writer.endArray();
//                return;
//            }
//            if (rawClazz == byte[].class) {
//                writer.startArray();
//                byte[] array = (byte[]) node;
//                for (int i = 0; i < array.length; i++) {
//                    if (i > 0) writer.separateElement();
//                    writer.writeByteValue(array[i]);
//                }
//                writer.endArray();
//                return;
//            }
//            if (rawClazz == short[].class) {
//                writer.startArray();
//                short[] array = (short[]) node;
//                for (int i = 0; i < array.length; i++) {
//                    if (i > 0) writer.separateElement();
//                    writer.writeShortValue(array[i]);
//                }
//                writer.endArray();
//                return;
//            }
//            if (rawClazz == int[].class) {
//                writer.startArray();
//                int[] array = (int[]) node;
//                for (int i = 0; i < array.length; i++) {
//                    if (i > 0) writer.separateElement();
//                    writer.writeIntValue(array[i]);
//                }
//                writer.endArray();
//                return;
//            }
//            if (rawClazz == long[].class) {
//                writer.startArray();
//                long[] array = (long[]) node;
//                for (int i = 0; i < array.length; i++) {
//                    if (i > 0) writer.separateElement();
//                    writer.writeLongValue(array[i]);
//                }
//                writer.endArray();
//                return;
//            }
//            if (rawClazz == float[].class) {
//                writer.startArray();
//                float[] array = (float[]) node;
//                for (int i = 0; i < array.length; i++) {
//                    if (i > 0) writer.separateElement();
//                    writer.writeFloatValue(array[i]);
//                }
//                writer.endArray();
//                return;
//            }
//            if (rawClazz == double[].class) {
//                writer.startArray();
//                double[] array = (double[]) node;
//                for (int i = 0; i < array.length; i++) {
//                    if (i > 0) writer.separateElement();
//                    writer.writeDoubleValue(array[i]);
//                }
//                writer.endArray();
//                return;
//            }
//            if (node instanceof Object[]) {
//                Object[] array = (Object[]) node;
//                writer.startArray();
//                for (int i = 0, len = array.length; i < len; i++) {
//                    if (i > 0) writer.separateElement();
//                    writeNode(writer, array[i], context);
//                }
//                writer.endArray();
//                return;
//            }
//
//            if (node instanceof Set) {
//                writer.startArray();
//                boolean veryStart = true;
//                for (Object v : (Set<?>) node) {
//                    if (veryStart) veryStart = false;
//                    else writer.separateElement();
//                    writeNode(writer, v, context);
//                }
//                writer.endArray();
//                return;
//            }
//
//            if (node instanceof Character) {
//                writer.writeCharValue((Character) node);
//                return;
//            }
//            if (node instanceof Enum) {
//                writer.writeStringValue(((Enum<?>) node).name());
//                return;
//            }
//
//            TypeInfo ti = TypeRegistry.registerTypeInfo(rawClazz);
//            String valueFormat = context.defaultValueFormat(rawClazz);
//            NodeValueInfo vci = ti.getNodeValueInfo(valueFormat);
////            if (vci == null) {
////                vci = TypeRegistry.resolveValueCodecForRuntimeClass(rawClazz, valueFormat);
////            }
//            if (vci != null) {
//                Object raw = vci.valueToRaw(node);
//                writeNode(writer, raw, context);
//                return;
//            }
//
//            PojoInfo pi = ti.pojoInfo;
//            if (pi != null) {
//                writePojo(writer, node, pi, context);
//                return;
//            }
//            throw new BindingException("unsupported node type '" + Types.name(node) + "'");
//
//        } catch (BindingException e) {
//            throw e;
//        } catch (Exception e) {
//            throw new BindingException("failed to write node of type '" + Types.name(node) + "'", null, e);
//        }
//    }


    public static void writeNode(StreamingWriter writer, Object node, StreamingContext context) throws IOException {
        try {
            if (node == null) {
                writer.writeNull();
                return;
            }

            // scalar
            if (node instanceof String) {
                writer.writeStringValue((String) node);
                return;
            }
            if (node instanceof Number) {
                writer.writeNumberValue((Number) node);
                return;
            }
            if (node instanceof Boolean) {
                writer.writeBooleanValue((Boolean) node);
                return;
            }
            if (node instanceof Character) {
                writer.writeCharValue((Character) node);
                return;
            }
            if (node instanceof Enum) {
                writer.writeStringValue(((Enum<?>) node).name());
                return;
            }

            // containers
            if (node instanceof Map) {
                writeMap(writer, (Map<?, ?>) node, context);
                return;
            }
            if (node instanceof List) {
                writeList(writer, (List<?>) node, context);
                return;
            }

            Class<?> rawClazz = node.getClass();

            if (rawClazz == JsonObject.class) {
                writeJsonObject(writer, (JsonObject) node, context);
                return;
            }
            if (node instanceof JsonArray) {
                writeJsonArray(writer, (JsonArray) node, context);
                return;
            }
            if (node instanceof Set) {
                writeSet(writer, (Set<?>) node, context);
                return;
            }

            // arrays
            if (rawClazz.isArray() && writeArray(writer, node, rawClazz, context)) {
                return;
            }

            // registered types
            TypeInfo ti = TypeRegistry.registerTypeInfo(rawClazz);

            if (ti.isNodeValue()) {
                String valueFormat = context.defaultValueFormat(rawClazz);
                ValueInfo info = ti.getNodeValueInfo(valueFormat);
                if (info != null) {
                    writeNode(writer, info.valueToRaw(node), context);
                    return;
                }
            }

            PojoInfo pi = ti.pojoInfo;
            if (pi != null) {
                writePojo(writer, node, pi, context);
                return;
            }

            throw new BindingException("unsupported node type '" + Types.name(node) + "'");

        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException(
                    "failed to write node of type '" + Types.name(node) + "'", null, e);
        }
    }

    private static void writeMap(StreamingWriter writer, Map<?, ?> map,
                                 StreamingContext context) throws IOException {
        writer.startObject();
        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value == null && !context.includeNulls) {
                continue;
            }
            if (count++ > 0) {
                writer.separateProperty();
            }
            writer.writeName(entry.getKey().toString());
            writeNode(writer, value, context);
        }
        writer.endObject();
    }

    private static void writeList(StreamingWriter writer, List<?> list,
                                  StreamingContext context) throws IOException {
        writer.startArray();
        if (list instanceof RandomAccess) {
            for (int i = 0, size = list.size(); i < size; i++) {
                if (i > 0) {
                    writer.separateElement();
                }
                writeNode(writer, list.get(i), context);
            }
        } else {
            boolean first = true;
            for (Object value : list) {
                if (first) {
                    first = false;
                } else {
                    writer.separateElement();
                }
                writeNode(writer, value, context);
            }
        }
        writer.endArray();
    }

    private static void writeJsonObject(StreamingWriter writer, JsonObject object,
                                        StreamingContext context) throws IOException {
        writer.startObject();
        int count = 0;
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            Object value = entry.getValue();
            if (value == null && !context.includeNulls) {
                continue;
            }
            if (count++ > 0) {
                writer.separateProperty();
            }
            writer.writeName(entry.getKey());
            writeNode(writer, value, context);
        }
        writer.endObject();
    }

    private static void writeJsonArray(StreamingWriter writer, JsonArray array,
                                       StreamingContext context) throws IOException {
        writer.startArray();
        for (int i = 0, size = array.size(); i < size; i++) {
            if (i > 0) {
                writer.separateElement();
            }
            writeNode(writer, array.getNode(i), context);
        }
        writer.endArray();
    }

    private static void writeSet(StreamingWriter writer, Set<?> set,
                                 StreamingContext context) throws IOException {
        writer.startArray();
        boolean first = true;
        for (Object value : set) {
            if (first) {
                first = false;
            } else {
                writer.separateElement();
            }
            writeNode(writer, value, context);
        }
        writer.endArray();
    }

    private static boolean writeArray(StreamingWriter writer, Object node, Class<?> rawClazz,
                                      StreamingContext context) throws IOException {
        if (rawClazz == boolean[].class) {
            boolean[] array = (boolean[]) node;
            writer.startArray();
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeBooleanValue(array[i]);
            }
            writer.endArray();
            return true;
        }

        if (rawClazz == int[].class) {
            int[] array = (int[]) node;
            writer.startArray();
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeIntValue(array[i]);
            }
            writer.endArray();
            return true;
        }

        if (rawClazz == long[].class) {
            long[] array = (long[]) node;
            writer.startArray();
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeLongValue(array[i]);
            }
            writer.endArray();
            return true;
        }

        if (rawClazz == double[].class) {
            double[] array = (double[]) node;
            writer.startArray();
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeDoubleValue(array[i]);
            }
            writer.endArray();
            return true;
        }

        if (rawClazz == float[].class) {
            float[] array = (float[]) node;
            writer.startArray();
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeFloatValue(array[i]);
            }
            writer.endArray();
            return true;
        }

        if (rawClazz == byte[].class) {
            byte[] array = (byte[]) node;
            writer.startArray();
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeByteValue(array[i]);
            }
            writer.endArray();
            return true;
        }

        if (rawClazz == short[].class) {
            short[] array = (short[]) node;
            writer.startArray();
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeShortValue(array[i]);
            }
            writer.endArray();
            return true;
        }

        if (rawClazz == char[].class) {
            char[] array = (char[]) node;
            writer.startArray();
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeCharValue(array[i]);
            }
            writer.endArray();
            return true;
        }

        if (node instanceof Object[]) {
            Object[] array = (Object[]) node;
            writer.startArray();
            for (int i = 0, len = array.length; i < len; i++) {
                if (i > 0) writer.separateElement();
                writeNode(writer, array[i], context);
            }
            writer.endArray();
            return true;
        }

        return false;
    }


    static void writePojo(StreamingWriter writer, Object node, PojoInfo pi,
                          StreamingContext context) throws IOException {
        writer.startObject();
        int cnt = 0;
        FieldWriter[] fieldWriters = pi.fieldWriters;
        PreparedName[] preparedNames = writer.binder().getPreparedNames(node.getClass());
        for (int i = 0, len = fieldWriters.length; i < len; i++) {
            cnt = fieldWriters[i].write(writer, preparedNames[i], node, context, cnt);
        }

        if (pi.isJojo && pi.writeDynamic) {
            Map<String, Object> dynamicMap = ((JsonObject) node)._dynamicMap();
            if (dynamicMap != null) {
                for (Map.Entry<String, Object> entry : dynamicMap.entrySet()) {
                    Object value = entry.getValue();
                    if (value == null && !context.includeNulls) continue;
                    if (cnt++ > 0) writer.separateProperty();
                    writer.writeName(entry.getKey());
                    writeNode(writer, value, context);
                }
            }
        }
        writer.endObject();
    }

}
