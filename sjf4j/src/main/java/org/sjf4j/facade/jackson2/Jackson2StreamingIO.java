package org.sjf4j.facade.jackson2;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.util.TokenBuffer;
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
 * Jackson2 streaming implementation aligned with {@link org.sjf4j.facade.StreamingIO} semantics.
 */
public class Jackson2StreamingIO {
    private static final Object UNSET = new Object();

    /// Read

    /**
     * Skips next scalar or nested value from parser.
     */
    private static void _skipNode(JsonParser parser) throws IOException {
        JsonToken tk = parser.currentToken();
        if (tk.isScalarValue()) {
            parser.nextToken();
        } else {
            parser.skipChildren();
            parser.nextToken();
        }
    }

    /**
     * Reads one node from Jackson2 parser into target type.
     */
    public static Object readNode(JsonParser parser, Type type) throws IOException {
        Class<?> rawBox = Types.rawBox(type);
        NodeRegistry.AnyOfInfo anyOfInfo = NodeRegistry.registerTypeInfo(rawBox).anyOfInfo;
        return _readNode(parser, type, rawBox, anyOfInfo);
    }

    /**
     * Reads next token and dispatches to typed node readers.
     */
    private static Object _readNode(JsonParser parser, Type type, Class<?> rawClazz,
                                    NodeRegistry.AnyOfInfo anyOfInfo)
            throws IOException {
        try {
            if (anyOfInfo != null) {
                return readAnyOf(parser, anyOfInfo);
            }
            if (rawClazz == Object.class) {
                return _readRawNode(parser);
            }
            StreamingReader.Token token = _peekToken(parser);
            switch (token) {
                case START_OBJECT:
                    return _readObject(parser, type, rawClazz);
                case START_ARRAY:
                    return _readArray(parser, type, rawClazz);
                case STRING:
                    return _readString(parser, rawClazz);
                case NUMBER:
                    return _readNumber(parser, rawClazz);
                case BOOLEAN:
                    return _readBoolean(parser, rawClazz);
                case NULL:
                    return _readNull(parser, rawClazz);
                default:
                    throw new JsonException("Unexpected token '" + token + "'");
            }
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to read streaming into '" + type + "'", null, e);
        }
    }

    private static Object _readRawNode(JsonParser parser) throws IOException {
        switch (_peekToken(parser)) {
            case START_OBJECT:
                return _readRawObject(parser);
            case START_ARRAY:
                return _readRawArray(parser);
            case STRING: {
                String s = parser.getText();
                parser.nextToken();
                return s;
            }
            case NUMBER: {
                Number n = parser.getNumberValue();
                parser.nextToken();
                return n;
            }
            case BOOLEAN: {
                boolean b = parser.getBooleanValue();
                parser.nextToken();
                return b;
            }
            case NULL:
                parser.nextToken();
                return null;
            default:
                throw new JsonException("Unexpected token '" + parser.currentToken() + "'");
        }
    }

