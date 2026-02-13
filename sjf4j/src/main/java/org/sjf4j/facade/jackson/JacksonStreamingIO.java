package org.sjf4j.facade.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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
 * Jackson specialized streaming implementation that mirrors StreamingIO semantics
 * (including BindingException/PathSegment behavior) while keeping Jackson-native
 * parser/generator hot paths.
 */
public class JacksonStreamingIO {

    /// Read

    public static void skipNode(JsonParser parser) throws IOException {
        JsonToken tk = parser.currentToken();
        if (tk.isScalarValue()) {
            parser.nextToken();
        } else {
            parser.skipChildren();
            parser.nextToken();
        }
    }

    public static Object readNode(JsonParser parser, Type type) throws IOException {
        return _readNode(
                parser,
                type,
                Types.rawBox(type),
                Sjf4jConfig.global().isBindingPath() ? PathSegment.Root.INSTANCE : null
        );
    }

    private static Object _readNode(JsonParser parser, Type type, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        try {
            StreamingReader.Token token = peekToken(parser);
            switch (token) {
                case START_OBJECT:
                    return _readObject(parser, type, rawClazz, ps);
                case START_ARRAY:
                    return _readArray(parser, type, rawClazz, ps);
                case STRING:
                    return _readString(parser, rawClazz, ps);
                case NUMBER:
                    return _readNumber(parser, rawClazz, ps);
                case BOOLEAN:
                    return _readBoolean(parser, rawClazz, ps);
                case NULL:
                    return _readNull(parser, rawClazz, ps);
                default:
                    throw new JsonException("Unexpected token '" + token + "'");
            }
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to read streaming into '" + type + "'", ps, e);
        }
    }

