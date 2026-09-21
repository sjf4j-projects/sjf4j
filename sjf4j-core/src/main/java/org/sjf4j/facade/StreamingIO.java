package org.sjf4j.facade;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.CreatorInfo;
import org.sjf4j.value.NodeValueInfo;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.OneOfInfo;
import org.sjf4j.node.FieldInfo;
import org.sjf4j.node.TypeInfo;
import org.sjf4j.node.Types;
import org.sjf4j.value.NodeValueCodec;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Streaming read/write helpers used by facade implementations.
 */
public final class StreamingIO {

    private static final Object UNSET = new Object();

    /// Read

    /**
     * Reads one node from streaming reader into target type using streaming context.
     */
    public static Object readNode(StreamingReader reader, Type type, StreamingContext context) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(context, "context");
        Class<?> rawBox = Types.rawBox(type);
        TypeInfo ti = TypeRegistry.registerTypeInfo(rawBox);
        return _readNode(reader, type, rawBox, ti, context);
    }

    /**
     * Reads next token and dispatches to typed node readers.
     */
    private static Object _readNode(StreamingReader reader, Type type, Class<?> rawBoxed,
                                    TypeInfo ti,
                                    StreamingContext context) {
        try {
            OneOfInfo oneOfInfo = ti != null ? ti.oneOfInfo : null;
            if (oneOfInfo != null) {
                return readOneOf(reader, oneOfInfo, context);
            }
            if (rawBoxed == Object.class) {
                return _readRawNode(reader);
            }
            StreamingReader.Token token = reader.peekToken();
            switch (token) {
                case START_OBJECT:
                    return _readObject(reader, type, rawBoxed, ti, context);
                case START_ARRAY:
                    return _readArray(reader, type, rawBoxed, ti, context);
                case STRING:
                    return _readString(reader, rawBoxed, context);
                case NUMBER:
                    return _readNumber(reader, rawBoxed, context);
                case BOOLEAN:
                    return _readBoolean(reader, rawBoxed, context);
                case NULL:
                    return _readNull(reader, rawBoxed, context);
                default:
                    throw new BindingException("unexpected token '" + token + "'");
            }
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to read streaming into '" + type + "'", null, e);
        }
    }


    private static Object _readRawNode(StreamingReader reader) throws IOException {
        switch (reader.peekToken()) {
            case START_OBJECT:
                return _readRawObject(reader);
            case START_ARRAY:
                return _readRawArray(reader);
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

    private static Map<String, Object> _readRawObject(StreamingReader reader) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        reader.startObject();
        while (!reader.nextIfObjectEnd()) {
            map.put(reader.nextName(), _readRawNode(reader));
        }
        return map;
    }

    private static List<Object> _readRawArray(StreamingReader reader) throws IOException {
        List<Object> list = new ArrayList<>();
        reader.startArray();
        while (!reader.nextIfArrayEnd()) {
            list.add(_readRawNode(reader));
        }
        return list;
    }

    /**
     * Reads null token and decodes via value codec when needed.
     */
    private static Object _readNull(StreamingReader reader, Class<?> rawClazz, StreamingContext context)
            throws IOException {
        reader.nextNull();
        if (rawClazz == Optional.class) return NodeValueCodec.OPTIONAL.rawToValue(null);
        return null;
    }

    /**
     * Reads boolean token into target type.
     */
    private static Object _readBoolean(StreamingReader reader, Class<?> rawClazz,
                                       StreamingContext context) throws IOException {
        if (rawClazz == Boolean.class) {
            return reader.nextBoolean();
        }

        NodeValueInfo vci = resolveValueCodecInfo(rawClazz, context);
        if (vci != null) {
            boolean b = reader.nextBoolean();
            return vci.rawToValue(b);
        }
        throw new BindingException("cannot read boolean value into type '" + rawClazz.getName() + "'");
    }

    /**
     * Reads number token into target numeric or codec type.
     */
    private static Object _readNumber(StreamingReader reader, Class<?> rawClazz,
                                      StreamingContext context) throws IOException {
        if (rawClazz == Number.class) {
            return reader.nextNumber();
        }
        if (rawClazz == Integer.class) return reader.nextInt();
        if (rawClazz == Long.class) return reader.nextLong();
        if (rawClazz == Float.class) return reader.nextFloat();
        if (rawClazz == Double.class) return reader.nextDouble();
        if (rawClazz == Short.class) return reader.nextShort();
        if (rawClazz == Byte.class) return reader.nextByte();
        if (rawClazz == BigInteger.class) return reader.nextBigInteger();
        if (rawClazz == BigDecimal.class) return reader.nextBigDecimal();

        NodeValueInfo vci = resolveValueCodecInfo(rawClazz, context);
        if (vci != null) {
            Number n = reader.nextNumber();
            return vci.rawToValue(n);
        }
        throw new BindingException("cannot read number value into type '" + rawClazz.getName() + "'");
    }

    /**
     * Reads string token into target scalar or codec type.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object _readString(StreamingReader reader, Class<?> rawClazz,
                                      StreamingContext context) throws IOException {
        if (rawClazz == String.class) {
            return reader.nextString();
        }
        if (rawClazz == Character.class) {
            String s = reader.nextString();
            return !s.isEmpty() ? s.charAt(0) : null;
        }
        if (rawClazz.isEnum()) {
            String s = reader.nextString();
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }

        NodeValueInfo vci = resolveValueCodecInfo(rawClazz, context);
        if (vci != null) {
            String s = reader.nextString();
            return vci.rawToValue(s);
        }
        throw new BindingException("cannot read string value into type '" + rawClazz.getName() + "'");
    }

    /**
     * Reads object token into Map/JsonObject/POJO target.
     */
    private static Object _readObject(StreamingReader reader, Type type, Class<?> rawClazz,
                                       TypeInfo ti,
                                       StreamingContext context)
            throws IOException {
        if (Map.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readMap(reader, rawClazz, valueType, valueClazz,
                    TypeRegistry.registerTypeInfo(valueClazz), context);
        }

        if (rawClazz == JsonObject.class) {
            return new JsonObject(_readRawObject(reader));
        }

        if (ti == null) {
            ti = TypeRegistry.registerTypeInfo(rawClazz);
        }
        if (ti.isNodeValue()) {
            String valueFormat = context.defaultValueFormat(rawClazz);
            NodeValueInfo vci = ti.getNodeValueInfo(valueFormat);
            if (vci != null) {
                Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
                Class<?> valueClazz = Types.rawBox(valueType);
                TypeInfo valueTi = TypeRegistry.registerTypeInfo(valueClazz);
                Map<String, Object> map = _readMap(reader, vci.rawClazz, valueType, valueClazz, valueTi, context);
                return vci.rawToValue(map);
            }
        }

        PojoInfo pi = ti.pojoInfo;
        if (pi != null && !pi.isJajo) {
            return readPojo(reader, type, rawClazz, pi, context);
        }

        throw new BindingException("cannot read object value into type '" + rawClazz.getName() + "'");
    }

    public static Object readPojo(StreamingReader reader, Type ownerType, Class<?> ownerRawClazz,
                                  PojoInfo pi, StreamingContext context)
            throws IOException {
        CreatorInfo ci = pi.creatorInfo;
        boolean hasParentOneOf = pi.hasParentScopeOneOf;

        if (!hasParentOneOf && ci.hasNoArgsCreator() && (ci.argNames == null || ci.argNames.length == 0)) {
            Object pojo = ci.newPojoNoArgs();
            Map<String, Object> dynamicMap = null;
            reader.startObject();
            while (!reader.nextIfObjectEnd()) {
                String key = reader.nextName();
                FieldInfo fi = pi.aliasProperties != null ? pi.aliasProperties.get(key) : pi.properties.get(key);
                if (fi != null) {
                    Object vv = _readField(reader, fi, ownerType, ownerRawClazz, context);
                    fi.invokeSetterIfPresent(pojo, vv);
                } else if (pi.isJojo && pi.readDynamic) {
                    if (dynamicMap == null) {
                        dynamicMap = new LinkedHashMap<>();
                    }
                    dynamicMap.put(key, _readRawNode(reader));
                } else {
                    reader.skipNext();
                }
            }
            if (pi.isJojo) {
                ((JsonObject) pojo)._dynamicMap(dynamicMap);
            }
            return pojo;
        }

        TypeRegistry.PojoCreationSession session = new TypeRegistry.PojoCreationSession(pi.creatorInfo, pi.propertyCount);
        FieldInfo deferredParentOneOfFi = null;
        Object deferredParentOneOfRaw = null;
        String parentOneOfKey = null;
        Object parentOneOfValue = UNSET;

        reader.startObject();
        while (!reader.nextIfObjectEnd()) {
            String key = reader.nextName();

            int argIdx = ci.getArgIndexOrAlias(key);
            if (argIdx >= 0) {
                Type argType = Types.resolveMemberType(ownerType, ownerRawClazz, ci.argTypes[argIdx]);
                Class<?> argRaw = Types.rawBox(argType);
                TypeInfo ti = TypeRegistry.registerTypeInfo(argRaw);
                NodeValueInfo argVci = ci.argValueCodecs[argIdx];
                if (argVci == null && ti.isNodeValue()) {
                    String valueFormat = context.defaultValueFormat(argRaw);
                    argVci = ti.getNodeValueInfo(valueFormat);
                }
                Object argValue;
                if (ti.oneOfInfo == null && argVci != null) {
                    argValue = _readValueWithCodec(reader, argType, argRaw, argVci, context);
                } else {
                    argValue = _readNode(reader, argType, argRaw, ti, context);
                }
                session.acceptCtorArg(argIdx, argValue);
                if (parentOneOfKey != null && parentOneOfKey.equals(key)) {
                    parentOneOfValue = argValue;
                }
                continue;
            }

            FieldInfo fi = pi.aliasProperties != null ? pi.aliasProperties.get(key) : pi.properties.get(key);
            if (fi != null) {
                Object vv;
                OneOfInfo fieldOneOf = fi.oneOfInfo;
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
                        vv = _readNode(reader, targetClazz, Types.rawBox(targetClazz), null, context);
                    } else {
                        if (deferredParentOneOfFi != null) {
                            throw new BindingException("at most one OneOf field with scope=PARENT is supported per class");
                        }
                        deferredParentOneOfFi = fi;
                        deferredParentOneOfRaw = _readRawNode(reader);
                        continue;
                    }
                } else {
                    vv = _readField(reader, fi, ownerType, ownerRawClazz, context);
                }

                if (parentOneOfKey != null && parentOneOfKey.equals(key)) {
                    parentOneOfValue = vv;
                }
                session.acceptProperty(fi, vv);
                continue;
            }

            if (pi.isJojo && pi.readDynamic) {
                Object vv = _readRawNode(reader);
                session.acceptDynamic(key, vv);
                if (parentOneOfKey != null && parentOneOfKey.equals(key)) {
                    parentOneOfValue = vv;
                }
            } else {
                reader.skipNext();
            }
        }

        Object pojo = session.finish();
        applyDeferredParentOneOf(pojo, pi, deferredParentOneOfFi, deferredParentOneOfRaw,
                parentOneOfValue, UNSET, context);
        return pojo;
    }

    /**
     * Reads array token into List/JsonArray/array/Set target.
     */
    private static Object _readArray(StreamingReader reader, Type type, Class<?> rawClazz,
                                       TypeInfo ti,
                                       StreamingContext context)
            throws IOException {
        if (List.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readList(reader, rawClazz, valueType, valueClazz,
                    TypeRegistry.registerTypeInfo(valueClazz), context);
        }

        if (rawClazz == JsonArray.class) {
            return new JsonArray(_readRawArray(reader));
        }

        if (Set.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readSet(reader, rawClazz, valueType, valueClazz,
                    TypeRegistry.registerTypeInfo(valueClazz), context);
        }

        if (rawClazz.isArray()) {
            Class<?> compType = rawClazz.getComponentType();
            Class<?> valueClazz = Types.box(compType);
            return _readArray(reader, rawClazz, compType, valueClazz,
                    TypeRegistry.registerTypeInfo(valueClazz), context);
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            JsonArray ja = (JsonArray) TypeRegistry.registerPojoOrElseThrow(rawClazz).creatorInfo.forceNewPojo();
            Class<?> elemType = ja.elementClass();
            Class<?> elemRaw = Types.box(elemType);
            TypeInfo elemTi = TypeRegistry.registerTypeInfo(elemRaw);
            reader.startArray();
            while (!reader.nextIfArrayEnd()) {
                Object value = _readNode(reader, elemType, elemRaw, elemTi, context);
                ja.add(value);
            }
            return ja;
        }

        if (ti == null) {
            ti = TypeRegistry.registerTypeInfo(rawClazz);
        }
        NodeValueInfo vci = ti.isNodeValue()
                ? ti.getNodeValueInfo(context.defaultValueFormat(rawClazz))
                : null;
        if (vci != null) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            List<Object> list = _readList(reader, vci.rawClazz, valueType, valueClazz,
                    TypeRegistry.registerTypeInfo(valueClazz), context);
            return vci.rawToValue(list);
        }

        throw new BindingException("cannot read array value into type '" + rawClazz.getName() + "'");
    }

    /**
     * Reads one object field based on field container metadata.
     */
    private static Object _readField(StreamingReader reader, FieldInfo fi,
                                     Type ownerType, Class<?> ownerRawClazz,
                                     StreamingContext context)
            throws IOException {
        Type fieldType = Types.resolveMemberType(ownerType, ownerRawClazz, fi.type);
        Class<?> fieldRaw = fieldType == fi.type ? fi.boxed : Types.rawBox(fieldType);

        OneOfInfo fieldOneOf = fi.oneOfInfo;
        if (fieldOneOf == null && fieldRaw != fi.boxed) {
            fieldOneOf = TypeRegistry.registerTypeInfo(fieldRaw).oneOfInfo;
        }
        if (fieldOneOf != null) {
            return readOneOf(reader, fieldOneOf, context);
        }

        if (fi.resolvedValueCodec != null) {
            return _readValueWithCodec(reader, fieldType, fieldRaw, fi.resolvedValueCodec, context);
        }

        switch (fieldType == fi.type ? fi.containerKind : FieldInfo.ContainerKind.NONE) {
            case MAP:
                return _readMap(reader, fi.boxed, fi.argType, fi.argBoxed,
                        TypeRegistry.registerTypeInfo(fi.argBoxed), context);
            case LIST:
                return _readList(reader, fi.boxed, fi.argType, fi.argBoxed,
                        TypeRegistry.registerTypeInfo(fi.argBoxed), context);
            case SET:
                return _readSet(reader, fi.boxed, fi.argType, fi.argBoxed,
                        TypeRegistry.registerTypeInfo(fi.argBoxed), context);
            case ARRAY:
                return _readArray(reader, fi.boxed, fi.argType, fi.argBoxed,
                        TypeRegistry.registerTypeInfo(fi.argBoxed), context);
            default:
                return _readNode(reader, fieldType, fieldRaw, null, context);
        }
    }

    private static Object _readValueWithCodec(StreamingReader reader,
                                              Type type,
                                              Class<?> rawClazz,
                                              NodeValueInfo nodeValueInfo,
                                              StreamingContext context) throws IOException {
        switch (reader.peekToken()) {
            case START_OBJECT: {
                Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
                Class<?> valueClazz = Types.rawBox(valueType);
                Map<String, Object> map = _readMap(reader, nodeValueInfo.rawClazz, valueType, valueClazz,
                        TypeRegistry.registerTypeInfo(valueClazz), context);
                return nodeValueInfo.rawToValue(map);
            }
            case START_ARRAY: {
                Type valueType = Types.resolveTypeArgument(type, List.class, 0);
                Class<?> valueClazz = Types.rawBox(valueType);
                List<Object> list = _readList(reader, nodeValueInfo.rawClazz, valueType, valueClazz,
                        TypeRegistry.registerTypeInfo(valueClazz), context);
                return nodeValueInfo.rawToValue(list);
            }
            case STRING:
                return nodeValueInfo.rawToValue(reader.nextString());
            case NUMBER:
                return nodeValueInfo.rawToValue(reader.nextNumber());
            case BOOLEAN:
                return nodeValueInfo.rawToValue(reader.nextBoolean());
            case NULL:
                reader.nextNull();
                return nodeValueInfo.rawToValue(null);
            default:
                throw new BindingException("cannot read value into type '" + rawClazz.getName() + "'");
        }
    }

    /**
     * Reads object token into map with typed values.
     */
    private static Map<String, Object> _readMap(StreamingReader reader, Class<?> mapClazz,
                                                 Type valueType, Class<?> valueClazz,
                                                 TypeInfo valueTi,
                                                 StreamingContext context)
            throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        Map<String, Object> map = mapClazz == Object.class || mapClazz == Map.class || mapClazz == LinkedHashMap.class
                ? new LinkedHashMap<>()
                : TypeRegistry.newMapContainer(mapClazz, 0, false);
        reader.startObject();
        while (!reader.nextIfObjectEnd()) {
            String key = reader.nextName();
            Object value = _readNode(reader, valueType, valueClazz, valueTi, context);
            map.put(key, value);
        }
        return map;
    }

    /**
     * Reads array token into list with typed elements.
     */
    private static List<Object> _readList(StreamingReader reader, Class<?> listClazz,
                                           Type valueType, Class<?> valueClazz,
                                           TypeInfo valueTi,
                                           StreamingContext context)
            throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        List<Object> list = listClazz == Object.class || listClazz == List.class || listClazz == ArrayList.class
                ? new ArrayList<>()
                : TypeRegistry.newListContainer(listClazz, 0, false);
        reader.startArray();
        while (!reader.nextIfArrayEnd()) {
            Object value = _readNode(reader, valueType, valueClazz, valueTi, context);
            list.add(value);
        }
        return list;
    }

    /**
     * Reads array token into set with typed elements.
     */
    private static Set<Object> _readSet(StreamingReader reader, Class<?> setClazz,
                                         Type valueType, Class<?> valueClazz,
                                         TypeInfo valueTi,
                                         StreamingContext context)
            throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        Set<Object> set = setClazz == Object.class || setClazz == Set.class || setClazz == LinkedHashSet.class
                ? new LinkedHashSet<>()
                : TypeRegistry.newSetContainer(setClazz, 0, false);
        reader.startArray();
        while (!reader.nextIfArrayEnd()) {
            Object value = _readNode(reader, valueType, valueClazz, valueTi, context);
            set.add(value);
        }
        return set;
    }

    /**
     * Reads array token into Java array with typed elements.
     */
    private static Object _readArray(StreamingReader reader, Class<?> rawClazz,
                                      Type valueType, Class<?> valueClazz,
                                      TypeInfo valueTi,
                                      StreamingContext context)
            throws IOException {
        List<Object> list = _readList(reader, List.class, valueType, valueClazz, valueTi, context);
        if (list == null) {
            return null;
        }

        Object array = Array.newInstance(rawClazz.getComponentType(), list.size());
        for (int j = 0, len = list.size(); j < len; j++) {
            Array.set(array, j, list.get(j));
        }
        return array;
    }

    /**
     * Reads OneOf target by discriminator or token kind.
     */
    public static Object readOneOf(StreamingReader reader, OneOfInfo anyOfInfo,
                                   StreamingContext context)
            throws IOException {
        if (anyOfInfo.hasDiscriminator) {
            // Discriminator-based OneOf may need to inspect the current value before binding it.
            StreamingReader forked = reader.forkValue();
            if (forked != null) {
                try (StreamingReader buffered = forked) {
                    return _readOneOf(buffered, anyOfInfo, context);
                }
            }
        }
        return _readOneOf(reader, anyOfInfo, context);
    }

    private static Object _readOneOf(StreamingReader reader, OneOfInfo anyOfInfo,
                                     StreamingContext context)
            throws IOException {
        if (anyOfInfo.hasDiscriminator) {
            Object rawNode = _readRawNode(reader);
            Class<?> targetClazz = resolveCurrentDiscriminatorTarget(rawNode, anyOfInfo);
            if (targetClazz == null) return null;
            return context.nodeFacade.readNode(rawNode, targetClazz);
        }

        Class<?> targetClazz = resolveOneOfJsonTypeTarget(reader.peekToken().jsonType(), anyOfInfo);
        if (targetClazz == null) {
            _readRawNode(reader);
            return null;
        }
        return _readNode(reader, targetClazz, Types.rawBox(targetClazz), null, context);
    }

    /// Write

    /**
     * Writes one node to streaming writer using instance-level value formats.
     */
    public static void writeNode(StreamingWriter writer, Object node, StreamingContext context) throws IOException {
        Objects.requireNonNull(writer, "writer");
        Objects.requireNonNull(context, "context");
        _writeNode(writer, node, context);
    }

    /**
     * Writes node recursively as streaming tokens.
     */
    private static void _writeNode(StreamingWriter writer, Object node, StreamingContext context) throws IOException {
        try {
            if (node == null) {
                writer.writeNull();
                return;
            }

            if (node instanceof String) {
                writer.writeString(node.toString());
                return;
            }
            if (node instanceof Number) {
                writer.writeNumber((Number) node);
                return;
            }
            if (node instanceof Boolean) {
                writer.writeBoolean((Boolean) node);
                return;
            }

            if (node instanceof Map) {
                writer.startObject();
                int cnt = 0;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                    Object value = entry.getValue();
                    if (value == null && !context.includeNulls) continue;
                    if (cnt++ > 0) writer.writeObjectComma();
                    String key = entry.getKey().toString();
                    writer.writeName(key);
                    _writeNode(writer, value, context);
                }
                writer.endObject();
                return;
            }

            if (node instanceof List) {
                writer.startArray();
                List<?> list = (List<?>) node;
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) writer.writeArrayComma();
                    _writeNode(writer, list.get(i), context);
                }
                writer.endArray();
                return;
            }

            Class<?> rawClazz = node.getClass();
            if (rawClazz == JsonObject.class) {
                writer.startObject();
                int cnt = 0;
                for (Map.Entry<String, Object> entry : ((JsonObject) node).entrySet()) {
                    Object value = entry.getValue();
                    if (value == null && !context.includeNulls) continue;
                    if (cnt++ > 0) writer.writeObjectComma();
                    writer.writeName(entry.getKey());
                    _writeNode(writer, value, context);
                }
                writer.endObject();
                return;
            }

            if (node instanceof JsonArray) {
                writer.startArray();
                JsonArray ja = (JsonArray) node;
                for (int i = 0, len = ja.size(); i < len; i++) {
                    if (i > 0) writer.writeArrayComma();
                    _writeNode(writer, ja.getNode(i), context);
                }
                writer.endArray();
                return;
            }

            if (node.getClass().isArray()) {
                writer.startArray();
                for (int i = 0, len = Array.getLength(node); i < len; i++) {
                    if (i > 0) writer.writeArrayComma();
                    _writeNode(writer, Array.get(node, i), context);
                }
                writer.endArray();
                return;
            }

            if (node instanceof Set) {
                writer.startArray();
                boolean veryStart = true;
                for (Object v : (Set<?>) node) {
                    if (veryStart) veryStart = false;
                    else writer.writeArrayComma();
                    _writeNode(writer, v, context);
                }
                writer.endArray();
                return;
            }

            if (node instanceof Character) {
                writer.writeString(node.toString());
                return;
            }
            if (node instanceof Enum) {
                writer.writeString(((Enum<?>) node).name());
                return;
            }

            TypeInfo ti = TypeRegistry.registerTypeInfo(rawClazz);
            if (ti.isNodeValue()) {
                String valueFormat = context.defaultValueFormat(rawClazz);
                NodeValueInfo vci = ti.getNodeValueInfo(valueFormat);
                if (vci != null) {
                    Object raw = vci.valueToRaw(node);
                    _writeNode(writer, raw, context);
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
            throw new BindingException("failed to write node of type '" + Types.name(node) + "'", null, e);
        }
    }

    public static void writePojo(StreamingWriter writer, Object node, PojoInfo pi,
                                 StreamingContext context) throws IOException {
        writer.startObject();
        int cnt = 0;
        for (Map.Entry<String, FieldInfo> entry : pi.readableProperties.entrySet()) {
            Object vv = entry.getValue().invokeGetter(node);
            if (vv == null && !context.includeNulls) continue;
            if (cnt++ > 0) writer.writeObjectComma();
            String key = entry.getKey();
            writer.writeName(key);
            if (vv == null) {
                writer.writeNull();
            } else {
                FieldInfo fi = entry.getValue();
                if (fi.resolvedValueCodec != null) {
                    vv = fi.resolvedValueCodec.valueToRaw(vv);
                }
                _writeNode(writer, vv, context);
            }
        }
        if (pi.isJojo && pi.writeDynamic) {
            Map<String, Object> dynamicMap = ((JsonObject) node)._dynamicMap();
            if (dynamicMap != null) {
                for (Map.Entry<String, Object> entry : dynamicMap.entrySet()) {
                    Object value = entry.getValue();
                    if (value == null && !context.includeNulls) continue;
                    if (cnt++ > 0) writer.writeObjectComma();
                    writer.writeName(entry.getKey());
                    _writeNode(writer, value, context);
                }
            }
        }
        writer.endObject();
    }

    /// Support

    public static NodeValueInfo resolveValueCodecInfo(Class<?> clazz, StreamingContext context) {
        TypeInfo ti = TypeRegistry.registerTypeInfo(clazz);
        if (ti.isNodeValue()) {
            String valueFormat = context.defaultValueFormat(clazz);
            return ti.getNodeValueInfo(valueFormat);
        }
        return null;
    }

    public static Class<?> resolveOneOfJsonTypeTarget(JsonType jsonType, OneOfInfo anyOfInfo) {
        Class<?> targetClazz = anyOfInfo.matchByJsonType(jsonType);
        if (targetClazz == null) {
            if (anyOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
                return null;
            }
            throw new BindingException("oneOf mapping does not support jsonType=" + jsonType +
                    " for type '" + anyOfInfo.clazz.getName() + "'");
        }
        return targetClazz;
    }

    public static Class<?> resolveOneOfDiscriminatorTarget(Object discriminatorValue, OneOfInfo anyOfInfo) {
        if (discriminatorValue == null) {
            if (anyOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) return null;
            String source = !anyOfInfo.key.isEmpty() ? "key '" + anyOfInfo.key + "'" : "path '" + anyOfInfo.path + "'";
            throw new BindingException("not found value for discriminator " + source);
        }

        Class<?> targetClazz = anyOfInfo.matchByWhen(discriminatorValue);
        if (targetClazz == null) {
            if (anyOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) return null;
            throw new BindingException("oneOf discriminator has no matching mapping: value='" + discriminatorValue + "'");
        }
        return targetClazz;
    }

    public static Class<?> resolveCurrentDiscriminatorTarget(Object rawNode, OneOfInfo anyOfInfo) {
        if (anyOfInfo.scope != OneOf.Scope.CURRENT) {
            throw new BindingException("oneOf scope '" + anyOfInfo.scope + "' is not supported in streaming parser");
        }
        if (!(rawNode instanceof Map)) {
            if (anyOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) return null;
            throw new BindingException("node must be an object, when OneOf has a CURRENT discriminator");
        }

        Object discriminatorValue = null;
        if (!anyOfInfo.key.isEmpty()) {
            discriminatorValue = ((Map<?, ?>) rawNode).get(anyOfInfo.key);
        } else if (!anyOfInfo.path.isEmpty()) {
            discriminatorValue = anyOfInfo.compiledPath.getNode(rawNode);
        }
        return resolveOneOfDiscriminatorTarget(discriminatorValue, anyOfInfo);
    }

    public static void applyDeferredParentOneOf(Object pojo, PojoInfo pi,
                                                FieldInfo deferredParentOneOfFi,
                                                Object deferredParentOneOfRaw, Object parentOneOfValue,
                                                Object unsetSentinel,
                                                StreamingContext context) {
        if (deferredParentOneOfFi == null) {
            return;
        }

        OneOfInfo aoi = deferredParentOneOfFi.oneOfInfo;
        String parentKey = aoi.key;
        if (parentOneOfValue == unsetSentinel) {
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

        Class<?> targetClazz = aoi.matchByWhen(parentOneOfValue == unsetSentinel ? null : parentOneOfValue);
        Object vv;
        if (targetClazz != null) {
            vv = context.nodeFacade.readNode(deferredParentOneOfRaw, targetClazz);
        } else if (aoi.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
            vv = null;
        } else {
            throw new BindingException("oneOf discriminator has no matching mapping: key='" +
                    aoi.key + "', value='" + (parentOneOfValue == unsetSentinel ? null : parentOneOfValue) + "'");
        }
        deferredParentOneOfFi.invokeSetterIfPresent(pojo, vv);
    }


}
