package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.FacadeFactory;
import org.sjf4j.facade.StreamingIO;
import org.sjf4j.facade.StreamingReader;
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
 * Fastjson2 streaming implementation aligned with {@link org.sjf4j.facade.StreamingIO} semantics.
 */
public class Fastjson2StreamingIO {
    private static final Object UNSET = new Object();

    /// Read

    /**
     * Reads one node from Fastjson2 reader into target type.
     */
    public static Object readNode(JSONReader reader, Type type) {
        Class<?> rawBox = Types.rawBox(type);
        NodeRegistry.AnyOfInfo anyOfInfo = NodeRegistry.registerTypeInfo(rawBox).anyOfInfo;
        return _readNode(reader, type, rawBox, anyOfInfo);
    }

    /**
     * Reads next token and dispatches to typed node readers.
     */
    private static Object _readNode(JSONReader reader, Type type, Class<?> rawClazz,
                                    NodeRegistry.AnyOfInfo anyOfInfo) {
        try {
            if (anyOfInfo != null) {
                return readAnyOf(reader, anyOfInfo);
            }
            if (rawClazz == Object.class) {
                return _readRawNode(reader);
            }
            StreamingReader.Token token = _peekToken(reader);
            switch (token) {
                case START_OBJECT:
                    return _readObject(reader, type, rawClazz);
                case START_ARRAY:
                    return _readArray(reader, type, rawClazz);
                case STRING:
                    return _readString(reader, rawClazz);
                case NUMBER:
                    return _readNumber(reader, rawClazz);
                case BOOLEAN:
                    return _readBoolean(reader, rawClazz);
                case NULL:
                    return _readNull(reader, rawClazz);
                default:
                    throw new JsonException("Unexpected token '" + token + "'");
            }
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to read streaming into '" + type + "'", null, e);
        }
    }

    private static Object _readRawNode(JSONReader reader) throws IOException {
        switch (_peekToken(reader)) {
            case START_OBJECT:
                return _readRawObject(reader);
            case START_ARRAY:
                return _readRawArray(reader);
            case STRING:
                return reader.readString();
            case NUMBER:
                return reader.readNumber();
            case BOOLEAN:
                return reader.readBoolValue();
            case NULL:
                reader.readNull();
                return null;
            default:
                throw new JsonException("Unexpected token '" + reader.current() + "'");
        }
    }