    private static Object _readNull(JsonParser parser, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        parser.nextToken();

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            return vci.decode(null);
        }
        return null;
    }

    private static Object _readBoolean(JsonParser parser, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        if (rawClazz == Object.class || rawClazz == Boolean.class) {
            boolean b = parser.getBooleanValue();
            parser.nextToken();
            return b;
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            boolean b = parser.getBooleanValue();
            parser.nextToken();
            return vci.decode(b);
        }

        throw new BindingException("Cannot read boolean value into type " + rawClazz.getName(), ps);
    }

    private static Object _readNumber(JsonParser parser, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        if (rawClazz == Object.class || rawClazz == Number.class) {
            Number n = parser.getNumberValue();
            parser.nextToken();
            return n;
        }
        if (rawClazz == Integer.class) {
            int n = parser.getIntValue();
            parser.nextToken();
            return n;
        }
        if (rawClazz == Long.class) {
            long n = parser.getLongValue();
            parser.nextToken();
            return n;
        }
        if (rawClazz == Float.class) {
            float n = parser.getFloatValue();
            parser.nextToken();
            return n;
        }
        if (rawClazz == Double.class) {
            double n = parser.getDoubleValue();
            parser.nextToken();
            return n;
        }
        if (rawClazz == Short.class) {
            short n = parser.getShortValue();
            parser.nextToken();
            return n;
        }
        if (rawClazz == Byte.class) {
            byte n = parser.getByteValue();
            parser.nextToken();
            return n;
        }
        if (rawClazz == BigInteger.class) {
            BigInteger n = parser.getBigIntegerValue();
            parser.nextToken();
            return n;
        }
        if (rawClazz == BigDecimal.class) {
            BigDecimal n = parser.getDecimalValue();
            parser.nextToken();
            return n;
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Number n = parser.getNumberValue();
            parser.nextToken();
            return vci.decode(n);
        }

        throw new BindingException("Cannot read number value into type " + rawClazz.getName(), ps);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object _readString(JsonParser parser, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        if (rawClazz == Object.class || rawClazz == String.class) {
            String s = parser.getText();
            parser.nextToken();
            return s;
        }
        if (rawClazz == Character.class) {
            String s = parser.getText();
            parser.nextToken();
            return s.length() > 0 ? s.charAt(0) : null;
        }
        if (rawClazz.isEnum()) {
            String s = parser.getText();
            parser.nextToken();
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            String s = parser.getText();
            parser.nextToken();
            return vci.decode(s);
        }

        throw new BindingException("Cannot read string value into type " + rawClazz.getName(), ps);
    }

    private static Object _readObject(JsonParser parser, Type type, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        if (rawClazz == Object.class || rawClazz == Map.class) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readMapWithValueType(parser, rawClazz, valueType, valueClazz, ps);
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_OBJECT) {
                String key = parser.currentName();
                parser.nextToken();
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                Object value = _readNode(parser, Object.class, Object.class, cps);
                jo.put(key, value);
            }
            parser.nextToken();
            return jo;
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
        if (ti.valueCodecInfo != null) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueClazz = Types.rawBox(valueType);
            Map<String, Object> map = _readMapWithValueType(parser, rawClazz, valueType, valueClazz, ps);
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

            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_OBJECT) {
                String key = parser.currentName();
                parser.nextToken();

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
                    args[argIdx] = _readNode(parser, argType, Types.rawBox(argType), cps);
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
                    Object vv = _readField(parser, fi, cps);
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
                    Object vv = _readNode(parser, Object.class, Object.class, cps);
                    dynamicMap.put(key, vv);
                } else {
                    skipNode(parser);
                }
            }
            parser.nextToken();

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

    private static Object _readArray(JsonParser parser, Type type, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        if (rawClazz == Object.class || rawClazz == List.class) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readListWithElementType(parser, rawClazz, valueType, valueClazz, ps);
        }

        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            int i = 0;
            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_ARRAY) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(parser, Object.class, Object.class, cps);
                ja.add(value);
            }
            parser.nextToken();
            return ja;
        }

        if (rawClazz == Set.class) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readSetWithElementType(parser, rawClazz, valueType, valueClazz, ps);
        }

        if (rawClazz.isArray()) {
            Class<?> compType = rawClazz.getComponentType();
            Class<?> valueClazz = Types.box(compType);
            List<Object> list = new ArrayList<>();
            int i = 0;
            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_ARRAY) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(parser, compType, valueClazz, cps);
                list.add(value);
            }
            parser.nextToken();

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
            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_ARRAY) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(parser, elemType, elemRaw, cps);
                ja.add(value);
            }
            parser.nextToken();
            return ja;
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            List<Object> list = _readListWithElementType(parser, rawClazz, valueType, valueClazz, ps);
            return vci.decode(list);
        }

        throw new BindingException("Cannot read array value into type " + rawClazz.getName(), ps);
    }

    private static Object _readField(JsonParser parser, NodeRegistry.FieldInfo fi, PathSegment ps)
            throws IOException {
        switch (fi.containerKind) {
            case MAP:
                return _readMapWithValueType(parser, fi.rawType, fi.argType, fi.argRawType, ps);
            case LIST:
                return _readListWithElementType(parser, fi.rawType, fi.argType, fi.argRawType, ps);
            case SET:
                return _readSetWithElementType(parser, fi.rawType, fi.argType, fi.argRawType, ps);
            default:
                return _readNode(parser, fi.type, fi.rawType, ps);
        }
    }

    private static Map<String, Object> _readMapWithValueType(JsonParser parser, Class<?> rawClazz,
                                                              Type valueType, Class<?> valueClazz, PathSegment ps)
            throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            parser.nextToken();
            return null;
        }
        Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_OBJECT) {
            String key = parser.currentName();
            parser.nextToken();
            PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
            Object value = _readNode(parser, valueType, valueClazz, cps);
            map.put(key, value);
        }
        parser.nextToken();
        return map;
    }

    private static List<Object> _readListWithElementType(JsonParser parser, Class<?> rawClazz,
                                                          Type valueType, Class<?> valueClazz, PathSegment ps)
            throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            parser.nextToken();
            return null;
        }
        List<Object> list = new ArrayList<>();
        int i = 0;
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_ARRAY) {
            PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
            Object value = _readNode(parser, valueType, valueClazz, cps);
            list.add(value);
        }
        parser.nextToken();
        return list;
    }

    private static Set<Object> _readSetWithElementType(JsonParser parser, Class<?> rawClazz,
                                                        Type valueType, Class<?> valueClazz, PathSegment ps)
            throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            parser.nextToken();
            return null;
        }
        Set<Object> set = Sjf4jConfig.global().setSupplier.create();
        int i = 0;
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_ARRAY) {
            PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
            Object value = _readNode(parser, valueType, valueClazz, cps);
            set.add(value);
        }
        parser.nextToken();
        return set;
    }

    /// Reader

    public static StreamingReader.Token peekToken(JsonParser parser) throws IOException {
        JsonToken tk = parser.currentToken();
        if (tk == null) {
            tk = parser.nextToken();
        }
        switch (tk) {
            case START_OBJECT:
                return StreamingReader.Token.START_OBJECT;
            case END_OBJECT:
                return StreamingReader.Token.END_OBJECT;
            case START_ARRAY:
                return StreamingReader.Token.START_ARRAY;
            case END_ARRAY:
                return StreamingReader.Token.END_ARRAY;
            case VALUE_STRING:
                return StreamingReader.Token.STRING;
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return StreamingReader.Token.NUMBER;
            case VALUE_TRUE:
            case VALUE_FALSE:
                return StreamingReader.Token.BOOLEAN;
            case VALUE_NULL:
                return StreamingReader.Token.NULL;
            default:
                return StreamingReader.Token.UNKNOWN;
        }
    }

    /// Write

    public static void writeNode(JsonGenerator gen, Object node) throws IOException {
        Objects.requireNonNull(gen, "gen is null");
        _writeNode(
                gen,
                node,
                Sjf4jConfig.global().isBindingPath() ? PathSegment.Root.INSTANCE : null
        );
    }

    private static void _writeNode(JsonGenerator gen, Object node, PathSegment ps) throws IOException {
        try {
            if (node == null) {
                gen.writeNull();
                return;
            }

            Class<?> rawClazz = node.getClass();

            if (node instanceof String || node instanceof Character) {
                gen.writeString(node.toString());
                return;
            }
            if (node instanceof Enum) {
                gen.writeString(((Enum<?>) node).name());
                return;
            }

            if (node instanceof Number) {
                writeValue(gen, (Number) node);
                return;
            }

            if (node instanceof Boolean) {
                gen.writeBoolean((Boolean) node);
                return;
            }

            if (node instanceof Map) {
                gen.writeStartObject();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                    String key = entry.getKey().toString();
                    gen.writeFieldName(key);
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    _writeNode(gen, entry.getValue(), cps);
                }
                gen.writeEndObject();
                return;
            }

            if (node instanceof List) {
                gen.writeStartArray();
                List<?> list = (List<?>) node;
                for (int i = 0, len = list.size(); i < len; i++) {
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                    _writeNode(gen, list.get(i), cps);
                }
                gen.writeEndArray();
                return;
            }

            if (node instanceof JsonObject) {
                gen.writeStartObject();
                ((JsonObject) node).forEach((k, v) -> {
                    try {
                        gen.writeFieldName(k);
                        PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, k);
                        _writeNode(gen, v, cps);
                    } catch (IOException e) {
                        throw new BindingException(e, ps);
                    }
                });
                gen.writeEndObject();
                return;
            }

            if (node instanceof JsonArray) {
                gen.writeStartArray();
                JsonArray ja = (JsonArray) node;
                for (int i = 0, len = ja.size(); i < len; i++) {
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                    _writeNode(gen, ja.getNode(i), cps);
                }
                gen.writeEndArray();
                return;
            }

            if (rawClazz.isArray()) {
                gen.writeStartArray();
                int len = Array.getLength(node);
                for (int i = 0; i < len; i++) {
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                    _writeNode(gen, Array.get(node, i), cps);
                }
                gen.writeEndArray();
                return;
            }

            if (node instanceof Set) {
                gen.writeStartArray();
                int i = 0;
                for (Object v : (Set<?>) node) {
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                    _writeNode(gen, v, cps);
                }
                gen.writeEndArray();
                return;
            }

            NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
            if (ti.valueCodecInfo != null) {
                Object raw = ti.valueCodecInfo.encode(node);
                _writeNode(gen, raw, ps);
                return;
            }

            NodeRegistry.PojoInfo pi = ti.pojoInfo;
            if (pi != null) {
                gen.writeStartObject();
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                    String key = entry.getKey();
                    gen.writeFieldName(key);
                    Object vv = entry.getValue().invokeGetter(node);
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    _writeNode(gen, vv, cps);
                }
                gen.writeEndObject();
                return;
            }

            throw new BindingException("Unsupported node type '" + rawClazz.getName() + "'", ps);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to write node of type '" + Types.name(node) + "'", ps, e);
        }
    }

    public static void writeValue(JsonGenerator gen, Number value) throws IOException {
        if (value instanceof Long) {
            gen.writeNumber((Long) value);
        } else if (value instanceof Integer) {
            gen.writeNumber((Integer) value);
        } else if (value instanceof Short) {
            gen.writeNumber((Short) value);
        } else if (value instanceof Byte) {
            gen.writeNumber((Byte) value);
        } else if (value instanceof BigInteger) {
            gen.writeNumber((BigInteger) value);
        } else if (value instanceof Double) {
            gen.writeNumber((Double) value);
        } else if (value instanceof Float) {
            gen.writeNumber((Float) value);
        } else if (value instanceof BigDecimal) {
            gen.writeNumber((BigDecimal) value);
        } else {
            gen.writeNumber(value.toString());
        }
    }
}
