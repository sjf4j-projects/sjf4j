package org.sjf4j.facade;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Types;

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
import java.util.Set;

/**
 * Streaming read/write helpers used by facade implementations.
 */
public final class StreamingIO {

    private static final Object UNSET = new Object();

    /// Read

    /**
     * Reads one node from streaming reader into target type.
     */
    public static Object readNode(StreamingReader reader, Type type) {
        Class<?> rawBox = Types.rawBox(type);
        NodeRegistry.AnyOfInfo anyOfInfo = NodeRegistry.registerTypeInfo(rawBox).anyOfInfo;
        return _readNode(reader, type, rawBox, anyOfInfo);
    }

    /**
     * Reads next token and dispatches to typed node readers.
     */
    private static Object _readNode(StreamingReader reader, Type type, Class<?> rawBoxed,
                                    NodeRegistry.AnyOfInfo anyOfInfo) {
        try {
            if (anyOfInfo != null) {
                return readAnyOf(reader, anyOfInfo);
            }
            if (rawBoxed == Object.class) {
                return _readRawNode(reader);
            }
            StreamingReader.Token token = reader.peekToken();
            switch (token) {
                case START_OBJECT:
                    return _readObject(reader, type, rawBoxed);
                case START_ARRAY:
                    return _readArray(reader, type, rawBoxed);
                case STRING:
                    return _readString(reader, rawBoxed);
                case NUMBER:
                    return _readNumber(reader, rawBoxed);
                case BOOLEAN:
                    return _readBoolean(reader, rawBoxed);
                case NULL:
                    return _readNull(reader, rawBoxed);
                default:
                    throw new JsonException("Unexpected token '" + token + "'");
            }
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to read streaming into '" + type + "'", null, e);
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
                throw new JsonException("Unexpected token '" + reader.peekToken() + "'");
        }
    }

