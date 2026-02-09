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
        return _readNode(reader, type,
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
    private static Object _readNode(StreamingReader reader, Type type, PathSegment ps) {
        try {
            StreamingReader.Token token = reader.peekToken();
            switch (token) {
                case START_OBJECT:
                    return _readObject(reader, type, ps);
                case START_ARRAY:
                    return _readArray(reader, type, ps);
                case STRING:
                    return _readString(reader, type, ps);
                case NUMBER:
                    return _readNumber(reader, type, ps);
                case BOOLEAN:
                    return _readBoolean(reader, type, ps);
                case NULL:
                    return _readNull(reader, type, ps);
                default:
                    throw new JsonException("Unexpected token '" + token + "'");
            }
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to read streaming into '" + type + "'", ps, e);
        }
    }

    private static Object _readNull(StreamingReader reader, Type type, PathSegment ps)
            throws IOException {
        Class<?> rawClazz = Types.rawBox(type);
        reader.nextNull();

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            return ci.decode(null);
        }
        return null;
    }

    private static Object _readBoolean(StreamingReader reader, Type type, PathSegment ps) throws IOException {
        Class<?> rawClazz = Types.rawBox(type);
        if (rawClazz == Object.class || rawClazz == Boolean.class) {
            return reader.nextBoolean();
        }

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            boolean b = reader.nextBoolean();
            return ci.decode(b);
        }
        throw new BindingException("Cannot deserialize Boolean value into type " + rawClazz.getName(), ps);
    }

    private static Object _readNumber(StreamingReader reader, Type type, PathSegment ps) throws IOException {
        Class<?> rawClazz = Types.rawBox(type);
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
    private static Object _readString(StreamingReader reader, Type type, PathSegment ps) throws IOException {
        Class<?> rawClazz = Types.rawBox(type);
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
    private static Object _readObject(StreamingReader reader, Type type, PathSegment ps) throws IOException {
        Class<?> rawClazz = Types.rawBox(type);

        if (rawClazz == Object.class || rawClazz == Map.class) {
            Type vt = Types.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
            reader.startObject();
            while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
                String key = reader.nextName();
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                Object value = _readNode(reader, vt, cps);
                map.put(key, value);
            }
            reader.endObject();
            return map;
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            reader.startObject();
            while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
                String key = reader.nextName();
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                Object value = _readNode(reader, Object.class, cps);
                jo.put(key, value);
            }
            reader.endObject();
            return jo;
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
            reader.startObject();
            while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
                String key = reader.nextName();
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                Object value = _readNode(reader, valueType, cps);
                map.put(key, value);
            }
            reader.endObject();
            return vci.decode(map);
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null && !pi.isJajo()) {
            NodeRegistry.CreatorInfo ci = pi.getCreatorInfo();
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            Map<String, NodeRegistry.FieldInfo> aliasFields = pi.getAliasFields();
            boolean useArgsCreator = !ci.hasNoArgsCtor();
            Object pojo = useArgsCreator ? null : ci.newPojoNoArgs();
            Object[] args = useArgsCreator ? new Object[ci.getArgNames().length] : null;
            int remainingArgs = useArgsCreator ? args.length : 0;
            int pendingSize = 0;
            NodeRegistry.FieldInfo[] pendingFields = null;
            Object[] pendingValues = null;
            Map<String, Object> dynamicMap = null;
            boolean isJojo = JsonObject.class.isAssignableFrom(pi.getType());

            reader.startObject();
            while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
                String key = reader.nextName();

                int argIdx = -1;
                if (pojo == null) {
                    argIdx = ci.getArgIndex(key);
                    if (argIdx < 0 && ci.getAliasMap() != null) {
                        String origin = ci.getAliasMap().get(key); // alias -> origin
                        if (origin != null) {
                            argIdx = ci.getArgIndex(origin);
                        }
                    }
                }
                if (argIdx >= 0) {
                    Type argType = ci.getArgTypes()[argIdx];
                    assert args != null;
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    args[argIdx] = _readNode(reader, argType, cps);
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

                NodeRegistry.FieldInfo fi = aliasFields != null ? aliasFields.get(key) : fields.get(key);
                if (fi != null) {
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    Object vv = _readNode(reader, fi.getType(), cps);
                    if (pojo != null) {
                        fi.invokeSetterIfPresent(pojo, vv);
                    } else {
                        if (pendingFields == null) {
                            int cap = pi.getFieldCount();
                            pendingFields = new NodeRegistry.FieldInfo[cap];
                            pendingValues = new Object[cap];
                        }
                        pendingFields[pendingSize] = fi;
                        pendingValues[pendingSize] = vv;
                        pendingSize++;
                    }
                    continue;
                }

                if (isJojo) {
                    if (dynamicMap == null) dynamicMap = Sjf4jConfig.global().mapSupplier.create();
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    Object vv = _readNode(reader, Object.class, cps);
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
            if (isJojo) ((JsonObject) pojo).setDynamicMap(dynamicMap);
            return pojo;
        }

        throw new BindingException("Cannot deserialize Object value into type " + rawClazz.getName(), ps);
    }

    private static Object _readArray(StreamingReader reader, Type type, PathSegment ps) throws IOException {
        Class<?> rawClazz = Types.rawBox(type);

        if (rawClazz == Object.class || rawClazz == List.class) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            int i = 0;
            reader.startArray();
            while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(reader, valueType, cps);
                list.add(value);
            }
            reader.endArray();
            return list;
        }

        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            int i = 0;
            reader.startArray();
            while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(reader, Object.class, cps);
                ja.add(value);
            }
            reader.endArray();
            return ja;
        }

        if (rawClazz == Set.class) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Set<Object> set = Sjf4jConfig.global().setSupplier.create();
            int i = 0;
            reader.startArray();
            while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(reader, valueType, cps);
                set.add(value);
            }
            reader.endArray();
            return set;
        }

        if (rawClazz.isArray()) {
            Class<?> vt = rawClazz.getComponentType();
            List<Object> list = new ArrayList<>();
            int i = 0;
            reader.startArray();
            while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(reader, vt, cps);
                list.add(value);
            }
            reader.endArray();

            Object array = Array.newInstance(vt, list.size());
            for (int j = 0, len = list.size(); j < len; j++) {
                Array.set(array, j, list.get(j));
            }
            return array;
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            JsonArray ja = (JsonArray) NodeRegistry.registerPojoOrElseThrow(rawClazz).getCreatorInfo().forceNewPojo();
            int i = 0;
            reader.startArray();
            while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(reader, ja.elementType(), cps);
                ja.add(value);
            }
            reader.endArray();
            return ja;
        }

        NodeRegistry.ValueCodecInfo ci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (ci != null) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            int i = 0;
            reader.startArray();
            while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(reader, valueType, cps);
                list.add(value);
            }
            reader.endArray();
            return ci.decode(list);
        }

        throw new BindingException("Cannot deserialize Array value into type " + rawClazz.getName(), ps);
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
            NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
            if (vci != null) {
                Object raw = vci.encode(node);
                _writeNode(writer, raw, ps);
                return;
            }

            if (node instanceof CharSequence || node instanceof Character) {
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

            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
            if (pi != null) {
                writer.startObject();
                boolean veryStart = true;
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.getFields().entrySet()) {
                    if (veryStart) veryStart = false;
                    else writer.writeObjectComma();
                    writer.writeName(entry.getKey());
                    Object vv = entry.getValue().invokeGetter(node);
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, entry.getKey());
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
