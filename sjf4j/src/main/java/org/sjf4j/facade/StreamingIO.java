package org.sjf4j.facade;

import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.BindingException;
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
 * Utility class for streaming JSON processing. Provides methods for reading JSON nodes from
 * a {@link StreamingReader} and writing JSON nodes to a {@link StreamingWriter}, with support
 * for type conversion and POJO handling.
 */
public class StreamingIO {

    /// Read

    public static Object readNode(StreamingReader reader, Type type) {
        return _readNode(reader, type, Types.rawBox(type),
                Sjf4jConfig.global().isBindingPath() ? PathSegment.Root.INSTANCE : null);
    }

    /**
     * Reads a JSON node from the provided {@link StreamingReader} and converts it to the appropriate
     * Java object based on the token type and optional target type.
     *
     * @param reader the FacadeReader to read from
     * @param type the target type for conversion (may be null)
     * @return the parsed and converted JSON node
     * @throws JsonException if an unexpected token is encountered
     */
    private static Object _readNode(StreamingReader reader, Type type, Class<?> rawClazz, PathSegment ps) {
        try {
            StreamingReader.Token token = reader.peekToken();
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

    private static Object _readNull(StreamingReader reader, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        reader.nextNull();

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            return vci.decode(null);
        }
        return null;
    }

    private static Object _readBoolean(StreamingReader reader, Class<?> rawClazz, PathSegment ps) throws IOException {
        if (rawClazz == Object.class || rawClazz == Boolean.class) {
            return reader.nextBoolean();
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            boolean b = reader.nextBoolean();
            return vci.decode(b);
        }
        throw new BindingException("Cannot deserialize Boolean value into type " + rawClazz.getName(), ps);
    }

    private static Object _readNumber(StreamingReader reader, Class<?> rawClazz, PathSegment ps) throws IOException {
        if (rawClazz == Object.class || rawClazz == Number.class) {
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

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Number n = reader.nextNumber();
            return vci.decode(n);
        }
        throw new BindingException("Cannot deserialize Number value into type " + rawClazz.getName(), ps);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object _readString(StreamingReader reader, Class<?> rawClazz, PathSegment ps) throws IOException {
        if (rawClazz == Object.class || rawClazz == String.class) {
            return reader.nextString();
        }
        if (rawClazz == Character.class) {
            String s = reader.nextString();
            return s.length() > 0 ? s.charAt(0) : null;
        }
        if (rawClazz.isEnum()) {
            String s = reader.nextString();
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            String s = reader.nextString();
            return vci.decode(s);
        }
        throw new BindingException("Cannot deserialize String value into type " + rawClazz.getName(), ps);
    }

    /**
     * Reads a JSON object from the provided {@link StreamingReader} and converts it to the appropriate
     * Java object based on the target type.
     *
     * @param reader the FacadeReader to read from
     * @param type the target type for conversion
     * @return the parsed and converted JSON object
     * @throws IOException if an I/O error occurs during reading
     */
    private static Object _readObject(StreamingReader reader, Type type, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        if (rawClazz == Object.class || rawClazz == Map.class) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readMapWithValueType(reader, rawClazz, valueType, valueClazz, ps);
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            reader.startObject();
            while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
                String key = reader.nextName();
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                Object value = _readNode(reader, Object.class, Object.class, cps);
                jo.put(key, value);
            }
            reader.endObject();
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

            reader.startObject();
            while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
                String key = reader.nextName();

                int argIdx = -1;
                if (pojo == null) {
                    argIdx = ci.getArgIndex(key);
                    if (argIdx < 0 && ci.aliasMap != null) {
                        String origin = ci.aliasMap.get(key); // alias -> origin
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
                    if (dynamicMap == null) dynamicMap = Sjf4jConfig.global().mapSupplier.create();
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    Object vv = _readNode(reader, Object.class, Object.class, cps);
                    dynamicMap.put(key, vv);
                } else {
                    reader.nextSkip();
                }
            }
            reader.endObject();

            if (pojo == null) {
                pojo = ci.newPojoWithArgs(args);
                for (int i = 0; i < pendingSize; i++) {
                    pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                }
            }
            if (pi.isJojo) ((JsonObject) pojo).setDynamicMap(dynamicMap);
            return pojo;
        }

        throw new BindingException("Cannot deserialize Object value into type " + rawClazz.getName(), ps);
    }