    private static Map<String, Object> _readRawObject(StreamingReader reader) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        reader.startObject();
        while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
            map.put(reader.nextName(), _readRawNode(reader));
        }
        reader.endObject();
        return map;
    }

    private static List<Object> _readRawArray(StreamingReader reader) throws IOException {
        List<Object> list = new ArrayList<>();
        reader.startArray();
        while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
            list.add(_readRawNode(reader));
        }
        reader.endArray();
        return list;
    }

    /**
     * Reads null token and decodes via value codec when needed.
     */
    private static Object _readNull(StreamingReader reader, Class<?> rawClazz)
            throws IOException {
        reader.nextNull();

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            return vci.rawToValue(null);
        }
        return null;
    }

    /**
     * Reads boolean token into target type.
     */
    private static Object _readBoolean(StreamingReader reader, Class<?> rawClazz) throws IOException {
        if (rawClazz == Boolean.class) {
            return reader.nextBoolean();
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            boolean b = reader.nextBoolean();
            return vci.rawToValue(b);
        }
        throw new BindingException("Cannot read boolean value into type '" + rawClazz.getName() + "'");
    }

    /**
     * Reads number token into target numeric or codec type.
     */
    private static Object _readNumber(StreamingReader reader, Class<?> rawClazz) throws IOException {
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

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            Number n = reader.nextNumber();
            return vci.rawToValue(n);
        }
        throw new BindingException("Cannot read number value into type '" + rawClazz.getName() + "'");
    }

    /**
     * Reads string token into target scalar or codec type.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object _readString(StreamingReader reader, Class<?> rawClazz) throws IOException {
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

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            String s = reader.nextString();
            return vci.rawToValue(s);
        }
        throw new BindingException("Cannot read string value into type '" + rawClazz.getName() + "'");
    }

    /**
     * Reads object token into Map/JsonObject/POJO target.
     */
    private static Object _readObject(StreamingReader reader, Type type, Class<?> rawClazz)
            throws IOException {
        if (Map.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readMap(reader, rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo);
        }

        if (rawClazz == JsonObject.class) {
            return new JsonObject(_readRawObject(reader));
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
        NodeRegistry.ValueCodecInfo vci = ti.valueCodecInfo;
        if (vci != null) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueClazz = Types.rawBox(valueType);
            Map<String, Object> map = _readMap(reader, vci.rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo);
            return vci.rawToValue(map);
        }

        NodeRegistry.PojoInfo pi = ti.pojoInfo;
        if (pi != null && !pi.isJajo) {
            return readPojo(reader, type, rawClazz, pi);
        }

        throw new BindingException("Cannot read object value into type '" + rawClazz.getName() + "'");
    }

    public static Object readPojo(StreamingReader reader, Type ownerType, Class<?> ownerRawClazz,
                                  NodeRegistry.PojoInfo pi)
            throws IOException {
        NodeRegistry.CreatorInfo ci = pi.creatorInfo;
        boolean hasParentAnyOf = pi.hasParentScopeAnyOf;

        if (!hasParentAnyOf && ci.hasNoArgsCreator() && (ci.argNames == null || ci.argNames.length == 0)) {
            Object pojo = ci.newPojoNoArgs();
            Map<String, Object> dynamicMap = null;
            reader.startObject();
            while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
                String key = reader.nextName();
                NodeRegistry.FieldInfo fi = pi.aliasFields != null ? pi.aliasFields.get(key) : pi.fields.get(key);
                if (fi != null) {
                    Object vv = _readField(reader, fi, ownerType, ownerRawClazz);
                    fi.invokeSetterIfPresent(pojo, vv);
                } else if (pi.isJojo) {
                    if (dynamicMap == null) {
                        dynamicMap = new LinkedHashMap<>();
                    }
                    dynamicMap.put(key, _readRawNode(reader));
                } else {
                    reader.skipNext();
                }
            }
            reader.endObject();
            if (pi.isJojo) {
                ((JsonObject) pojo).setDynamicMap(dynamicMap);
            }
            return pojo;
        }

        NodeRegistry.PojoCreationSession session = new NodeRegistry.PojoCreationSession(pi.creatorInfo, pi.fieldCount);
        NodeRegistry.FieldInfo deferredParentAnyOfFi = null;
        Object deferredParentAnyOfRaw = null;
        String parentAnyOfKey = null;
        Object parentAnyOfValue = UNSET;

        reader.startObject();
        while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
            String key = reader.nextName();

            int argIdx = session.resolveArgIndex(key);
            if (argIdx >= 0) {
                Type argType = Types.resolveMemberType(ownerType, ownerRawClazz, ci.argTypes[argIdx]);
                Class<?> argRaw = Types.rawBox(argType);
                NodeRegistry.AnyOfInfo argAnyOf = NodeRegistry.registerTypeInfo(argRaw).anyOfInfo;
                Object argValue = _readNode(reader, argType, argRaw, argAnyOf);
                session.acceptResolvedField(argIdx, argValue, null);
                if (parentAnyOfKey != null && parentAnyOfKey.equals(key)) {
                    parentAnyOfValue = argValue;
                }
                continue;
            }

            NodeRegistry.FieldInfo fi = pi.aliasFields != null ? pi.aliasFields.get(key) : pi.fields.get(key);
            if (fi != null) {
                Object vv;
                NodeRegistry.AnyOfInfo fieldAnyOf = fi.anyOfInfo;
                if (hasParentAnyOf && fieldAnyOf != null && fieldAnyOf.scope == AnyOf.Scope.PARENT) {
                    if (!fieldAnyOf.path.isEmpty()) {
                        throw new BindingException("AnyOf scope=PARENT does not support path discriminator");
                    }
                    String parentKey = fieldAnyOf.key;
                    if (parentAnyOfKey == null) {
                        parentAnyOfKey = parentKey;
                    } else if (!parentAnyOfKey.equals(parentKey)) {
                        throw new BindingException("At most one AnyOf parent discriminator key is supported per class");
                    }
                    Class<?> targetClazz = fieldAnyOf.resolveByWhen(parentAnyOfValue == UNSET ? null : parentAnyOfValue);
                    if (targetClazz != null) {
                        vv = _readNode(reader, targetClazz, Types.rawBox(targetClazz), null);
                    } else {
                        if (deferredParentAnyOfFi != null) {
                            throw new BindingException("At most one AnyOf field with scope=PARENT is supported per class");
                        }
                        deferredParentAnyOfFi = fi;
                        deferredParentAnyOfRaw = _readRawNode(reader);
                        continue;
                    }
                } else {
                    vv = _readField(reader, fi, ownerType, ownerRawClazz);
                }

                if (parentAnyOfKey != null && parentAnyOfKey.equals(key)) {
                    parentAnyOfValue = vv;
                }
                session.acceptResolvedField(-1, vv, fi);
                continue;
            }

            if (pi.isJojo) {
                Object vv = _readRawNode(reader);
                session.acceptResolvedJsonEntry(-1, key, vv);
                if (parentAnyOfKey != null && parentAnyOfKey.equals(key)) {
                    parentAnyOfValue = vv;
                }
            } else {
                reader.skipNext();
            }
        }
        reader.endObject();

        Object pojo = session.finishField();
        applyDeferredParentAnyOf(pojo, pi, deferredParentAnyOfFi, deferredParentAnyOfRaw,
                parentAnyOfValue, UNSET);
        return pojo;
    }

    /**
     * Reads array token into List/JsonArray/array/Set target.
     */
    private static Object _readArray(StreamingReader reader, Type type, Class<?> rawClazz)
            throws IOException {
        if (List.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readList(reader, rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo);
        }

        if (rawClazz == JsonArray.class) {
            return new JsonArray(_readRawArray(reader));
        }

        if (Set.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readSet(reader, rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo);
        }

        if (rawClazz.isArray()) {
            Class<?> compType = rawClazz.getComponentType();
            Class<?> valueClazz = Types.box(compType);
            return _readArray(reader, rawClazz, compType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo);
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            JsonArray ja = (JsonArray) NodeRegistry.registerPojoOrElseThrow(rawClazz).creatorInfo.forceNewPojo();
            Class<?> elemType = ja.elementType();
            Class<?> elemRaw = Types.box(elemType);
            NodeRegistry.AnyOfInfo elemAnyOf = NodeRegistry.registerTypeInfo(elemRaw).anyOfInfo;
            reader.startArray();
            while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
                Object value = _readNode(reader, elemType, elemRaw, elemAnyOf);
                ja.add(value);
            }
            reader.endArray();
            return ja;
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            List<Object> list = _readList(reader, vci.rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo);
            return vci.rawToValue(list);
        }

        throw new BindingException("Cannot read array value into type '" + rawClazz.getName() + "'");
    }

    /**
     * Reads one object field based on field container metadata.
     */
    private static Object _readField(StreamingReader reader, NodeRegistry.FieldInfo fi,
                                     Type ownerType, Class<?> ownerRawClazz)
            throws IOException {
        Type fieldType = Types.resolveMemberType(ownerType, ownerRawClazz, fi.type);
        Class<?> fieldRaw = fieldType == fi.type ? fi.rawClazz : Types.rawBox(fieldType);

        NodeRegistry.AnyOfInfo fieldAnyOf = fi.anyOfInfo;
        if (fieldAnyOf == null && fieldRaw != fi.rawClazz) {
            fieldAnyOf = NodeRegistry.registerTypeInfo(fieldRaw).anyOfInfo;
        }
        if (fieldAnyOf != null) {
            return _readNode(reader, fieldType, fieldRaw, fieldAnyOf);
        }

        switch (fieldType == fi.type ? fi.containerKind : NodeRegistry.FieldInfo.ContainerKind.NONE) {
            case MAP:
                return _readMap(reader, fi.rawClazz, fi.argType, fi.argRawClazz, fi.argAnyOfInfo);
            case LIST:
                return _readList(reader, fi.rawClazz, fi.argType, fi.argRawClazz, fi.argAnyOfInfo);
            case SET:
                return _readSet(reader, fi.rawClazz, fi.argType, fi.argRawClazz, fi.argAnyOfInfo);
            case ARRAY:
                return _readArray(reader, fi.rawClazz, fi.argType, fi.argRawClazz, fi.argAnyOfInfo);
            default:
                return _readNode(reader, fieldType, fieldRaw, null);
        }
    }

    /**
     * Reads object token into map with typed values.
     */
    private static Map<String, Object> _readMap(StreamingReader reader, Class<?> mapClazz,
                                                Type valueType, Class<?> valueClazz,
                                                NodeRegistry.AnyOfInfo valueAnyOf)
            throws IOException {
        if (reader.peekToken() == StreamingReader.Token.NULL) {
            reader.nextNull();
            return null;
        }
        Map<String, Object> map = mapClazz == Object.class || mapClazz == Map.class || mapClazz == LinkedHashMap.class
                ? new LinkedHashMap<>()
                : NodeRegistry.newMapContainer(mapClazz, false);
        reader.startObject();
        while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
            String key = reader.nextName();
            Object value = _readNode(reader, valueType, valueClazz, valueAnyOf);
            map.put(key, value);
        }
        reader.endObject();
        return map;
    }

    /**
     * Reads array token into list with typed elements.
     */
    private static List<Object> _readList(StreamingReader reader, Class<?> listClazz,
                                          Type valueType, Class<?> valueClazz,
                                          NodeRegistry.AnyOfInfo valueAnyOf)
            throws IOException {
        if (reader.peekToken() == StreamingReader.Token.NULL) {
            reader.nextNull();
            return null;
        }
        List<Object> list = listClazz == Object.class || listClazz == List.class || listClazz == ArrayList.class
                ? new ArrayList<>()
                : NodeRegistry.newListContainer(listClazz, false);
        reader.startArray();
        while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
            Object value = _readNode(reader, valueType, valueClazz, valueAnyOf);
            list.add(value);
        }
        reader.endArray();
        return list;
    }

    /**
     * Reads array token into set with typed elements.
     */
    private static Set<Object> _readSet(StreamingReader reader, Class<?> setClazz,
                                        Type valueType, Class<?> valueClazz,
                                        NodeRegistry.AnyOfInfo valueAnyOf)
            throws IOException {
        if (reader.peekToken() == StreamingReader.Token.NULL) {
            reader.nextNull();
            return null;
        }
        Set<Object> set = setClazz == Object.class || setClazz == Set.class || setClazz == LinkedHashSet.class
                ? new LinkedHashSet<>()
                : NodeRegistry.newSetContainer(setClazz, false);
        reader.startArray();
        while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
            Object value = _readNode(reader, valueType, valueClazz, valueAnyOf);
            set.add(value);
        }
        reader.endArray();
        return set;
    }

    /**
     * Reads array token into Java array with typed elements.
     */
    private static Object _readArray(StreamingReader reader, Class<?> rawClazz,
                                     Type valueType, Class<?> valueClazz,
                                     NodeRegistry.AnyOfInfo valueAnyOf)
            throws IOException {
        List<Object> list = _readList(reader, List.class, valueType, valueClazz, valueAnyOf);
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
     * Reads AnyOf target by discriminator or token kind.
     */
    public static Object readAnyOf(StreamingReader reader, NodeRegistry.AnyOfInfo anyOfInfo)
            throws IOException {
        if (anyOfInfo.hasDiscriminator) {
            // Discriminator-based AnyOf may need to inspect the current value before binding it.
            StreamingReader forked = reader.forkValue();
            if (forked != null) {
                try (StreamingReader buffered = forked) {
                    return _readAnyOf(buffered, anyOfInfo);
                }
            }
        }
        return _readAnyOf(reader, anyOfInfo);
    }

    private static Object _readAnyOf(StreamingReader reader, NodeRegistry.AnyOfInfo anyOfInfo)
            throws IOException {
        if (anyOfInfo.hasDiscriminator) {
            Object rawNode = _readRawNode(reader);
            Class<?> targetClazz = resolveSelfDiscriminatorTarget(rawNode, anyOfInfo);
            if (targetClazz == null) return null;
            return FacadeFactory.getDefaultNodeFacade().readNode(rawNode, targetClazz);
        }

        Class<?> targetClazz = resolveAnyOfJsonTypeTarget(reader.peekToken().jsonType(), anyOfInfo);
        if (targetClazz == null) {
            _readRawNode(reader);
            return null;
        }
        return _readNode(reader, targetClazz, Types.rawBox(targetClazz), null);
    }

    /// Write

    /**
     * Writes one node to streaming writer.
     */
    public static void writeNode(StreamingWriter writer, Object node) throws IOException {
        Objects.requireNonNull(writer, "writer");
        _writeNode(writer, node);
    }

    /**
     * Writes node recursively as streaming tokens.
     */
    private static void _writeNode(StreamingWriter writer, Object node) throws IOException {
        try {
            if (node == null) {
                writer.writeNull();
                return;
            }

            Class<?> rawClazz = node.getClass();

            if (node instanceof String || node instanceof Character) {
                writer.writeString(node.toString());
                return;
            }
            if (node instanceof Enum) {
                writer.writeString(((Enum<?>) node).name());
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
                boolean veryStart = true;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                    if (veryStart) veryStart = false;
                    else writer.writeObjectComma();
                    String key = entry.getKey().toString();
                    writer.writeName(key);
                    _writeNode(writer, entry.getValue());
                }
                writer.endObject();
                return;
            }

            if (node instanceof List) {
                writer.startArray();
                List<?> list = (List<?>) node;
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) writer.writeArrayComma();
                    _writeNode(writer, list.get(i));
                }
                writer.endArray();
                return;
            }

            if (node instanceof JsonObject) {
                writer.startObject();
                final boolean[] veryStart = { true };
                ((JsonObject) node).forEach((k, v) -> {
                    try {
                        if (veryStart[0]) veryStart[0] = false;
                        else writer.writeObjectComma();
                        writer.writeName(k);
                        _writeNode(writer, v);
                    } catch (IOException e) {
                        throw new BindingException(e);
                    }
                });
                writer.endObject();
                return;
            }

            if (node instanceof JsonArray) {
                writer.startArray();
                JsonArray ja = (JsonArray) node;
                for (int i = 0, len = ja.size(); i < len; i++) {
                    if (i > 0) writer.writeArrayComma();
                    _writeNode(writer, ja.getNode(i));
                }
                writer.endArray();
                return;
            }

            if (node.getClass().isArray()) {
                writer.startArray();
                for (int i = 0, len = Array.getLength(node); i < len; i++) {
                    if (i > 0) writer.writeArrayComma();
                    _writeNode(writer, Array.get(node, i));
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
                    _writeNode(writer, v);
                }
                writer.endArray();
                return;
            }

            NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
            NodeRegistry.ValueCodecInfo vci = ti.valueCodecInfo;
            if (vci != null) {
                Object raw = vci.valueToRaw(node);
                _writeNode(writer, raw);
                return;
            }

            NodeRegistry.PojoInfo pi = ti.pojoInfo;
            if (pi != null) {
                writer.startObject();
                boolean veryStart = true;
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                    if (!entry.getValue().hasGetter()) {
                        continue;
                    }
                    if (veryStart) veryStart = false;
                    else writer.writeObjectComma();
                    String key = entry.getKey();
                    writer.writeName(key);
                    Object vv = entry.getValue().invokeGetter(node);
                    _writeNode(writer, vv);
                }
                writer.endObject();
                return;
            }

            throw new BindingException("Unsupported node type '" + Types.name(node) + "'");

        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to write node of type '" + Types.name(node) + "'", null, e);
        }
    }


    /// Support

    public static Class<?> resolveAnyOfJsonTypeTarget(JsonType jsonType, NodeRegistry.AnyOfInfo anyOfInfo) {
        Class<?> targetClazz = anyOfInfo.resolveByJsonType(jsonType);
        if (targetClazz == null) {
            if (anyOfInfo.onNoMatch == AnyOf.OnNoMatch.FAILBACK_NULL) {
                return null;
            }
            throw new BindingException("AnyOf mapping does not support jsonType=" + jsonType +
                    " for type '" + anyOfInfo.clazz.getName() + "'");
        }
        return targetClazz;
    }

    public static Class<?> resolveAnyOfDiscriminatorTarget(Object discriminatorValue, NodeRegistry.AnyOfInfo anyOfInfo) {
        if (discriminatorValue == null) {
            if (anyOfInfo.onNoMatch == AnyOf.OnNoMatch.FAILBACK_NULL) return null;
            String source = !anyOfInfo.key.isEmpty() ? "key '" + anyOfInfo.key + "'" : "path '" + anyOfInfo.path + "'";
            throw new BindingException("Not found value for discriminator " + source);
        }

        Class<?> targetClazz = anyOfInfo.resolveByWhen(discriminatorValue);
        if (targetClazz == null) {
            if (anyOfInfo.onNoMatch == AnyOf.OnNoMatch.FAILBACK_NULL) return null;
            throw new BindingException("AnyOf discriminator has no matching mapping: value='" + discriminatorValue + "'");
        }
        return targetClazz;
    }

    public static Class<?> resolveSelfDiscriminatorTarget(Object rawNode, NodeRegistry.AnyOfInfo anyOfInfo) {
        if (anyOfInfo.scope != AnyOf.Scope.CURRENT) {
            throw new BindingException("AnyOf scope '" + anyOfInfo.scope + "' is not supported in streaming parser");
        }
        if (!(rawNode instanceof Map)) {
            if (anyOfInfo.onNoMatch == AnyOf.OnNoMatch.FAILBACK_NULL) return null;
            throw new BindingException("Node must be a JSON object, when AnyOf has a SELF discriminator");
        }

        Object discriminatorValue = null;
        if (!anyOfInfo.key.isEmpty()) {
            discriminatorValue = ((Map<?, ?>) rawNode).get(anyOfInfo.key);
        } else if (!anyOfInfo.path.isEmpty()) {
            discriminatorValue = anyOfInfo.compiledPath.getNode(rawNode);
        }
        return resolveAnyOfDiscriminatorTarget(discriminatorValue, anyOfInfo);
    }

    public static void applyDeferredParentAnyOf(Object pojo, NodeRegistry.PojoInfo pi,
                                                NodeRegistry.FieldInfo deferredParentAnyOfFi,
                                                Object deferredParentAnyOfRaw, Object parentAnyOfValue,
                                                Object unsetSentinel) {
        if (deferredParentAnyOfFi == null) {
            return;
        }

        NodeRegistry.AnyOfInfo aoi = deferredParentAnyOfFi.anyOfInfo;
        String parentKey = aoi.key;
        if (parentAnyOfValue == unsetSentinel) {
            Object discriminator = null;
            NodeRegistry.FieldInfo parentFi = pi.aliasFields != null
                    ? pi.aliasFields.get(parentKey) : pi.fields.get(parentKey);
            if (parentFi != null) {
                discriminator = parentFi.invokeGetter(pojo);
            } else if (pi.isJojo) {
                discriminator = ((JsonObject) pojo).getNode(parentKey);
            }
            if (discriminator != null) {
                parentAnyOfValue = discriminator;
            }
        }

        Class<?> targetClazz = aoi.resolveByWhen(parentAnyOfValue == unsetSentinel ? null : parentAnyOfValue);
        Object vv;
        if (targetClazz != null) {
            vv = FacadeFactory.getDefaultNodeFacade().readNode(deferredParentAnyOfRaw, targetClazz);
        } else if (aoi.onNoMatch == AnyOf.OnNoMatch.FAILBACK_NULL) {
            vv = null;
        } else {
            throw new BindingException("AnyOf discriminator has no matching mapping: key='" +
                    aoi.key + "', value='" + (parentAnyOfValue == unsetSentinel ? null : parentAnyOfValue) + "'");
        }
        deferredParentAnyOfFi.invokeSetterIfPresent(pojo, vv);
    }


}
