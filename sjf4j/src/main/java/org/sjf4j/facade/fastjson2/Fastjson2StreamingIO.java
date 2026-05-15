package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.StreamingContext;
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

    public static Object readNode(JSONReader reader, Type type, StreamingContext context) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(context, "context");
        Class<?> rawBox = Types.rawBox(type);
        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawBox);
        return _readNode(reader, type, rawBox, ti, context);
    }

    private static Object _readNode(JSONReader reader, Type type, Class<?> rawBoxed,
                                    NodeRegistry.TypeInfo ti,
                                    StreamingContext context) {
        try {
            NodeRegistry.OneOfInfo oneOfInfo = ti != null ? ti.oneOfInfo : null;
            if (oneOfInfo != null) {
                return readOneOf(reader, oneOfInfo, context);
            }
            if (rawBoxed == Object.class) {
                return _readRawNode(reader);
            }
            StreamingReader.Token token = _peekToken(reader);
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
                throw new BindingException("unexpected token '" + reader.current() + "'");
        }
    }

    private static Map<String, Object> _readRawObject(JSONReader reader) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        if (!reader.nextIfObjectStart()) {
            throw new BindingException("expected token '{', but got " + reader.current());
        }
        while (!reader.nextIfObjectEnd()) {
            map.put(reader.readFieldName(), _readRawNode(reader));
        }
        return map;
    }

    private static List<Object> _readRawArray(JSONReader reader) throws IOException {
        List<Object> list = new ArrayList<>();
        if (!reader.nextIfArrayStart()) {
            throw new BindingException("expected token '[', but was " + reader.current());
        }
        while (!reader.nextIfArrayEnd()) {
            list.add(_readRawNode(reader));
        }
        return list;
    }

    private static Object _readNull(JSONReader reader, Class<?> rawClazz, StreamingContext context)
            throws IOException {
        reader.readNull();

        NodeRegistry.ValueCodecInfo vci = StreamingIO.resolveValueCodecInfo(rawClazz, context);
        if (vci != null) {
            return vci.rawToValue(null);
        }
        return null;
    }

    private static Object _readBoolean(JSONReader reader, Class<?> rawClazz, StreamingContext context)
            throws IOException {
        if (rawClazz == Boolean.class) {
            return reader.readBoolValue();
        }

        NodeRegistry.ValueCodecInfo vci = StreamingIO.resolveValueCodecInfo(rawClazz, context);
        if (vci != null) {
            boolean b = reader.readBoolValue();
            return vci.rawToValue(b);
        }

        throw new BindingException("cannot read boolean value into type '" + rawClazz.getName() + "'");
    }

    private static Object _readNumber(JSONReader reader, Class<?> rawClazz, StreamingContext context)
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

        NodeRegistry.ValueCodecInfo vci = StreamingIO.resolveValueCodecInfo(rawClazz, context);
        if (vci != null) {
            Number n = reader.readNumber();
            return vci.rawToValue(n);
        }

        throw new BindingException("cannot read number value into type '" + rawClazz.getName() + "'");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object _readString(JSONReader reader, Class<?> rawClazz, StreamingContext context)
            throws IOException {
        if (rawClazz == String.class) {
            return reader.readString();
        }
        if (rawClazz == Character.class) {
            String s = reader.readString();
            return !s.isEmpty() ? s.charAt(0) : null;
        }
        if (rawClazz.isEnum()) {
            String s = reader.readString();
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }

        NodeRegistry.ValueCodecInfo vci = StreamingIO.resolveValueCodecInfo(rawClazz, context);
        if (vci != null) {
            String s = reader.readString();
            return vci.rawToValue(s);
        }

        throw new BindingException("cannot read string value into type '" + rawClazz.getName() + "'");
    }

    private static Object _readObject(JSONReader reader, Type type, Class<?> rawClazz,
                                       NodeRegistry.TypeInfo ti,
                                       StreamingContext context)
            throws IOException {
        if (Map.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readMap(reader, rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz), context);
        }

        if (rawClazz == JsonObject.class) {
            return new JsonObject(_readRawObject(reader));
        }

        if (ti == null) {
            ti = NodeRegistry.registerTypeInfo(rawClazz);
        }
        if (ti.hasValueCodecs()) {
            String valueFormat = context.defaultValueFormat(rawClazz);
            NodeRegistry.ValueCodecInfo vci = ti.getValueCodecInfo(valueFormat);
            if (vci != null) {
                Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
                Class<?> valueClazz = Types.rawBox(valueType);
                NodeRegistry.TypeInfo valueTi = NodeRegistry.registerTypeInfo(valueClazz);
                Map<String, Object> map = _readMap(reader, vci.rawClazz, valueType, valueClazz, valueTi, context);
                return vci.rawToValue(map);
            }
        }

        NodeRegistry.PojoInfo pi = ti.pojoInfo;
        if (pi != null && !pi.isJajo) {
            return readPojo(reader, type, rawClazz, pi, context);
        }

        throw new BindingException("cannot read object value into type '" + rawClazz.getName() + "'");
    }

    public static Object readPojo(JSONReader reader, Type ownerType, Class<?> ownerRawClazz,
                                  NodeRegistry.PojoInfo pi, StreamingContext context) throws IOException {
        Objects.requireNonNull(context, "context");
        NodeRegistry.CreatorInfo ci = pi.creatorInfo;
        boolean hasParentOneOf = pi.hasParentScopeOneOf;

        if (!hasParentOneOf && ci.hasNoArgsCreator() && (ci.argNames == null || ci.argNames.length == 0)) {
            Object pojo = ci.newPojoNoArgs();
            Map<String, Object> dynamicMap = null;
            if (!reader.nextIfObjectStart()) {
                throw new BindingException("expected token '{', but got " + reader.current());
            }
            while (!reader.nextIfObjectEnd()) {
                String key = reader.readFieldName();
                NodeRegistry.PropertyInfo fi = pi.aliasProperties != null ? pi.aliasProperties.get(key) : pi.properties.get(key);
                if (fi != null) {
                    Object vv = _readField(reader, fi, ownerType, ownerRawClazz, context);
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
                ((JsonObject) pojo)._dynamicMap(dynamicMap);
            }
            return pojo;
        }

        NodeRegistry.PojoCreationSession session = new NodeRegistry.PojoCreationSession(pi.creatorInfo, pi.propertyCount);
        NodeRegistry.PropertyInfo deferredParentOneOfFi = null;
        Object deferredParentOneOfRaw = null;
        String parentOneOfKey = null;
        Object parentOneOfValue = UNSET;

        if (!reader.nextIfObjectStart()) {
            throw new BindingException("expected token '{', but got " + reader.current());
        }
        while (!reader.nextIfObjectEnd()) {
            String key = reader.readFieldName();

            int argIdx = ci.getArgIndexOrAlias(key);
            if (argIdx >= 0) {
                Type argType = Types.resolveMemberType(ownerType, ownerRawClazz, ci.argTypes[argIdx]);
                Class<?> argRaw = Types.rawBox(argType);
                NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(argRaw);
                NodeRegistry.ValueCodecInfo argVci = ci.argValueCodecs[argIdx];
                if (argVci == null && ti.hasValueCodecs()) {
                    String valueFormat = context.defaultValueFormat(argRaw);
                    argVci = ti.getValueCodecInfo(valueFormat);
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

            NodeRegistry.PropertyInfo fi = pi.aliasProperties != null ? pi.aliasProperties.get(key) : pi.properties.get(key);
            if (fi != null) {
                Object vv;
                NodeRegistry.OneOfInfo fieldOneOf = fi.oneOfInfo;
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
                    Class<?> targetClazz = fieldOneOf.resolveByWhen(parentOneOfValue == UNSET ? null : parentOneOfValue);
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
                reader.skipValue();
            }
        }

        Object pojo = session.finish();
        StreamingIO.applyDeferredParentOneOf(pojo, pi, deferredParentOneOfFi, deferredParentOneOfRaw,
                parentOneOfValue, UNSET, context);
        return pojo;
    }

    private static Object _readArray(JSONReader reader, Type type, Class<?> rawClazz,
                                      NodeRegistry.TypeInfo ti,
                                      StreamingContext context)
            throws IOException {
        if (List.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readList(reader, rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz), context);
        }

        if (rawClazz == JsonArray.class) {
            return new JsonArray(_readRawArray(reader));
        }

        if (Set.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readSet(reader, rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz), context);
        }

        if (rawClazz.isArray()) {
            Class<?> compType = rawClazz.getComponentType();
            Class<?> valueClazz = Types.box(compType);
            return _readArray(reader, rawClazz, compType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz), context);
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            JsonArray ja = (JsonArray) NodeRegistry.registerPojoOrElseThrow(rawClazz).creatorInfo.forceNewPojo();
            Class<?> elemType = ja.elementType();
            Class<?> elemRaw = Types.box(elemType);
            NodeRegistry.TypeInfo elemTi = NodeRegistry.registerTypeInfo(elemRaw);
            if (!reader.nextIfArrayStart()) {
                throw new BindingException("expected token '[', but was " + reader.current());
            }
            while (!reader.nextIfArrayEnd()) {
                Object value = _readNode(reader, elemType, elemRaw, elemTi, context);
                ja.add(value);
            }
            return ja;
        }

        if (ti == null) {
            ti = NodeRegistry.registerTypeInfo(rawClazz);
        }
        NodeRegistry.ValueCodecInfo vci = ti.hasValueCodecs()
                ? ti.getValueCodecInfo(context.defaultValueFormat(rawClazz))
                : null;
        if (vci != null) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            List<Object> list = _readList(reader, vci.rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz), context);
            return vci.rawToValue(list);
        }

        throw new BindingException("cannot read array value into type '" + rawClazz.getName() + "'");
    }

    private static Object _readField(JSONReader reader, NodeRegistry.PropertyInfo fi,
                                     Type ownerType, Class<?> ownerRawClazz,
                                     StreamingContext context)
            throws IOException {
        Type fieldType = Types.resolveMemberType(ownerType, ownerRawClazz, fi.type);
        Class<?> fieldRaw = fieldType == fi.type ? fi.rawClazz : Types.rawBox(fieldType);

        NodeRegistry.OneOfInfo fieldOneOf = fi.oneOfInfo;
        if (fieldOneOf == null && fieldRaw != fi.rawClazz) {
            fieldOneOf = NodeRegistry.registerTypeInfo(fieldRaw).oneOfInfo;
        }
        if (fieldOneOf != null) {
            return readOneOf(reader, fieldOneOf, context);
        }

        if (fi.resolvedValueCodec != null) {
            return _readValueWithCodec(reader, fieldType, fieldRaw, fi.resolvedValueCodec, context);
        }

        switch (fieldType == fi.type ? fi.containerKind : NodeRegistry.PropertyInfo.ContainerKind.NONE) {
            case MAP:
                return _readMap(reader, fi.rawClazz, fi.argType, fi.argRawClazz,
                        NodeRegistry.registerTypeInfo(fi.argRawClazz), context);
            case LIST:
                return _readList(reader, fi.rawClazz, fi.argType, fi.argRawClazz,
                        NodeRegistry.registerTypeInfo(fi.argRawClazz), context);
            case SET:
                return _readSet(reader, fi.rawClazz, fi.argType, fi.argRawClazz,
                        NodeRegistry.registerTypeInfo(fi.argRawClazz), context);
            case ARRAY:
                return _readArray(reader, fi.rawClazz, fi.argType, fi.argRawClazz,
                        NodeRegistry.registerTypeInfo(fi.argRawClazz), context);
            default:
                return _readNode(reader, fieldType, fieldRaw, null, context);
        }
    }

    private static Object _readValueWithCodec(JSONReader reader,
                                              Type type,
                                              Class<?> rawClazz,
                                              NodeRegistry.ValueCodecInfo valueCodecInfo,
                                              StreamingContext context) throws IOException {
        switch (_peekToken(reader)) {
            case START_OBJECT: {
                Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
                Class<?> valueClazz = Types.rawBox(valueType);
                Map<String, Object> map = _readMap(reader, valueCodecInfo.rawClazz, valueType, valueClazz,
                        NodeRegistry.registerTypeInfo(valueClazz), context);
                return valueCodecInfo.rawToValue(map);
            }
            case START_ARRAY: {
                Type valueType = Types.resolveTypeArgument(type, List.class, 0);
                Class<?> valueClazz = Types.rawBox(valueType);
                List<Object> list = _readList(reader, valueCodecInfo.rawClazz, valueType, valueClazz,
                        NodeRegistry.registerTypeInfo(valueClazz), context);
                return valueCodecInfo.rawToValue(list);
            }
            case STRING:
                return valueCodecInfo.rawToValue(reader.readString());
            case NUMBER:
                return valueCodecInfo.rawToValue(reader.readNumber());
            case BOOLEAN:
                return valueCodecInfo.rawToValue(reader.readBoolValue());
            case NULL:
                reader.readNull();
                return valueCodecInfo.rawToValue(null);
            default:
                throw new BindingException("cannot read value into type '" + rawClazz.getName() + "'");
        }
    }

    private static Map<String, Object> _readMap(JSONReader reader, Class<?> mapClazz,
                                                 Type valueType, Class<?> valueClazz,
                                                 NodeRegistry.TypeInfo valueTi,
                                                 StreamingContext context)
            throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        Map<String, Object> map = mapClazz == Object.class || mapClazz == Map.class || mapClazz == LinkedHashMap.class
                ? new LinkedHashMap<>()
                : NodeRegistry.newMapContainer(mapClazz, false);
        if (!reader.nextIfObjectStart()) {
            throw new BindingException("expected token '{', but was " + reader.current());
        }
        while (!reader.nextIfObjectEnd()) {
            String key = reader.readFieldName();
            Object value = _readNode(reader, valueType, valueClazz, valueTi, context);
            map.put(key, value);
        }
        return map;
    }

    private static List<Object> _readList(JSONReader reader, Class<?> listClazz,
                                           Type valueType, Class<?> valueClazz,
                                           NodeRegistry.TypeInfo valueTi,
                                           StreamingContext context)
            throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        List<Object> list = listClazz == Object.class || listClazz == List.class || listClazz == ArrayList.class
                ? new ArrayList<>()
                : NodeRegistry.newListContainer(listClazz, false);
        if (!reader.nextIfArrayStart()) {
            throw new BindingException("expected token '[', but was " + reader.current());
        }
        while (!reader.nextIfArrayEnd()) {
            Object value = _readNode(reader, valueType, valueClazz, valueTi, context);
            list.add(value);
        }
        return list;
    }

    private static Set<Object> _readSet(JSONReader reader, Class<?> setClazz,
                                         Type valueType, Class<?> valueClazz,
                                         NodeRegistry.TypeInfo valueTi,
                                         StreamingContext context)
            throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        Set<Object> set = setClazz == Object.class || setClazz == Set.class || setClazz == LinkedHashSet.class
                ? new LinkedHashSet<>()
                : NodeRegistry.newSetContainer(setClazz, false);
        if (!reader.nextIfArrayStart()) {
            throw new BindingException("expected token '[', but was " + reader.current());
        }
        while (!reader.nextIfArrayEnd()) {
            Object value = _readNode(reader, valueType, valueClazz, valueTi, context);
            set.add(value);
        }
        return set;
    }

    private static Object _readArray(JSONReader reader, Class<?> rawClazz,
                                      Type valueType, Class<?> valueClazz,
                                      NodeRegistry.TypeInfo valueTi,
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

    public static Object readOneOf(JSONReader reader, NodeRegistry.OneOfInfo anyOfInfo,
                                   StreamingContext context) throws IOException {
        Objects.requireNonNull(context, "context");
        try {
            if (anyOfInfo.hasDiscriminator) {
                Object rawNode = _readRawNode(reader);
                Class<?> targetClazz = StreamingIO.resolveCurrentDiscriminatorTarget(rawNode, anyOfInfo);
                if (targetClazz == null) return null;
                return context.nodeFacade.readNode(rawNode, targetClazz);
            }

            Class<?> targetClazz = StreamingIO.resolveOneOfJsonTypeTarget(_peekToken(reader).jsonType(), anyOfInfo);
            if (targetClazz == null) {
                _readRawNode(reader);
                return null;
            }

            return _readNode(reader, targetClazz, Types.rawBox(targetClazz), null, context);
        } catch (BindingException e) {
            throw e;
        } catch (IOException e) {
            throw new BindingException("failed to peek token for OneOf", null, e);
        }
    }

    /// Reader

    private static StreamingReader.Token _peekToken(JSONReader reader) throws IOException {
        return Fastjson2Reader.mappingToken(reader.current());
    }

    /// Write

    public static void writeNode(JSONWriter writer, Object node, StreamingContext context) throws IOException {
        Objects.requireNonNull(writer, "writer");
        Objects.requireNonNull(context, "context");
        _writeNode(writer, node, context);
    }

    private static void _writeNode(JSONWriter writer, Object node, StreamingContext context) throws IOException {
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
                    Object value = entry.getValue();
                    if (value == null && !context.includeNulls) continue;
                    String key = entry.getKey().toString();
                    writer.writeName(key);
                    writer.writeColon();
                    _writeNode(writer, value, context);
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
                    _writeNode(writer, list.get(i), context);
                }
                writer.endArray();
                return;
            }

            if (rawClazz == JsonObject.class) {
                writer.startObject();
                for (Map.Entry<String, Object> entry : ((JsonObject) node).entrySet()) {
                    Object value = entry.getValue();
                    if (value == null && !context.includeNulls) continue;
                    writer.writeName(entry.getKey());
                    writer.writeColon();
                    _writeNode(writer, value, context);
                }
                writer.endObject();
                return;
            }

            if (node instanceof JsonArray) {
                writer.startArray();
                JsonArray ja = (JsonArray) node;
                for (int i = 0, len = ja.size(); i < len; i++) {
                    if (i > 0) writer.writeComma();
                    _writeNode(writer, ja.getNode(i), context);
                }
                writer.endArray();
                return;
            }

            if (rawClazz.isArray()) {
                writer.startArray();
                int len = Array.getLength(node);
                for (int i = 0; i < len; i++) {
                    if (i > 0) writer.writeComma();
                    _writeNode(writer, Array.get(node, i), context);
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
                    _writeNode(writer, v, context);
                }
                writer.endArray();
                return;
            }

            NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
            if (ti.hasValueCodecs()) {
                String valueFormat = context.defaultValueFormat(rawClazz);
                NodeRegistry.ValueCodecInfo vci = ti.getValueCodecInfo(valueFormat);
                if (vci != null) {
                    Object raw = vci.valueToRaw(node);
                    _writeNode(writer, raw, context);
                    return;
                }
            }

            NodeRegistry.PojoInfo pi = ti.pojoInfo;
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

    public static void writePojo(JSONWriter writer, Object node, NodeRegistry.PojoInfo pi,
                                 StreamingContext context) throws IOException {
        writer.startObject();
        for (Map.Entry<String, NodeRegistry.PropertyInfo> entry : pi.readableProperties.entrySet()) {
            Object vv = entry.getValue().invokeGetter(node);
            if (vv == null && !context.includeNulls) continue;
            String key = entry.getKey();
            writer.writeName(key);
            writer.writeColon();
            if (vv == null) {
                writer.writeNull();
            } else {
                NodeRegistry.PropertyInfo fi = entry.getValue();
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
                    writer.writeName(entry.getKey());
                    writer.writeColon();
                    _writeNode(writer, value, context);
                }
            }
        }
        writer.endObject();
    }


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

    /// Support

}