    // Keep untyped object/array hot paths direct. Reusing typed container readers adds
    // extra per-value dispatch on the common JOJO unknown-field path.
    private static Map<String, Object> _readRawObject(JSONReader reader) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        if (!reader.nextIfObjectStart()) {
            throw new JsonException("Expected token '{', but got " + reader.current());
        }
        while (!reader.nextIfObjectEnd()) {
            map.put(reader.readFieldName(), _readRawNode(reader));
        }
        return map;
    }

    private static List<Object> _readRawArray(JSONReader reader) throws IOException {
        List<Object> list = new ArrayList<>();
        if (!reader.nextIfArrayStart()) {
            throw new JsonException("Expected token '[', but was " + reader.current());
        }
        while (!reader.nextIfArrayEnd()) {
            list.add(_readRawNode(reader));
        }
        return list;
    }

    private static Object _readNull(JSONReader reader, Class<?> rawClazz)
            throws IOException {
        reader.readNull();

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            return vci.rawToValue(null);
        }
        return null;
    }

    private static Object _readBoolean(JSONReader reader, Class<?> rawClazz)
            throws IOException {
        if (rawClazz == Boolean.class) {
            return reader.readBoolValue();
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            boolean b = reader.readBoolValue();
            return vci.rawToValue(b);
        }

        throw new BindingException("Cannot read boolean value into type " + rawClazz.getName());
    }

    private static Object _readNumber(JSONReader reader, Class<?> rawClazz)
            throws IOException {
        if (rawClazz == Number.class) {
            return reader.readNumber();
        }
        if (rawClazz == Integer.class) return reader.readInt32Value();
        if (rawClazz == Long.class) return reader.readInt64Value();
        if (rawClazz == Float.class) return reader.readFloatValue();
        if (rawClazz == Double.class) return reader.readDoubleValue();
        if (rawClazz == Short.class) return reader.readInt16Value();
        if (rawClazz == Byte.class) return reader.readInt8Value();
        if (rawClazz == BigInteger.class) return reader.readBigInteger();
        if (rawClazz == BigDecimal.class) return reader.readBigDecimal();

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            Number n = reader.readNumber();
            return vci.rawToValue(n);
        }

        throw new BindingException("Cannot read number value into type " + rawClazz.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object _readString(JSONReader reader, Class<?> rawClazz)
            throws IOException {
        if (rawClazz == String.class) {
            return reader.readString();
        }
        if (rawClazz == Character.class) {
            String s = reader.readString();
            return s.length() > 0 ? s.charAt(0) : null;
        }
        if (rawClazz.isEnum()) {
            String s = reader.readString();
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            String s = reader.readString();
            return vci.rawToValue(s);
        }

        throw new BindingException("Cannot read string value into type " + rawClazz.getName());
    }

    /**
     * Reads object token into Map/JsonObject/POJO target.
     */
    private static Object _readObject(JSONReader reader, Type type, Class<?> rawClazz)
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
        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
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

        throw new BindingException("Cannot read object value into type " + rawClazz.getName());
    }

    public static Object readPojo(JSONReader reader, Type ownerType, Class<?> ownerRawClazz, NodeRegistry.PojoInfo pi)
            throws IOException {
        NodeRegistry.CreatorInfo ci = pi.creatorInfo;
        boolean hasParentAnyOf = pi.hasParentScopeAnyOf;

        if (!hasParentAnyOf && ci.hasNoArgsCreator() && (ci.argNames == null || ci.argNames.length == 0)) {
            Object pojo = ci.newPojoNoArgs();
            Map<String, Object> dynamicMap = null;
            if (!reader.nextIfObjectStart()) {
                throw new JsonException("Expected token '{', but got " + reader.current());
            }
            while (!reader.nextIfObjectEnd()) {
                String key = reader.readFieldName();
                NodeRegistry.FieldInfo fi = pi.aliasFields != null ? pi.aliasFields.get(key) : pi.fields.get(key);
                if (fi != null) {
                    Object vv = _readField(reader, fi, ownerType, ownerRawClazz);
                    fi.invokeSetterIfPresent(pojo, vv);
                } else if (pi.isJojo && pi.readDynamic) {
                    if (dynamicMap == null) {
                        dynamicMap = new LinkedHashMap<>();
                    }
                    dynamicMap.put(key, _readRawNode(reader));
                } else {
                    reader.skipValue();
                }
            }
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

        if (!reader.nextIfObjectStart()) {
            throw new JsonException("Expected token '{', but got " + reader.current());
        }
        while (!reader.nextIfObjectEnd()) {
            String key = reader.readFieldName();

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

            if (pi.isJojo && pi.readDynamic) {
                Object vv = _readRawNode(reader);
                session.acceptResolvedJsonEntry(-1, key, vv);
                if (parentAnyOfKey != null && parentAnyOfKey.equals(key)) {
                    parentAnyOfValue = vv;
                }
            } else {
                reader.skipValue();
            }
        }

        Object pojo = session.finishField();
        StreamingIO.applyDeferredParentAnyOf(pojo, pi, deferredParentAnyOfFi, deferredParentAnyOfRaw,
                parentAnyOfValue, UNSET);
        return pojo;
    }

    /**
     * Reads array token into List/JsonArray/array/Set target.
     */
    private static Object _readArray(JSONReader reader, Type type, Class<?> rawClazz)
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
            if (!reader.nextIfArrayStart()) {
                throw new JsonException("Expected token '[', but was " + reader.current());
            }
            while (!reader.nextIfArrayEnd()) {
                NodeRegistry.AnyOfInfo elemAnyOf = NodeRegistry.registerTypeInfo(elemRaw).anyOfInfo;
                Object value = _readNode(reader, elemType, elemRaw, elemAnyOf);
                ja.add(value);
            }
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

        throw new BindingException("Cannot read array value into type " + rawClazz.getName());
    }

    private static Object _readField(JSONReader reader, NodeRegistry.FieldInfo fi,
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

    private static Map<String, Object> _readMap(JSONReader reader, Class<?> mapClazz,
                                                Type valueType, Class<?> valueClazz,
                                                NodeRegistry.AnyOfInfo valueAnyOf)
            throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        Map<String, Object> map = mapClazz == Object.class || mapClazz == Map.class || mapClazz == LinkedHashMap.class
                ? new LinkedHashMap<>()
                : NodeRegistry.newMapContainer(mapClazz, false);
        if (!reader.nextIfObjectStart()) {
            throw new JsonException("Expected token '{', but was " + reader.current());
        }
        while (!reader.nextIfObjectEnd()) {
            String key = reader.readFieldName();
            Object value = _readNode(reader, valueType, valueClazz, valueAnyOf);
            map.put(key, value);
        }
        return map;
    }

    private static List<Object> _readList(JSONReader reader, Class<?> listClazz,
                                          Type valueType, Class<?> valueClazz,
                                          NodeRegistry.AnyOfInfo valueAnyOf)
            throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        List<Object> list = listClazz == Object.class || listClazz == List.class || listClazz == ArrayList.class
                ? new ArrayList<>()
                : NodeRegistry.newListContainer(listClazz, false);
        if (!reader.nextIfArrayStart()) {
            throw new JsonException("Expected token '[', but was " + reader.current());
        }
        while (!reader.nextIfArrayEnd()) {
            Object value = _readNode(reader, valueType, valueClazz, valueAnyOf);
            list.add(value);
        }
        return list;
    }

    private static Set<Object> _readSet(JSONReader reader, Class<?> setClazz,
                                        Type valueType, Class<?> valueClazz,
                                        NodeRegistry.AnyOfInfo valueAnyOf)
            throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        Set<Object> set = setClazz == Object.class || setClazz == Set.class || setClazz == LinkedHashSet.class
                ? new LinkedHashSet<>()
                : NodeRegistry.newSetContainer(setClazz, false);
        if (!reader.nextIfArrayStart()) {
            throw new JsonException("Expected token '[', but was " + reader.current());
        }
        while (!reader.nextIfArrayEnd()) {
            Object value = _readNode(reader, valueType, valueClazz, valueAnyOf);
            set.add(value);
        }
        return set;
    }

    private static Object _readArray(JSONReader reader, Class<?> rawClazz,
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

    public static Object readAnyOf(JSONReader reader, NodeRegistry.AnyOfInfo anyOfInfo) {
        try {
            if (anyOfInfo.hasDiscriminator) {
                Object rawNode = _readRawNode(reader);
                Class<?> targetClazz = StreamingIO.resolveSelfDiscriminatorTarget(rawNode, anyOfInfo);
                if (targetClazz == null) return null;
                return FacadeFactory.defaultNodeFacade().readNode(rawNode, targetClazz);
            }

            Class<?> targetClazz;
            targetClazz = StreamingIO.resolveAnyOfJsonTypeTarget(_peekToken(reader).jsonType(), anyOfInfo);
            if (targetClazz == null) {
                _readRawNode(reader);
                return null;
            }

            return _readNode(reader, targetClazz, Types.rawBox(targetClazz), null);
        } catch (BindingException e) {
            throw e;
        } catch (IOException e) {
            throw new BindingException("Failed to peek token for AnyOf", null, e);
        }
    }

    /// Reader

    /**
     * Peeks token kind from current Fastjson2 reader state.
     */
    private static StreamingReader.Token _peekToken(JSONReader reader) throws IOException {
        return Fastjson2Reader.mappingToken(reader.current());
    }

    /// Write

    /**
     * Writes one node to Fastjson2 writer.
     */
    public static void writeNode(JSONWriter writer, Object node) throws IOException {
        Objects.requireNonNull(writer, "writer");
        _writeNode(
                writer,
                node
        );
    }

    /**
     * Writes node recursively as JSON tokens.
     */
    private static void _writeNode(JSONWriter writer, Object node) throws IOException {
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
                _writeNumber(writer, (Number) node);
                return;
            }

            if (node instanceof Boolean) {
                writer.writeBool((Boolean) node);
                return;
            }

            if (node instanceof Map) {
                writer.startObject();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                    String key = entry.getKey().toString();
                    writer.writeName(key);
                    writer.writeColon();
                    _writeNode(writer, entry.getValue());
                }
                writer.endObject();
                return;
            }

            if (node instanceof List) {
                writer.startArray();
                List<?> list = (List<?>) node;
                for (int i = 0, len = list.size(); i < len; i++) {
                    if (i > 0) {
                        writer.writeComma();
                    }
                    _writeNode(writer, list.get(i));
                }
                writer.endArray();
                return;
            }

            if (node instanceof JsonObject) {
                if (rawClazz != JsonObject.class) {
                    NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
                    if (!pi.writeDynamic) {
                        writer.startObject();
                        for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                            writer.writeName(entry.getKey());
                            writer.writeColon();
                            _writeNode(writer, entry.getValue().invokeGetter(node));
                        }
                        writer.endObject();
                        return;
                    }
                }
                writer.startObject();
                ((JsonObject) node).forEach((k, v) -> {
                    try {
                        writer.writeName(k);
                        writer.writeColon();
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
                    if (i > 0) {
                        writer.writeComma();
                    }
                    _writeNode(writer, ja.getNode(i));
                }
                writer.endArray();
                return;
            }

            if (rawClazz.isArray()) {
                writer.startArray();
                int len = Array.getLength(node);
                for (int i = 0; i < len; i++) {
                    if (i > 0) {
                        writer.writeComma();
                    }
                    _writeNode(writer, Array.get(node, i));
                }
                writer.endArray();
                return;
            }

            if (node instanceof Set) {
                writer.startArray();
                boolean veryStart = true;
                for (Object v : (Set<?>) node) {
                    if (veryStart) {
                        veryStart = false;
                    } else {
                        writer.writeComma();
                    }
                    _writeNode(writer, v);
                }
                writer.endArray();
                return;
            }

            NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
            NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
            if (vci != null) {
                Object raw = vci.valueToRaw(node);
                _writeNode(writer, raw);
                return;
            }

            NodeRegistry.PojoInfo pi = ti.pojoInfo;
            if (pi != null) {
                writer.startObject();
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                    if (!entry.getValue().hasGetter()) {
                        continue;
                    }
                    String key = entry.getKey();
                    writer.writeName(key);
                    writer.writeColon();
                    Object vv = entry.getValue().invokeGetter(node);
                    _writeNode(writer, vv);
                }
                writer.endObject();
                return;
            }

            throw new BindingException("Unsupported node type '" + rawClazz.getName() + "'");
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to write node of type '" + Types.name(node) + "'", null, e);
        }
    }

    /**
     * Writes numeric value with precise Fastjson2 API mapping.
     */
    private static void _writeNumber(JSONWriter writer, Number value) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            writer.writeInt64(value.longValue());
        } else if (value instanceof Float || value instanceof Double) {
            writer.writeDouble(value.doubleValue());
        } else if (value instanceof BigInteger) {
            writer.writeBigInt((BigInteger) value);
        } else if (value instanceof BigDecimal) {
            writer.writeDecimal((BigDecimal) value);
        } else {
            writer.writeInt64(value.longValue());
        }
    }
}
