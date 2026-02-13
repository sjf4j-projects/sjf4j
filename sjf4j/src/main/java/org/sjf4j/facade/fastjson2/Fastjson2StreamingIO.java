package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Types;
import org.sjf4j.path.PathSegment;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fastjson2 specialized streaming implementation that mirrors StreamingIO semantics
 * (including BindingException/PathSegment behavior) while keeping fastjson2-native
 * reader/writer hot paths.
 */
public class Fastjson2StreamingIO {

    /// Read

    public static Object readNode(JSONReader reader, Type type) throws IOException {
        return _readNode(
                reader,
                type,
                Types.rawBox(type),
                Sjf4jConfig.global().isBindingPath() ? PathSegment.Root.INSTANCE : null
        );
    }

    private static Object _readNode(JSONReader reader, Type type, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        try {
            StreamingReader.Token token = peekToken(reader);
            switch (token) {
                case START_OBJECT:
                    return _readObject(reader, type, rawClazz, ps);
                case START_ARRAY:
                    return _readArray(reader, type, rawClazz, ps);
                case STRING:
                    return _readString(reader, rawClazz, ps);
                case NUMBER:
                    return _readNumber(reader, rawClazz, ps);
                case BOOLEAN:
                    return _readBoolean(reader, rawClazz, ps);
                case NULL:
                    return _readNull(reader, rawClazz, ps);
                default:
                    throw new JsonException("Unexpected token '" + token + "'");
            }
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to read streaming into '" + type + "'", ps, e);
        }
    }

