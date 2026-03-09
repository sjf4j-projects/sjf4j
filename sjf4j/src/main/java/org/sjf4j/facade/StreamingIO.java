package org.sjf4j.facade;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.annotation.node.AnyOf;
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
        return _readNode(reader, type, rawBox, anyOfInfo,
                Sjf4jConfig.global().isBindingPath() ? PathSegment.Root.INSTANCE : null);
    }

    /**
     * Reads next token and dispatches to typed node readers.
     */
    private static Object _readNode(StreamingReader reader, Type type, Class<?> rawBoxed,
                                    NodeRegistry.AnyOfInfo anyOfInfo, PathSegment ps) {
        try {
            if (anyOfInfo != null) {
                return _readAnyOf(reader, type, rawBoxed, anyOfInfo, ps);
            }
            StreamingReader.Token token = reader.peekToken();
            switch (token) {
                case START_OBJECT:
                    return _readObject(reader, type, rawBoxed, ps);
                case START_ARRAY:
                    return _readArray(reader, type, rawBoxed, ps);
                case STRING:
                    return _readString(reader, rawBoxed, ps);
                case NUMBER:
                    return _readNumber(reader, rawBoxed, ps);
                case BOOLEAN:
                    return _readBoolean(reader, rawBoxed, ps);
                case NULL:
                    return _readNull(reader, rawBoxed, ps);
                default:
                    throw new JsonException("Unexpected token '" + token + "'");
            }
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to read streaming into '" + type + "'", ps, e);
        }
    }

    /**
     * Reads null token and decodes via value codec when needed.
     */
    private static Object _readNull(StreamingReader reader, Class<?> rawClazz, PathSegment ps)
            throws IOException {
        reader.nextNull();

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            return vci.rawToValue(null);
        }
        return null;
    }

    /**
     * Reads boolean token into target type.
     */
    private static Object _readBoolean(StreamingReader reader, Class<?> rawClazz, PathSegment ps) throws IOException {
        if (rawClazz == Object.class || rawClazz == Boolean.class) {
            return reader.nextBoolean();
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            boolean b = reader.nextBoolean();
            return vci.rawToValue(b);
        }
        throw new BindingException("Cannot read boolean value into type '" + rawClazz.getName() + "'", ps);
    }

    /**
     * Reads number token into target numeric or codec type.
     */
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
            return vci.rawToValue(n);
        }
        throw new BindingException("Cannot read number value into type '" + rawClazz.getName() + "'", ps);
    }

    /**
     * Reads string token into target scalar or codec type.
     */
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
            return vci.rawToValue(s);
        }
        throw new BindingException("Cannot read string value into type '" + rawClazz.getName() + "'", ps);
    }

    /**
     * Reads object token into Map/JsonObject/POJO target.
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
                Object value = _readNode(reader, Object.class, Object.class, null, cps);
                jo.put(key, value);
            }
            reader.endObject();
            return jo;
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
        NodeRegistry.ValueCodecInfo vci = ti.valueCodecInfo;
        if (vci != null) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueClazz = Types.rawBox(valueType);
            Map<String, Object> map = _readMapWithValueType(reader, rawClazz, valueType, valueClazz, ps);
            return vci.rawToValue(map);
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
            NodeRegistry.FieldInfo deferredParentAnyOfFi = null;
            Object deferredParentAnyOfRaw = null;
            PathSegment deferredParentAnyOfPath = null;
            String parentAnyOfKey = null;
            Object parentAnyOfValue = UNSET;

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
                    Class<?> argRaw = Types.rawBox(argType);
                    NodeRegistry.AnyOfInfo argAnyOf = NodeRegistry.registerTypeInfo(argRaw).anyOfInfo;
                    Object argValue = _readNode(reader, argType, argRaw, argAnyOf, cps);
                    args[argIdx] = argValue;
                    if (parentAnyOfKey != null && parentAnyOfKey.equals(key)) {
                        parentAnyOfValue = argValue;
                    }
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
                    Object vv;
                    NodeRegistry.AnyOfInfo fieldAnyOf = fi.anyOfInfo;
                    if (fieldAnyOf != null && fieldAnyOf.getScope() == AnyOf.Scope.PARENT) {
                        if (!fieldAnyOf.getPath().isEmpty()) {
                            throw new BindingException("AnyOf scope=PARENT does not support path discriminator", cps);
                        }
                        String parentKey = fieldAnyOf.getKey();
                        if (parentAnyOfKey == null) {
                            parentAnyOfKey = parentKey;
                        } else if (!parentAnyOfKey.equals(parentKey)) {
                            throw new BindingException("At most one AnyOf parent discriminator key is supported per class", cps);
                        }
                        if (parentAnyOfValue == UNSET) {
                            Object discriminator = null;
                            if (pojo != null) {
                                NodeRegistry.FieldInfo parentFi = pi.aliasFields != null
                                        ? pi.aliasFields.get(parentKey) : pi.fields.get(parentKey);
                                if (parentFi != null) discriminator = parentFi.invokeGetter(pojo);
                                else if (pi.isJojo) discriminator = ((JsonObject) pojo).getNode(parentKey);
                            }
                            if (discriminator == null) {
                                int idx = ci.getArgIndex(parentKey);
                                if (idx >= 0 && args != null) discriminator = args[idx];
                            }
                            if (discriminator == null && ci.aliasMap != null) {
                                String origin = ci.aliasMap.get(parentKey);
                                if (origin != null) {
                                    int idx = ci.getArgIndex(origin);
                                    if (idx >= 0 && args != null) discriminator = args[idx];
                                }
                            }
                            if (discriminator == null) {
                                for (int i = 0; i < pendingSize; i++) {
                                    if (pendingFields[i] != null && parentKey.equals(pendingFields[i].name)) {
                                        discriminator = pendingValues[i];
                                        break;
                                    }
                                }
                            }
                            if (discriminator == null && dynamicMap != null && dynamicMap.containsKey(parentKey)) {
                                discriminator = dynamicMap.get(parentKey);
                            }
                            if (discriminator != null) {
                                parentAnyOfValue = discriminator;
                            }
                        }
                        Class<?> targetClazz = fieldAnyOf.resolveByWhen(parentAnyOfValue == UNSET ? null : parentAnyOfValue);
                        if (targetClazz != null) {
                            vv = _readNode(reader, targetClazz, Types.rawBox(targetClazz), null, cps);
                        } else {
                            if (deferredParentAnyOfFi != null) {
                                throw new BindingException("At most one AnyOf field with scope=PARENT is supported per class", cps);
                            }
                            deferredParentAnyOfFi = fi;
                            deferredParentAnyOfRaw = _readNode(reader, Object.class, Object.class, null, cps);
                            deferredParentAnyOfPath = cps;
                            continue;
                        }
                    } else {
                        vv = _readField(reader, fi, cps);
                    }

                    if (parentAnyOfKey != null && parentAnyOfKey.equals(key)) {
                        parentAnyOfValue = vv;
                    }

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
                    Object vv = _readNode(reader, Object.class, Object.class, null, cps);
                    dynamicMap.put(key, vv);
                    if (parentAnyOfKey != null && parentAnyOfKey.equals(key)) {
                        parentAnyOfValue = vv;
                    }
                } else {
                    reader.nextSkip();
                }
            }
            reader.endObject();

            // Handle deferred AnyOf
            if (deferredParentAnyOfFi != null) {
                NodeRegistry.AnyOfInfo aoi = deferredParentAnyOfFi.anyOfInfo;
                String parentKey = aoi.getKey();
                if (parentAnyOfValue == UNSET) {
                    Object discriminator = null;
                    if (pojo != null) {
                        NodeRegistry.FieldInfo parentFi = pi.aliasFields != null
                                ? pi.aliasFields.get(parentKey) : pi.fields.get(parentKey);
                        if (parentFi != null) discriminator = parentFi.invokeGetter(pojo);
                        else if (pi.isJojo) discriminator = ((JsonObject) pojo).getNode(parentKey);
                    }
                    if (discriminator == null) {
                        int idx = ci.getArgIndex(parentKey);
                        if (idx >= 0 && args != null) discriminator = args[idx];
                    }
                    if (discriminator == null && ci.aliasMap != null) {
                        String origin = ci.aliasMap.get(parentKey);
                        if (origin != null) {
                            int idx = ci.getArgIndex(origin);
                            if (idx >= 0 && args != null) discriminator = args[idx];
                        }
                    }
                    if (discriminator == null) {
                        for (int i = 0; i < pendingSize; i++) {
                            if (pendingFields[i] != null && parentKey.equals(pendingFields[i].name)) {
                                discriminator = pendingValues[i];
                                break;
                            }
                        }
                    }
                    if (discriminator == null && dynamicMap != null && dynamicMap.containsKey(parentKey)) {
                        discriminator = dynamicMap.get(parentKey);
                    }
                    if (discriminator != null) parentAnyOfValue = discriminator;
                }
                Class<?> targetClazz = aoi.resolveByWhen(parentAnyOfValue == UNSET ? null : parentAnyOfValue);
                Object vv;
                if (targetClazz != null) {
                    vv = Sjf4jConfig.global().getNodeFacade().readNode(deferredParentAnyOfRaw, targetClazz);
                } else if (aoi.getOnNoMatch() == AnyOf.OnNoMatch.FAILBACK_NULL) {
                    vv = null;
                } else {
                    throw new BindingException("AnyOf discriminator has no matching mapping: key='" +
                            aoi.getKey() + "', value='" + (parentAnyOfValue == UNSET ? null : parentAnyOfValue) + "'", deferredParentAnyOfPath);
                }

                if (pojo != null) {
                    deferredParentAnyOfFi.invokeSetterIfPresent(pojo, vv);
                } else {
                    if (pendingFields == null) {
                        int cap = pi.fieldCount;
                        pendingFields = new NodeRegistry.FieldInfo[cap];
                        pendingValues = new Object[cap];
                    }
                    pendingFields[pendingSize] = deferredParentAnyOfFi;
                    pendingValues[pendingSize] = vv;
                    pendingSize++;
                }
            }

            if (pojo == null) {
                pojo = ci.newPojoWithArgs(args);
                for (int i = 0; i < pendingSize; i++) {
                    pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                }
            }
            if (pi.isJojo) ((JsonObject) pojo).setDynamicMap(dynamicMap);
            return pojo;
        }

        throw new BindingException("Cannot read object value into type '" + rawClazz.getName() + "'", ps);
    }

    /**
     * Reads array token into List/JsonArray/array/Set target.
     */
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
                Object value = _readNode(reader, Object.class, Object.class, null, cps);
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
            return _readArrayWithElementType(reader, rawClazz, compType, valueClazz, ps);
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            JsonArray ja = (JsonArray) NodeRegistry.registerPojoOrElseThrow(rawClazz).creatorInfo.forceNewPojo();
            Class<?> elemType = ja.elementType();
            Class<?> elemRaw = Types.box(elemType);
            NodeRegistry.AnyOfInfo elemAnyOf = NodeRegistry.registerTypeInfo(elemRaw).anyOfInfo;
            int i = 0;
            reader.startArray();
            while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object value = _readNode(reader, elemType, elemRaw, elemAnyOf, cps);
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
            return vci.rawToValue(list);
        }

        throw new BindingException("Cannot read array value into type '" + rawClazz.getName() + "'", ps);
    }

    /**
     * Reads one object field based on field container metadata.
     */
    private static Object _readField(StreamingReader reader, NodeRegistry.FieldInfo fi, PathSegment ps)
            throws IOException {
        if (fi.anyOfInfo != null) {
            return _readNode(reader, fi.type, fi.rawClazz, fi.anyOfInfo, ps);
        }
        switch (fi.containerKind) {
            case MAP:
                return _readMapWithValueType(reader, fi.rawClazz, fi.argType, fi.argRawClazz, ps);
            case LIST:
                return _readListWithElementType(reader, fi.rawClazz, fi.argType, fi.argRawClazz, ps);
            case SET:
                return _readSetWithElementType(reader, fi.rawClazz, fi.argType, fi.argRawClazz, ps);
            case ARRAY:
                return _readArrayWithElementType(reader, fi.rawClazz, fi.argType, fi.argRawClazz, ps);
            default:
                NodeRegistry.AnyOfInfo typeAnyOf = NodeRegistry.registerTypeInfo(fi.rawClazz).anyOfInfo;
                return _readNode(reader, fi.type, fi.rawClazz, typeAnyOf, ps);
        }
    }

    /**
     * Reads object token into map with typed values.
     */
    private static Map<String, Object> _readMapWithValueType(StreamingReader reader, Class<?> rawClazz,
                                                              Type valueType, Class<?> valueClazz, PathSegment ps)
            throws IOException {
        if (reader.peekToken() == StreamingReader.Token.NULL) {
            reader.nextNull();
            return null;
        }
        Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
        NodeRegistry.AnyOfInfo valueAnyOf = NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo;
        reader.startObject();
        while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
            String key = reader.nextName();
            PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
            Object value = _readNode(reader, valueType, valueClazz, valueAnyOf, cps);
            map.put(key, value);
        }
        reader.endObject();
        return map;
    }

    /**
     * Reads array token into list with typed elements.
     */
    private static List<Object> _readListWithElementType(StreamingReader reader, Class<?> rawClazz,
                                                         Type valueType, Class<?> valueClazz, PathSegment ps)
            throws IOException {
        if (reader.peekToken() == StreamingReader.Token.NULL) {
            reader.nextNull();
            return null;
        }
        List<Object> list = new ArrayList<>();
        NodeRegistry.AnyOfInfo valueAnyOf = NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo;
        int i = 0;
        reader.startArray();
        while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
            PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
            Object value = _readNode(reader, valueType, valueClazz, valueAnyOf, cps);
            list.add(value);
        }
        reader.endArray();
        return list;
    }

    /**
     * Reads array token into set with typed elements.
     */
    private static Set<Object> _readSetWithElementType(StreamingReader reader, Class<?> rawClazz,
                                                        Type valueType, Class<?> valueClazz, PathSegment ps)
            throws IOException {
        if (reader.peekToken() == StreamingReader.Token.NULL) {
            reader.nextNull();
            return null;
        }
        Set<Object> set = Sjf4jConfig.global().setSupplier.create();
        NodeRegistry.AnyOfInfo valueAnyOf = NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo;
        int i = 0;
        reader.startArray();
        while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
            PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
            Object value = _readNode(reader, valueType, valueClazz, valueAnyOf, cps);
            set.add(value);
        }
        reader.endArray();
        return set;
    }

    /**
     * Reads array token into Java array with typed elements.
     */
    private static Object _readArrayWithElementType(StreamingReader reader, Class<?> rawClazz,
                                                    Type valueType, Class<?> valueClazz, PathSegment ps)
            throws IOException {
        if (reader.peekToken() == StreamingReader.Token.NULL) {
            reader.nextNull();
            return null;
        }
        List<Object> list = new ArrayList<>();
        NodeRegistry.AnyOfInfo valueAnyOf = NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo;
        int i = 0;
        reader.startArray();
        while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
            PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
            Object value = _readNode(reader, valueType, valueClazz, valueAnyOf, cps);
            list.add(value);
        }
        reader.endArray();

        Object array = Array.newInstance(rawClazz.getComponentType(), list.size());
        for (int j = 0, len = list.size(); j < len; j++) {
            Array.set(array, j, list.get(j));
        }
        return array;
    }

    /**
     * Reads AnyOf target by discriminator or token kind.
     */
    private static Object _readAnyOf(StreamingReader reader, Type type, Class<?> rawClazz,
                                     NodeRegistry.AnyOfInfo anyOfInfo, PathSegment ps)
            throws IOException {
        Class<?> targetClazz;

        if (anyOfInfo.hasDiscriminator()) {
            if (anyOfInfo.getScope() != AnyOf.Scope.SELF) {
                throw new BindingException("AnyOf scope '" + anyOfInfo.getScope() + "' is not supported", ps);
            }

            Object rawNode = _readNode(reader, Object.class, Object.class, null, ps);
            if (!(rawNode instanceof Map)) {
                if (anyOfInfo.getOnNoMatch() == AnyOf.OnNoMatch.FAILBACK_NULL) return null;
                throw new BindingException("Node must be a JSON object, when AnyOf has a SELF discriminator", ps);
            }
            Object discriminatorValue = null;
            if (!anyOfInfo.getKey().isEmpty()) {
                discriminatorValue = ((Map<?, ?>) rawNode).get(anyOfInfo.getKey());
            } else if (!anyOfInfo.getPath().isEmpty()) {
                discriminatorValue = anyOfInfo.getCompiledPath().getNode(rawNode);
            }
            if (discriminatorValue == null) {
                if (anyOfInfo.getOnNoMatch() == AnyOf.OnNoMatch.FAILBACK_NULL) return null;
                throw new BindingException("Not found value for discriminator key '" + anyOfInfo.getKey() + "'", ps);
            }

            targetClazz = anyOfInfo.resolveByWhen(discriminatorValue);
            if (targetClazz == null) {
                if (anyOfInfo.getOnNoMatch() == AnyOf.OnNoMatch.FAILBACK_NULL) return null;
                throw new BindingException("AnyOf discriminator has no matching mapping: value='" + discriminatorValue + "'", ps);
            }
            return Sjf4jConfig.global().getNodeFacade().readNode(rawNode, targetClazz);
        }

        JsonType jsonType = reader.peekToken().jsonType();
        targetClazz = anyOfInfo.resolveByJsonType(jsonType);
        if (targetClazz == null) {
            if (anyOfInfo.getOnNoMatch() == AnyOf.OnNoMatch.FAILBACK_NULL) {
                _readNode(reader, Object.class, Object.class, null, ps);
                return null;
            }
            throw new BindingException("AnyOf mapping does not support jsonType=" + jsonType +
                    " for type '" + rawClazz.getName() + "'", ps);
        }
        return _readNode(reader, targetClazz, Types.rawBox(targetClazz), null, ps);
    }

    /// Write

    /**
     * Writes one node to streaming writer.
     */
    public static void writeNode(StreamingWriter writer, Object node) throws IOException {
        Objects.requireNonNull(writer, "writer is null");
        _writeNode(writer, node,
                Sjf4jConfig.global().isBindingPath() ? PathSegment.Root.INSTANCE : null);
    }

    /**
     * Writes node recursively as streaming tokens.
     */
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
                Object raw = ti.valueCodecInfo.valueToRaw(node);
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

            throw new BindingException("Unsupported node type '" + Types.name(node) + "'", ps);

        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to write node of type '" + Types.name(node) + "'", ps, e);
        }
    }


}