    private static Object _readArray(StreamingReader reader, Type type, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        if (rawClazz == Object.class || rawClazz == List.class) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readListWithElementType(reader, rawClazz, valueType, valueClazz, ps);
        }

        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            int i = 0;
            reader.startArray();
            while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(reader, Object.class, Object.class, cps);
                ja.add(value);
            }
            reader.endArray();
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
            reader.startArray();
            while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(reader, compType, valueClazz, cps);
                list.add(value);
            }
            reader.endArray();

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
            reader.startArray();
            while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(reader, elemType, elemRaw, cps);
                ja.add(value);
            }
            reader.endArray();
            return ja;
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            List<Object> list = _readListWithElementType(reader, rawClazz, valueType, valueClazz, ps);
            return vci.decode(list);
        }

        throw new BindingException("Cannot deserialize Array value into type " + rawClazz.getName(), ps);
    }

    private static Object _readField(StreamingReader reader, NodeRegistry.FieldInfo fi, PathSegment ps)
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

    private static Map<String, Object> _readMapWithValueType(StreamingReader reader, Class<?> rawClazz,
                                                             Type valueType, Class<?> valueClazz, PathSegment ps)
            throws IOException {
        if (reader.peekToken() == StreamingReader.Token.NULL) {
            reader.nextNull();
            return null;
        }
        Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
        reader.startObject();
        while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
            String key = reader.nextName();
            PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
            Object value = _readNode(reader, valueType, valueClazz, cps);
            map.put(key, value);
        }
        reader.endObject();
        return map;
    }

    private static List<Object> _readListWithElementType(StreamingReader reader, Class<?> rawClazz,
                                                         Type valueType, Class<?> valueClazz, PathSegment ps)
            throws IOException {
        if (reader.peekToken() == StreamingReader.Token.NULL) {
            reader.nextNull();
            return null;
        }
        List<Object> list = new ArrayList<>();
        int i = 0;
        reader.startArray();
        while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
            PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
            Object value = _readNode(reader, valueType, valueClazz, cps);
            list.add(value);
        }
        reader.endArray();
        return list;
    }

    private static Set<Object> _readSetWithElementType(StreamingReader reader, Class<?> rawClazz,
                                                       Type valueType, Class<?> valueClazz, PathSegment ps)
            throws IOException {
        if (reader.peekToken() == StreamingReader.Token.NULL) {
            reader.nextNull();
            return null;
        }
        Set<Object> set = Sjf4jConfig.global().setSupplier.create();
        int i = 0;
        reader.startArray();
        while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
            PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
            Object value = _readNode(reader, valueType, valueClazz, cps);
            set.add(value);
        }
        reader.endArray();
        return set;
    }


    /// Write

    public static void writeNode(StreamingWriter writer, Object node) throws IOException {
        Objects.requireNonNull(writer, "writer is null");
        _writeNode(writer, node,
                Sjf4jConfig.global().isBindingPath() ? PathSegment.Root.INSTANCE : null);
    }

    private static void _writeNode(StreamingWriter writer, Object node, PathSegment ps) throws IOException {
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
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    _writeNode(writer, entry.getValue(), cps);
                }
                writer.endObject();
                return;
            }

            if (node instanceof List) {
                writer.startArray();
                List<?> list = (List<?>) node;
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) writer.writeArrayComma();
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                    _writeNode(writer, list.get(i), cps);
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
                    if (i > 0) writer.writeArrayComma();
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                    _writeNode(writer, ja.getNode(i), cps);
                }
                writer.endArray();
                return;
            }

            if (node.getClass().isArray()) {
                writer.startArray();
                for (int i = 0, len = Array.getLength(node); i < len; i++) {
                    if (i > 0) writer.writeArrayComma();
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
                    if (veryStart) veryStart = false;
                    else writer.writeArrayComma();
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
                boolean veryStart = true;
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                    if (veryStart) veryStart = false;
                    else writer.writeObjectComma();
                    String key = entry.getKey();
                    writer.writeName(key);
                    Object vv = entry.getValue().invokeGetter(node);
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    _writeNode(writer, vv, cps);
                }
                writer.endObject();
                return;
            }

            throw new BindingException("Unsupported node type '" + node.getClass().getName() + "'", ps);

        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to write node of type '" + Types.name(node) + "'", ps, e);
        }
    }


}