    private static Object _readNull(JSONReader reader, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        reader.readNull();

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            return vci.decode(null);
        }
        return null;
    }

    private static Object _readBoolean(JSONReader reader, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        if (rawClazz == Object.class || rawClazz == Boolean.class) {
            return reader.readBoolValue();
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            boolean b = reader.readBoolValue();
            return vci.decode(b);
        }

        throw new BindingException("Cannot read boolean value into type " + rawClazz.getName(), ps);
    }

    private static Object _readNumber(JSONReader reader, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        if (rawClazz == Object.class || rawClazz == Number.class) {
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

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Number n = reader.readNumber();
            return vci.decode(n);
        }

        throw new BindingException("Cannot read number value into type " + rawClazz.getName(), ps);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object _readString(JSONReader reader, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        if (rawClazz == Object.class || rawClazz == String.class) {
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

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            String s = reader.readString();
            return vci.decode(s);
        }

        throw new BindingException("Cannot read string value into type " + rawClazz.getName(), ps);
    }

    private static Object _readObject(JSONReader reader, Type type, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        if (rawClazz == Object.class || rawClazz == Map.class) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readMapWithValueType(reader, rawClazz, valueType, valueClazz, ps);
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            if (!reader.nextIfObjectStart()) {
                throw new JsonException("Expected token '{', but got " + reader.current());
            }
            while (!reader.nextIfObjectEnd()) {
                String key = reader.readFieldName();
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                Object value = _readNode(reader, Object.class, Object.class, cps);
                jo.put(key, value);
            }
            return jo;
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
        if (ti.valueCodecInfo != null) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueClazz = Types.rawBox(valueType);
            Map<String, Object> map = _readMapWithValueType(reader, rawClazz, valueType, valueClazz, ps);
            return ti.valueCodecInfo.decode(map);
        }

        NodeRegistry.PojoInfo pi = ti.pojoInfo;
        if (pi != null && !pi.isJajo) {
            NodeRegistry.CreatorInfo ci = pi.creatorInfo;
            Object pojo = ci.noArgsCtorHandle == null ? null : ci.newPojoNoArgs();
            Object[] args = ci.noArgsCtorHandle == null ? new Object[ci.argNames.length] : null;
            int remainingArgs = ci.noArgsCtorHandle == null ? args.length : 0;
            int pendingSize = 0;
            NodeRegistry.FieldInfo[] pendingFields = null;
            Object[] pendingValues = null;
            Map<String, Object> dynamicMap = null;

            if (!reader.nextIfObjectStart()) {
                throw new JsonException("Expected token '{', but got " + reader.current());
            }
            while (!reader.nextIfObjectEnd()) {
                String key = reader.readFieldName();

                int argIdx = -1;
                if (pojo == null) {
                    argIdx = ci.getArgIndex(key);
                    if (argIdx < 0 && ci.aliasMap != null) {
                        String origin = ci.aliasMap.get(key);
                        if (origin != null) {
                            argIdx = ci.getArgIndex(origin);
                        }
                    }
                }
                if (argIdx >= 0) {
                    Type argType = ci.argTypes[argIdx];
                    assert args != null;
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    args[argIdx] = _readNode(reader, argType, Types.rawBox(argType), cps);
                    remainingArgs--;
                    if (remainingArgs == 0) {
                        pojo = ci.newPojoWithArgs(args);
                        for (int i = 0; i < pendingSize; i++) {
                            pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                        }
                        pendingSize = 0;
                    }
                    continue;
                }

                NodeRegistry.FieldInfo fi = pi.aliasFields != null ? pi.aliasFields.get(key) : pi.fields.get(key);
                if (fi != null) {
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    Object vv = _readField(reader, fi, cps);
                    if (pojo != null) {
                        fi.invokeSetterIfPresent(pojo, vv);
                    } else {
                        if (pendingFields == null) {
                            int cap = pi.fieldCount;
                            pendingFields = new NodeRegistry.FieldInfo[cap];
                            pendingValues = new Object[cap];
                        }
                        pendingFields[pendingSize] = fi;
                        pendingValues[pendingSize] = vv;
                        pendingSize++;
                    }
                    continue;
                }

                if (pi.isJojo) {
                    if (dynamicMap == null) {
                        dynamicMap = Sjf4jConfig.global().mapSupplier.create();
                    }
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    Object vv = _readNode(reader, Object.class, Object.class, cps);
                    dynamicMap.put(key, vv);
                } else {
                    reader.skipValue();
                }
            }

            if (pojo == null) {
                pojo = ci.newPojoWithArgs(args);
                for (int i = 0; i < pendingSize; i++) {
                    pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                }
            }
            if (pi.isJojo) {
                ((JsonObject) pojo).setDynamicMap(dynamicMap);
            }
            return pojo;
        }

        throw new BindingException("Cannot read object value into type " + rawClazz.getName(), ps);
    }

    private static Object _readArray(JSONReader reader, Type type, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        if (rawClazz == Object.class || rawClazz == List.class) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readListWithElementType(reader, rawClazz, valueType, valueClazz, ps);
        }

        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            int i = 0;
            if (!reader.nextIfArrayStart()) {
                throw new JsonException("Expected token '[', but was " + reader.current());
            }
            while (!reader.nextIfArrayEnd()) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(reader, Object.class, Object.class, cps);
                ja.add(value);
            }
            return ja;
        }

        if (rawClazz == Set.class) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readSetWithElementType(reader, rawClazz, valueType, valueClazz, ps);
        }

        if (rawClazz.isArray()) {
            Class<?> compType = rawClazz.getComponentType();
            Class<?> valueClazz = Types.box(compType);
            List<Object> list = new ArrayList<>();
            int i = 0;
            if (!reader.nextIfArrayStart()) {
                throw new JsonException("Expected token '[', but was " + reader.current());
            }
            while (!reader.nextIfArrayEnd()) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(reader, compType, valueClazz, cps);
                list.add(value);
            }

            Object array = Array.newInstance(compType, list.size());
            for (int j = 0, len = list.size(); j < len; j++) {
                Array.set(array, j, list.get(j));
            }
            return array;
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            JsonArray ja = (JsonArray) NodeRegistry.registerPojoOrElseThrow(rawClazz).creatorInfo.forceNewPojo();
            Class<?> elemType = ja.elementType();
            Class<?> elemRaw = Types.box(elemType);
            int i = 0;
            if (!reader.nextIfArrayStart()) {
                throw new JsonException("Expected token '[', but was " + reader.current());
            }
            while (!reader.nextIfArrayEnd()) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(reader, elemType, elemRaw, cps);
                ja.add(value);
            }
            return ja;
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            List<Object> list = _readListWithElementType(reader, rawClazz, valueType, valueClazz, ps);
            return vci.decode(list);
        }

        throw new BindingException("Cannot read array value into type " + rawClazz.getName(), ps);
    }

    private static Object _readField(JSONReader reader, NodeRegistry.FieldInfo fi, PathSegment ps)
            throws IOException {
        switch (fi.containerKind) {
            case MAP:
                return _readMapWithValueType(reader, fi.rawType, fi.argType, fi.argRawType, ps);
            case LIST:
                return _readListWithElementType(reader, fi.rawType, fi.argType, fi.argRawType, ps);
            case SET:
                return _readSetWithElementType(reader, fi.rawType, fi.argType, fi.argRawType, ps);
            default:
                return _readNode(reader, fi.type, fi.rawType, ps);
        }
    }

    private static Map<String, Object> _readMapWithValueType(JSONReader reader, Class<?> rawClazz,
                                                              Type valueType, Class<?> valueClazz, PathSegment ps)
            throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
        if (!reader.nextIfObjectStart()) {
            throw new JsonException("Expected token '{', but was " + reader.current());
        }
        while (!reader.nextIfObjectEnd()) {
            String key = reader.readFieldName();
            PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
            Object value = _readNode(reader, valueType, valueClazz, cps);
            map.put(key, value);
        }
        return map;
    }

    private static List<Object> _readListWithElementType(JSONReader reader, Class<?> rawClazz,
                                                          Type valueType, Class<?> valueClazz, PathSegment ps)
            throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        List<Object> list = new ArrayList<>();
        int i = 0;
        if (!reader.nextIfArrayStart()) {
            throw new JsonException("Expected token '[', but was " + reader.current());
        }
        while (!reader.nextIfArrayEnd()) {
            PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
            Object value = _readNode(reader, valueType, valueClazz, cps);
            list.add(value);
        }
        return list;
    }

    private static Set<Object> _readSetWithElementType(JSONReader reader, Class<?> rawClazz,
                                                        Type valueType, Class<?> valueClazz, PathSegment ps)
            throws IOException {
        if (reader.nextIfNull()) {
            return null;
        }
        Set<Object> set = Sjf4jConfig.global().setSupplier.create();
        int i = 0;
        if (!reader.nextIfArrayStart()) {
            throw new JsonException("Expected token '[', but was " + reader.current());
        }
        while (!reader.nextIfArrayEnd()) {
            PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
            Object value = _readNode(reader, valueType, valueClazz, cps);
            set.add(value);
        }
        return set;
    }

    /// Reader

    public static StreamingReader.Token peekToken(JSONReader reader) throws IOException {
        switch (reader.current()) {
            case '{':
                return StreamingReader.Token.START_OBJECT;
            case '}':
                return StreamingReader.Token.END_OBJECT;
            case '[':
                return StreamingReader.Token.START_ARRAY;
            case ']':
                return StreamingReader.Token.END_ARRAY;
            case '"':
                return StreamingReader.Token.STRING;
            case 't':
            case 'f':
                return StreamingReader.Token.BOOLEAN;
            case 'n':
                return StreamingReader.Token.NULL;
            case '-':
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                return StreamingReader.Token.NUMBER;
            default:
                break;
        }

        if (reader.isObject()) {
            return StreamingReader.Token.START_OBJECT;
        } else if (reader.current() == '}') {
            return StreamingReader.Token.END_OBJECT;
        } else if (reader.isArray()) {
            return StreamingReader.Token.START_ARRAY;
        } else if (reader.current() == ']') {
            return StreamingReader.Token.END_ARRAY;
        } else if (reader.isString()) {
            return StreamingReader.Token.STRING;
        } else if (reader.isNumber()) {
            return StreamingReader.Token.NUMBER;
        } else if (reader.current() == 't' || reader.current() == 'f') {
            return StreamingReader.Token.BOOLEAN;
        } else if (reader.isNull()) {
            return StreamingReader.Token.NULL;
        } else {
            return StreamingReader.Token.UNKNOWN;
        }
    }

    /// Write

    public static void writeNode(JSONWriter writer, Object node) throws IOException {
        Objects.requireNonNull(writer, "writer is null");
        _writeNode(
                writer,
                node,
                Sjf4jConfig.global().isBindingPath() ? PathSegment.Root.INSTANCE : null
        );
    }

    private static void _writeNode(JSONWriter writer, Object node, PathSegment ps) throws IOException {
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
                writeNumber(writer, (Number) node);
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
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    _writeNode(writer, entry.getValue(), cps);
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
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                    _writeNode(writer, list.get(i), cps);
                }
                writer.endArray();
                return;
            }

            if (node instanceof JsonObject) {
                writer.startObject();
                ((JsonObject) node).forEach((k, v) -> {
                    try {
                        writer.writeName(k);
                        writer.writeColon();
                        PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, k);
                        _writeNode(writer, v, cps);
                    } catch (IOException e) {
                        throw new BindingException(e, ps);
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
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                    _writeNode(writer, ja.getNode(i), cps);
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
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                    _writeNode(writer, Array.get(node, i), cps);
                }
                writer.endArray();
                return;
            }

            if (node instanceof Set) {
                writer.startArray();
                boolean veryStart = true;
                int i = 0;
                for (Object v : (Set<?>) node) {
                    if (veryStart) {
                        veryStart = false;
                    } else {
                        writer.writeComma();
                    }
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                    _writeNode(writer, v, cps);
                }
                writer.endArray();
                return;
            }

            NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
            if (ti.valueCodecInfo != null) {
                Object raw = ti.valueCodecInfo.encode(node);
                _writeNode(writer, raw, ps);
                return;
            }

            NodeRegistry.PojoInfo pi = ti.pojoInfo;
            if (pi != null) {
                writer.startObject();
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                    String key = entry.getKey();
                    writer.writeName(key);
                    writer.writeColon();
                    Object vv = entry.getValue().invokeGetter(node);
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    _writeNode(writer, vv, cps);
                }
                writer.endObject();
                return;
            }

            throw new BindingException("Unsupported node type '" + rawClazz.getName() + "'", ps);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to write node of type '" + Types.name(node) + "'", ps, e);
        }
    }

    public static void writeNumber(JSONWriter writer, Number value) throws IOException {
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