    private static Map<String, Object> _readRawObject(JsonParser parser) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_OBJECT) {
            String key = parser.currentName();
            parser.nextToken();
            map.put(key, _readRawNode(parser));
        }
        parser.nextToken();
        return map;
    }

    private static List<Object> _readRawArray(JsonParser parser) throws IOException {
        List<Object> list = new ArrayList<>();
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_ARRAY) {
            list.add(_readRawNode(parser));
        }
        parser.nextToken();
        return list;
    }

    private static Object _readNull(JsonParser parser, Class<?> rawClazz)
            throws IOException {
        parser.nextToken();

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            return vci.rawToValue(null);
        }
        return null;
    }

    private static Object _readBoolean(JsonParser parser, Class<?> rawClazz)
            throws IOException {
        if (rawClazz == Boolean.class) {
            boolean b = parser.getBooleanValue();
            parser.nextToken();
            return b;
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            boolean b = parser.getBooleanValue();
            parser.nextToken();
            return vci.rawToValue(b);
        }

        throw new BindingException("Cannot read boolean value into type " + rawClazz.getName());
    }

    private static Object _readNumber(JsonParser parser, Class<?> rawClazz)
            throws IOException {
        if (rawClazz == Number.class) {
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

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            Number n = parser.getNumberValue();
            parser.nextToken();
            return vci.rawToValue(n);
        }

        throw new BindingException("Cannot read number value into type " + rawClazz.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object _readString(JsonParser parser, Class<?> rawClazz)
            throws IOException {
        if (rawClazz == String.class) {
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

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            String s = parser.getText();
            parser.nextToken();
            return vci.rawToValue(s);
        }

        throw new BindingException("Cannot read string value into type " + rawClazz.getName());
    }

    /**
     * Reads object token into Map/JsonObject/POJO target.
     */
    private static Object _readObject(JsonParser parser, Type type, Class<?> rawClazz)
            throws IOException {
        if (Map.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readMap(parser, rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo);
        }

        if (rawClazz == JsonObject.class) {
            return new JsonObject(_readRawObject(parser));
        }

        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueClazz = Types.rawBox(valueType);
            Map<String, Object> map = _readMap(parser, vci.rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo);
            return vci.rawToValue(map);
        }

        NodeRegistry.PojoInfo pi = ti.pojoInfo;
        if (pi != null && !pi.isJajo) {
            return readPojo(parser, type, rawClazz, pi);
        }

        throw new BindingException("Cannot read object value into type " + rawClazz.getName());
    }

    public static Object readPojo(JsonParser parser, Type ownerType, Class<?> ownerRawClazz,
                                  NodeRegistry.PojoInfo pi) throws IOException {
        NodeRegistry.CreatorInfo ci = pi.creatorInfo;
        boolean hasParentAnyOf = pi.hasParentScopeAnyOf;

        if (!hasParentAnyOf && ci.hasNoArgsCreator() && (ci.argNames == null || ci.argNames.length == 0)) {
            Object pojo = ci.newPojoNoArgs();
            Map<String, Object> dynamicMap = null;
            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_OBJECT) {
                String key = parser.currentName();
                parser.nextToken();

                NodeRegistry.FieldInfo fi = pi.aliasFields != null ? pi.aliasFields.get(key) : pi.fields.get(key);
                if (fi != null) {
                    Object vv = _readField(parser, fi, ownerType, ownerRawClazz);
                    fi.invokeSetterIfPresent(pojo, vv);
                } else if (pi.isJojo) {
                    if (dynamicMap == null) {
                        dynamicMap = new LinkedHashMap<>();
                    }
                    dynamicMap.put(key, _readRawNode(parser));
                } else {
                    _skipNode(parser);
                }
            }
            parser.nextToken();
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

        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_OBJECT) {
            String key = parser.currentName();
            parser.nextToken();

            int argIdx = session.resolveArgIndex(key);
            if (argIdx >= 0) {
                Type argType = Types.resolveMemberType(ownerType, ownerRawClazz, ci.argTypes[argIdx]);
                Class<?> argRaw = Types.rawBox(argType);
                NodeRegistry.AnyOfInfo argAnyOf = NodeRegistry.registerTypeInfo(argRaw).anyOfInfo;
                Object argValue = _readNode(parser, argType, argRaw, argAnyOf);
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
                        vv = _readNode(parser, targetClazz, Types.rawBox(targetClazz), null);
                    } else {
                        if (deferredParentAnyOfFi != null) {
                            throw new BindingException("At most one AnyOf field with scope=PARENT is supported per class");
                        }
                        deferredParentAnyOfFi = fi;
                        deferredParentAnyOfRaw = _readRawNode(parser);
                        continue;
                    }
                } else {
                    vv = _readField(parser, fi, ownerType, ownerRawClazz);
                }
                if (parentAnyOfKey != null && parentAnyOfKey.equals(key)) {
                    parentAnyOfValue = vv;
                }
                session.acceptResolvedField(-1, vv, fi);
                continue;
            }

            if (pi.isJojo) {
                Object vv = _readRawNode(parser);
                session.acceptResolvedJsonEntry(-1, key, vv);
                if (parentAnyOfKey != null && parentAnyOfKey.equals(key)) {
                    parentAnyOfValue = vv;
                }
            } else {
                _skipNode(parser);
            }
        }
        parser.nextToken();

        Object pojo = session.finishField();
        StreamingIO.applyDeferredParentAnyOf(pojo, pi, deferredParentAnyOfFi, deferredParentAnyOfRaw,
                parentAnyOfValue, UNSET);
        return pojo;
    }

    /**
     * Reads array token into List/JsonArray/array/Set target.
     */
    private static Object _readArray(JsonParser parser, Type type, Class<?> rawClazz)
            throws IOException {
        if (List.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readList(parser, rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo);
        }

        if (rawClazz == JsonArray.class) {
            return new JsonArray(_readRawArray(parser));
        }

        if (Set.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readSet(parser, rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo);
        }

        if (rawClazz.isArray()) {
            Class<?> compType = rawClazz.getComponentType();
            Class<?> valueClazz = Types.box(compType);
            return _readArray(parser, rawClazz, compType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo);
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            JsonArray ja = (JsonArray) NodeRegistry.registerPojoOrElseThrow(rawClazz).creatorInfo.forceNewPojo();
            Class<?> elemType = ja.elementType();
            Class<?> elemRaw = Types.box(elemType);
            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_ARRAY) {
                NodeRegistry.AnyOfInfo elemAnyOf = NodeRegistry.registerTypeInfo(elemRaw).anyOfInfo;
                Object value = _readNode(parser, elemType, elemRaw, elemAnyOf);
                ja.add(value);
            }
            parser.nextToken();
            return ja;
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
        if (vci != null) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            List<Object> list = _readList(parser, vci.rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz).anyOfInfo);
            return vci.rawToValue(list);
        }

        throw new BindingException("Cannot read array value into type " + rawClazz.getName());
    }

    private static Object _readField(JsonParser parser, NodeRegistry.FieldInfo fi,
                                     Type ownerType, Class<?> ownerRawClazz)
            throws IOException {
        Type fieldType = Types.resolveMemberType(ownerType, ownerRawClazz, fi.type);
        Class<?> fieldRaw = fieldType == fi.type ? fi.rawClazz : Types.rawBox(fieldType);

        NodeRegistry.AnyOfInfo fieldAnyOf = fi.anyOfInfo;
        if (fieldAnyOf == null && fieldRaw != fi.rawClazz) {
            fieldAnyOf = NodeRegistry.registerTypeInfo(fieldRaw).anyOfInfo;
        }
        if (fieldAnyOf != null) {
            return _readNode(parser, fieldType, fieldRaw, fieldAnyOf);
        }

        switch (fieldType == fi.type ? fi.containerKind : NodeRegistry.FieldInfo.ContainerKind.NONE) {
            case MAP:
                return _readMap(parser, fi.rawClazz, fi.argType, fi.argRawClazz, fi.argAnyOfInfo);
            case LIST:
                return _readList(parser, fi.rawClazz, fi.argType, fi.argRawClazz, fi.argAnyOfInfo);
            case SET:
                return _readSet(parser, fi.rawClazz, fi.argType, fi.argRawClazz, fi.argAnyOfInfo);
            case ARRAY:
                return _readArray(parser, fi.rawClazz, fi.argType, fi.argRawClazz, fi.argAnyOfInfo);
            default:
                return _readNode(parser, fieldType, fieldRaw, null);
        }
    }

    private static Map<String, Object> _readMap(JsonParser parser, Class<?> mapClazz,
                                                Type valueType, Class<?> valueClazz,
                                                NodeRegistry.AnyOfInfo valueAnyOf)
            throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            parser.nextToken();
            return null;
        }
        Map<String, Object> map = mapClazz == Object.class || mapClazz == Map.class || mapClazz == LinkedHashMap.class
                ? new LinkedHashMap<>()
                : NodeRegistry.newMapContainer(mapClazz, false);
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_OBJECT) {
            String key = parser.currentName();
            parser.nextToken();
            Object value = _readNode(parser, valueType, valueClazz, valueAnyOf);
            map.put(key, value);
        }
        parser.nextToken();
        return map;
    }

    private static List<Object> _readList(JsonParser parser, Class<?> listClazz,
                                          Type valueType, Class<?> valueClazz,
                                          NodeRegistry.AnyOfInfo valueAnyOf)
            throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            parser.nextToken();
            return null;
        }
        List<Object> list = listClazz == Object.class || listClazz == List.class || listClazz == ArrayList.class
                ? new ArrayList<>()
                : NodeRegistry.newListContainer(listClazz, false);
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_ARRAY) {
            Object value = _readNode(parser, valueType, valueClazz, valueAnyOf);
            list.add(value);
        }
        parser.nextToken();
        return list;
    }

    private static Set<Object> _readSet(JsonParser parser, Class<?> setClazz,
                                        Type valueType, Class<?> valueClazz,
                                        NodeRegistry.AnyOfInfo valueAnyOf)
            throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            parser.nextToken();
            return null;
        }
        Set<Object> set = setClazz == Object.class || setClazz == Set.class || setClazz == LinkedHashSet.class
                ? new LinkedHashSet<>()
                : NodeRegistry.newSetContainer(setClazz, false);
        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_ARRAY) {
            Object value = _readNode(parser, valueType, valueClazz, valueAnyOf);
            set.add(value);
        }
        parser.nextToken();
        return set;
    }

    private static Object _readArray(JsonParser parser, Class<?> rawClazz,
                                     Type valueType, Class<?> valueClazz,
                                     NodeRegistry.AnyOfInfo valueAnyOf)
            throws IOException {
        List<Object> list = _readList(parser, List.class, valueType, valueClazz, valueAnyOf);
        if (list == null) {
            return null;
        }

        Object array = Array.newInstance(rawClazz.getComponentType(), list.size());
        for (int j = 0, len = list.size(); j < len; j++) {
            Array.set(array, j, list.get(j));
        }
        return array;
    }

    public static Object readAnyOf(JsonParser parser, NodeRegistry.AnyOfInfo anyOfInfo) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == null) {
            token = parser.nextToken();
        }

        if (anyOfInfo.hasDiscriminator) {
            if (token == JsonToken.START_OBJECT && anyOfInfo.scope == AnyOf.Scope.CURRENT && !anyOfInfo.key.isEmpty()) {
                TokenBuffer rawBuffer = _bufferCurrentValue(parser);
                Class<?> targetClazz = _resolveKeyDiscriminatorTarget(rawBuffer, anyOfInfo);
                if (targetClazz == null) return null;
                JsonParser targetParser = rawBuffer.asParserOnFirstToken();
                try {
                    return _readNode(targetParser, targetClazz, Types.rawBox(targetClazz), null);
                } finally {
                    targetParser.close();
                }
            }

            Object rawNode = _readRawNode(parser);
            Class<?> targetClazz = StreamingIO.resolveSelfDiscriminatorTarget(rawNode, anyOfInfo);
            if (targetClazz == null) return null;
            return FacadeFactory.defaultNodeFacade().readNode(rawNode, targetClazz);
        }

        Class<?> targetClazz = StreamingIO.resolveAnyOfJsonTypeTarget(_peekToken(parser).jsonType(), anyOfInfo);
        if (targetClazz == null) {
            _readRawNode(parser);
            return null;
        }

        return _readNode(parser, targetClazz, Types.rawBox(targetClazz), null);
    }

    private static TokenBuffer _bufferCurrentValue(JsonParser parser) throws IOException {
        TokenBuffer rawBuffer = new TokenBuffer(parser);
        rawBuffer.copyCurrentStructure(parser);
        parser.nextToken();
        return rawBuffer;
    }

    private static Class<?> _resolveKeyDiscriminatorTarget(TokenBuffer rawBuffer, NodeRegistry.AnyOfInfo anyOfInfo)
            throws IOException {
        JsonParser discriminatorParser = rawBuffer.asParserOnFirstToken();
        try {
            JsonToken token = discriminatorParser.currentToken();
            if (token == null) {
                token = discriminatorParser.nextToken();
            }
            if (token != JsonToken.START_OBJECT) {
                if (anyOfInfo.onNoMatch == AnyOf.OnNoMatch.FAILBACK_NULL) return null;
                throw new BindingException("Node must be a JSON object, when AnyOf has a SELF discriminator");
            }
            while (discriminatorParser.nextToken() != JsonToken.END_OBJECT) {
                String name = discriminatorParser.currentName();
                JsonToken valueToken = discriminatorParser.nextToken();
                if (anyOfInfo.key.equals(name)) {
                    return StreamingIO.resolveAnyOfDiscriminatorTarget(
                            _readDiscriminatorValue(discriminatorParser, valueToken), anyOfInfo);
                }
                discriminatorParser.skipChildren();
            }
            return StreamingIO.resolveAnyOfDiscriminatorTarget(null, anyOfInfo);
        } finally {
            discriminatorParser.close();
        }
    }

    private static Object _readDiscriminatorValue(JsonParser parser, JsonToken valueToken) throws IOException {
        if (valueToken == JsonToken.VALUE_NULL) return null;
        if (valueToken == JsonToken.VALUE_STRING) return parser.getText();
        if (valueToken == JsonToken.VALUE_NUMBER_INT || valueToken == JsonToken.VALUE_NUMBER_FLOAT) {
            return parser.getNumberValue();
        }
        if (valueToken == JsonToken.VALUE_TRUE || valueToken == JsonToken.VALUE_FALSE) {
            return parser.getBooleanValue();
        }
        return _readRawNode(parser);
    }

    /// Reader

    /**
     * Peeks token kind from current parser state.
     */
    private static StreamingReader.Token _peekToken(JsonParser parser) throws IOException {
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

    /**
     * Writes one node to Jackson2 generator.
     */
    public static void writeNode(JsonGenerator gen, Object node) throws IOException {
        Objects.requireNonNull(gen, "gen");
        _writeNode(gen, node);
    }

    /**
     * Writes node recursively as JSON tokens.
     */
    private static void _writeNode(JsonGenerator gen, Object node) throws IOException {
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
                _writeValue(gen, (Number) node);
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
                    _writeNode(gen, entry.getValue());
                }
                gen.writeEndObject();
                return;
            }

            if (node instanceof List) {
                gen.writeStartArray();
                List<?> list = (List<?>) node;
                for (int i = 0, len = list.size(); i < len; i++) {
                    _writeNode(gen, list.get(i));
                }
                gen.writeEndArray();
                return;
            }

            if (node instanceof JsonObject) {
                gen.writeStartObject();
                ((JsonObject) node).forEach((k, v) -> {
                    try {
                        gen.writeFieldName(k);
                        _writeNode(gen, v);
                    } catch (IOException e) {
                        throw new BindingException(e);
                    }
                });
                gen.writeEndObject();
                return;
            }

            if (node instanceof JsonArray) {
                gen.writeStartArray();
                JsonArray ja = (JsonArray) node;
                for (int i = 0, len = ja.size(); i < len; i++) {
                    _writeNode(gen, ja.getNode(i));
                }
                gen.writeEndArray();
                return;
            }

            if (rawClazz.isArray()) {
                gen.writeStartArray();
                int len = Array.getLength(node);
                for (int i = 0; i < len; i++) {
                    _writeNode(gen, Array.get(node, i));
                }
                gen.writeEndArray();
                return;
            }

            if (node instanceof Set) {
                gen.writeStartArray();
                for (Object v : (Set<?>) node) {
                    _writeNode(gen, v);
                }
                gen.writeEndArray();
                return;
            }

            NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
            NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodecInfo(rawClazz);
            if (vci != null) {
                Object raw = vci.valueToRaw(node);
                _writeNode(gen, raw);
                return;
            }

            NodeRegistry.PojoInfo pi = ti.pojoInfo;
            if (pi != null) {
                gen.writeStartObject();
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                    if (!entry.getValue().hasGetter()) {
                        continue;
                    }
                    String key = entry.getKey();
                    gen.writeFieldName(key);
                    Object vv = entry.getValue().invokeGetter(node);
                    _writeNode(gen, vv);
                }
                gen.writeEndObject();
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
     * Writes numeric value using matching Jackson2 overload.
     */
    private static void _writeValue(JsonGenerator gen, Number value) throws IOException {
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
