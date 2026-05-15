package org.sjf4j.facade.jackson2;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.StreamingIO;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Types;
import org.sjf4j.node.ValueCodec;

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
 * Jackson2 streaming implementation aligned with {@link org.sjf4j.facade.StreamingIO} semantics.
 */
public class Jackson2StreamingIO {
    private static final Object UNSET = new Object();

    /// Read

    private static void _skipNode(JsonParser parser) throws IOException {
        JsonToken tk = parser.currentToken();
        if (tk.isScalarValue()) {
            parser.nextToken();
        } else {
            parser.skipChildren();
            parser.nextToken();
        }
    }

    public static Object readNode(JsonParser parser, Type type, StreamingContext context) throws IOException {
        Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(context, "context");
        Class<?> rawBox = Types.rawBox(type);
        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawBox);
        return _readNode(parser, type, rawBox, ti, context);
    }

    private static Object _readNode(JsonParser parser, Type type, Class<?> rawBoxed,
                                    NodeRegistry.TypeInfo ti,
                                    StreamingContext context)
            throws IOException {
        try {
            NodeRegistry.OneOfInfo oneOfInfo = ti != null ? ti.oneOfInfo : null;
            if (oneOfInfo != null) {
                return readOneOf(parser, oneOfInfo, context);
            }
            if (rawBoxed == Object.class) {
                return _readRawNode(parser);
            }
            StreamingReader.Token token = _peekToken(parser);
            switch (token) {
                case START_OBJECT:
                    return _readObject(parser, type, rawBoxed, ti, context);
                case START_ARRAY:
                    return _readArray(parser, type, rawBoxed, ti, context);
                case STRING:
                    return _readString(parser, rawBoxed, context);
                case NUMBER:
                    return _readNumber(parser, rawBoxed, context);
                case BOOLEAN:
                    return _readBoolean(parser, rawBoxed, context);
                case NULL:
                    return _readNull(parser, rawBoxed, context);
                default:
                    throw new BindingException("unexpected token '" + token + "'");
            }
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to read streaming into '" + type + "'", null, e);
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
                throw new BindingException("unexpected token '" + parser.currentToken() + "'");
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

    private static Object _readNull(JsonParser parser, Class<?> rawClazz, StreamingContext context)
            throws IOException {
        parser.nextToken();
        if (rawClazz == Optional.class) return ValueCodec.OPTIONAL.rawToValue(null);
        return null;
    }

    private static Object _readBoolean(JsonParser parser, Class<?> rawClazz, StreamingContext context)
            throws IOException {
        if (rawClazz == Boolean.class) {
            boolean b = parser.getBooleanValue();
            parser.nextToken();
            return b;
        }

        NodeRegistry.ValueCodecInfo vci = StreamingIO.resolveValueCodecInfo(rawClazz, context);
        if (vci != null) {
            boolean b = parser.getBooleanValue();
            parser.nextToken();
            return vci.rawToValue(b);
        }

        throw new BindingException("cannot read boolean value into type '" + rawClazz.getName() + "'");
    }

    private static Object _readNumber(JsonParser parser, Class<?> rawClazz, StreamingContext context)
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

        NodeRegistry.ValueCodecInfo vci = StreamingIO.resolveValueCodecInfo(rawClazz, context);
        if (vci != null) {
            Number n = parser.getNumberValue();
            parser.nextToken();
            return vci.rawToValue(n);
        }

        throw new BindingException("cannot read number value into type '" + rawClazz.getName() + "'");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object _readString(JsonParser parser, Class<?> rawClazz, StreamingContext context)
            throws IOException {
        if (rawClazz == String.class) {
            String s = parser.getText();
            parser.nextToken();
            return s;
        }
        if (rawClazz == Character.class) {
            String s = parser.getText();
            parser.nextToken();
            return !s.isEmpty() ? s.charAt(0) : null;
        }
        if (rawClazz.isEnum()) {
            String s = parser.getText();
            parser.nextToken();
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }

        NodeRegistry.ValueCodecInfo vci = StreamingIO.resolveValueCodecInfo(rawClazz, context);
        if (vci != null) {
            String s = parser.getText();
            parser.nextToken();
            return vci.rawToValue(s);
        }

        throw new BindingException("cannot read string value into type '" + rawClazz.getName() + "'");
    }

    private static Object _readObject(JsonParser parser, Type type, Class<?> rawClazz,
                                       NodeRegistry.TypeInfo ti,
                                       StreamingContext context)
            throws IOException {
        if (Map.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readMap(parser, rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz), context);
        }

        if (rawClazz == JsonObject.class) {
            return new JsonObject(_readRawObject(parser));
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
                Map<String, Object> map = _readMap(parser, vci.rawClazz, valueType, valueClazz, valueTi, context);
                return vci.rawToValue(map);
            }
        }

        NodeRegistry.PojoInfo pi = ti.pojoInfo;
        if (pi != null && !pi.isJajo) {
            return readPojo(parser, type, rawClazz, pi, context);
        }

        throw new BindingException("cannot read object value into type '" + rawClazz.getName() + "'");
    }

    public static Object readPojo(JsonParser parser, Type ownerType, Class<?> ownerRawClazz,
                                  NodeRegistry.PojoInfo pi, StreamingContext context) throws IOException {
        NodeRegistry.CreatorInfo ci = pi.creatorInfo;
        boolean hasParentOneOf = pi.hasParentScopeOneOf;

        if (!hasParentOneOf && ci.hasNoArgsCreator() && (ci.argNames == null || ci.argNames.length == 0)) {
            Object pojo = ci.newPojoNoArgs();
            Map<String, Object> dynamicMap = null;
            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_OBJECT) {
                String key = parser.currentName();
                parser.nextToken();

                NodeRegistry.PropertyInfo fi = pi.aliasProperties != null ? pi.aliasProperties.get(key) : pi.properties.get(key);
                if (fi != null) {
                    Object vv = _readField(parser, fi, ownerType, ownerRawClazz, context);
                    fi.invokeSetterIfPresent(pojo, vv);
                } else if (pi.isJojo && pi.readDynamic) {
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
                ((JsonObject) pojo)._dynamicMap(dynamicMap);
            }
            return pojo;
        }

        NodeRegistry.PojoCreationSession session = new NodeRegistry.PojoCreationSession(pi.creatorInfo, pi.propertyCount);
        NodeRegistry.PropertyInfo deferredParentOneOfFi = null;
        Object deferredParentOneOfRaw = null;
        String parentOneOfKey = null;
        Object parentOneOfValue = UNSET;

        parser.nextToken();
        while (parser.currentToken() != JsonToken.END_OBJECT) {
            String key = parser.currentName();
            parser.nextToken();

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
                    argValue = _readValueWithCodec(parser, argType, argRaw, argVci, context);
                } else {
                    argValue = _readNode(parser, argType, argRaw, ti, context);
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
                        vv = _readNode(parser, targetClazz, Types.rawBox(targetClazz), null, context);
                    } else {
                        if (deferredParentOneOfFi != null) {
                            throw new BindingException("at most one OneOf field with scope=PARENT is supported per class");
                        }
                        deferredParentOneOfFi = fi;
                        deferredParentOneOfRaw = _readRawNode(parser);
                        continue;
                    }
                } else {
                    vv = _readField(parser, fi, ownerType, ownerRawClazz, context);
                }
                if (parentOneOfKey != null && parentOneOfKey.equals(key)) {
                    parentOneOfValue = vv;
                }
                session.acceptProperty(fi, vv);
                continue;
            }

            if (pi.isJojo && pi.readDynamic) {
                Object vv = _readRawNode(parser);
                session.acceptDynamic(key, vv);
                if (parentOneOfKey != null && parentOneOfKey.equals(key)) {
                    parentOneOfValue = vv;
                }
            } else {
                _skipNode(parser);
            }
        }
        parser.nextToken();

        Object pojo = session.finish();
        StreamingIO.applyDeferredParentOneOf(pojo, pi, deferredParentOneOfFi, deferredParentOneOfRaw,
                parentOneOfValue, UNSET, context);
        return pojo;
    }

    private static Object _readArray(JsonParser parser, Type type, Class<?> rawClazz,
                                      NodeRegistry.TypeInfo ti,
                                      StreamingContext context)
            throws IOException {
        if (List.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readList(parser, rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz), context);
        }

        if (rawClazz == JsonArray.class) {
            return new JsonArray(_readRawArray(parser));
        }

        if (Set.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueClazz = Types.rawBox(valueType);
            return _readSet(parser, rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz), context);
        }

        if (rawClazz.isArray()) {
            Class<?> compType = rawClazz.getComponentType();
            Class<?> valueClazz = Types.box(compType);
            return _readArray(parser, rawClazz, compType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz), context);
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            JsonArray ja = (JsonArray) NodeRegistry.registerPojoOrElseThrow(rawClazz).creatorInfo.forceNewPojo();
            Class<?> elemType = ja.elementType();
            Class<?> elemRaw = Types.box(elemType);
            NodeRegistry.TypeInfo elemTi = NodeRegistry.registerTypeInfo(elemRaw);
            parser.nextToken();
            while (parser.currentToken() != JsonToken.END_ARRAY) {
                Object value = _readNode(parser, elemType, elemRaw, elemTi, context);
                ja.add(value);
            }
            parser.nextToken();
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
            List<Object> list = _readList(parser, vci.rawClazz, valueType, valueClazz,
                    NodeRegistry.registerTypeInfo(valueClazz), context);
            return vci.rawToValue(list);
        }

        throw new BindingException("cannot read array value into type '" + rawClazz.getName() + "'");
    }

    private static Object _readField(JsonParser parser, NodeRegistry.PropertyInfo fi,
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
            return readOneOf(parser, fieldOneOf, context);
        }

        if (fi.resolvedValueCodec != null) {
            return _readValueWithCodec(parser, fieldType, fieldRaw, fi.resolvedValueCodec, context);
        }

        switch (fieldType == fi.type ? fi.containerKind : NodeRegistry.PropertyInfo.ContainerKind.NONE) {
            case MAP:
                return _readMap(parser, fi.rawClazz, fi.argType, fi.argRawClazz,
                        NodeRegistry.registerTypeInfo(fi.argRawClazz), context);
            case LIST:
                return _readList(parser, fi.rawClazz, fi.argType, fi.argRawClazz,
                        NodeRegistry.registerTypeInfo(fi.argRawClazz), context);
            case SET:
                return _readSet(parser, fi.rawClazz, fi.argType, fi.argRawClazz,
                        NodeRegistry.registerTypeInfo(fi.argRawClazz), context);
            case ARRAY:
                return _readArray(parser, fi.rawClazz, fi.argType, fi.argRawClazz,
                        NodeRegistry.registerTypeInfo(fi.argRawClazz), context);
            default:
                return _readNode(parser, fieldType, fieldRaw, null, context);
        }
    }

    private static Object _readValueWithCodec(JsonParser parser,
                                              Type type,
                                              Class<?> rawClazz,
                                              NodeRegistry.ValueCodecInfo valueCodecInfo,
                                              StreamingContext context) throws IOException {
        switch (_peekToken(parser)) {
            case START_OBJECT: {
                Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
                Class<?> valueClazz = Types.rawBox(valueType);
                Map<String, Object> map = _readMap(parser, valueCodecInfo.rawClazz, valueType, valueClazz,
                        NodeRegistry.registerTypeInfo(valueClazz), context);
                return valueCodecInfo.rawToValue(map);
            }
            case START_ARRAY: {
                Type valueType = Types.resolveTypeArgument(type, List.class, 0);
                Class<?> valueClazz = Types.rawBox(valueType);
                List<Object> list = _readList(parser, valueCodecInfo.rawClazz, valueType, valueClazz,
                        NodeRegistry.registerTypeInfo(valueClazz), context);
                return valueCodecInfo.rawToValue(list);
            }
            case STRING: {
                String s = parser.getText();
                parser.nextToken();
                return valueCodecInfo.rawToValue(s);
            }
            case NUMBER: {
                Number n = parser.getNumberValue();
                parser.nextToken();
                return valueCodecInfo.rawToValue(n);
            }
            case BOOLEAN: {
                boolean b = parser.getBooleanValue();
                parser.nextToken();
                return valueCodecInfo.rawToValue(b);
            }
            case NULL:
                parser.nextToken();
                return valueCodecInfo.rawToValue(null);
            default:
                throw new BindingException("cannot read value into type '" + rawClazz.getName() + "'");
        }
    }

    private static Map<String, Object> _readMap(JsonParser parser, Class<?> mapClazz,
                                                 Type valueType, Class<?> valueClazz,
                                                 NodeRegistry.TypeInfo valueTi,
                                                 StreamingContext context)
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
            Object value = _readNode(parser, valueType, valueClazz, valueTi, context);
            map.put(key, value);
        }
        parser.nextToken();
        return map;
    }

    private static List<Object> _readList(JsonParser parser, Class<?> listClazz,
                                           Type valueType, Class<?> valueClazz,
                                           NodeRegistry.TypeInfo valueTi,
                                           StreamingContext context)
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
            Object value = _readNode(parser, valueType, valueClazz, valueTi, context);
            list.add(value);
        }
        parser.nextToken();
        return list;
    }

    private static Set<Object> _readSet(JsonParser parser, Class<?> setClazz,
                                         Type valueType, Class<?> valueClazz,
                                         NodeRegistry.TypeInfo valueTi,
                                         StreamingContext context)
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
            Object value = _readNode(parser, valueType, valueClazz, valueTi, context);
            set.add(value);
        }
        parser.nextToken();
        return set;
    }

    private static Object _readArray(JsonParser parser, Class<?> rawClazz,
                                      Type valueType, Class<?> valueClazz,
                                      NodeRegistry.TypeInfo valueTi,
                                      StreamingContext context)
            throws IOException {
        List<Object> list = _readList(parser, List.class, valueType, valueClazz, valueTi, context);
        if (list == null) {
            return null;
        }

        Object array = Array.newInstance(rawClazz.getComponentType(), list.size());
        for (int j = 0, len = list.size(); j < len; j++) {
            Array.set(array, j, list.get(j));
        }
        return array;
    }

    public static Object readOneOf(JsonParser parser, NodeRegistry.OneOfInfo anyOfInfo,
                                   StreamingContext context) throws IOException {
        Objects.requireNonNull(context, "context");
        JsonToken token = parser.currentToken();
        if (token == null) {
            token = parser.nextToken();
        }

        if (anyOfInfo.hasDiscriminator) {
            if (token == JsonToken.START_OBJECT && anyOfInfo.scope == OneOf.Scope.CURRENT && !anyOfInfo.key.isEmpty()) {
                TokenBuffer rawBuffer = _bufferCurrentValue(parser);
                Class<?> targetClazz = _resolveKeyDiscriminatorTarget(rawBuffer, anyOfInfo);
                if (targetClazz == null) return null;
                JsonParser targetParser = rawBuffer.asParserOnFirstToken();
                try {
                    return _readNode(targetParser, targetClazz, Types.rawBox(targetClazz), null, context);
                } finally {
                    targetParser.close();
                }
            }

            Object rawNode = _readRawNode(parser);
            Class<?> targetClazz = StreamingIO.resolveCurrentDiscriminatorTarget(rawNode, anyOfInfo);
            if (targetClazz == null) return null;
            return context.nodeFacade.readNode(rawNode, targetClazz);
        }

        Class<?> targetClazz = StreamingIO.resolveOneOfJsonTypeTarget(_peekToken(parser).jsonType(), anyOfInfo);
        if (targetClazz == null) {
            _readRawNode(parser);
            return null;
        }

        return _readNode(parser, targetClazz, Types.rawBox(targetClazz), null, context);
    }

    private static TokenBuffer _bufferCurrentValue(JsonParser parser) throws IOException {
        TokenBuffer rawBuffer = new TokenBuffer(parser);
        rawBuffer.copyCurrentStructure(parser);
        parser.nextToken();
        return rawBuffer;
    }

    private static Class<?> _resolveKeyDiscriminatorTarget(TokenBuffer rawBuffer, NodeRegistry.OneOfInfo anyOfInfo)
            throws IOException {
        JsonParser discriminatorParser = rawBuffer.asParserOnFirstToken();
        try {
            JsonToken token = discriminatorParser.currentToken();
            if (token == null) {
                token = discriminatorParser.nextToken();
            }
            if (token != JsonToken.START_OBJECT) {
                if (anyOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) return null;
                throw new BindingException("node must be a JSON object, when OneOf has a CURRENT discriminator");
            }
            while (discriminatorParser.nextToken() != JsonToken.END_OBJECT) {
                String name = discriminatorParser.currentName();
                JsonToken valueToken = discriminatorParser.nextToken();
                if (anyOfInfo.key.equals(name)) {
                    return StreamingIO.resolveOneOfDiscriminatorTarget(
                            _readDiscriminatorValue(discriminatorParser, valueToken), anyOfInfo);
                }
                discriminatorParser.skipChildren();
            }
            return StreamingIO.resolveOneOfDiscriminatorTarget(null, anyOfInfo);
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

    public static void writeNode(JsonGenerator gen, Object node, StreamingContext context) throws IOException {
        Objects.requireNonNull(gen, "gen");
        Objects.requireNonNull(context, "context");
        _writeNode(gen, node, context);
    }

    private static void _writeNode(JsonGenerator gen, Object node, StreamingContext context) throws IOException {
        try {
            if (node == null) {
                gen.writeNull();
                return;
            }

            if (node instanceof String) {
                gen.writeString(node.toString());
                return;
            }
            if (node instanceof Number) {
                _writeNumber(gen, (Number) node);
                return;
            }
            if (node instanceof Boolean) {
                gen.writeBoolean((Boolean) node);
                return;
            }

            if (node instanceof Map) {
                gen.writeStartObject();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                    Object value = entry.getValue();
                    if (value == null && !context.includeNulls) continue;
                    String key = entry.getKey().toString();
                    gen.writeFieldName(key);
                    _writeNode(gen, value, context);
                }
                gen.writeEndObject();
                return;
            }

            if (node instanceof List) {
                gen.writeStartArray();
                List<?> list = (List<?>) node;
                for (int i = 0, len = list.size(); i < len; i++) {
                    _writeNode(gen, list.get(i), context);
                }
                gen.writeEndArray();
                return;
            }

            Class<?> rawClazz = node.getClass();
            if (rawClazz == JsonObject.class) {
                gen.writeStartObject();
                for (Map.Entry<String, Object> entry : ((JsonObject) node).entrySet()) {
                    Object value = entry.getValue();
                    if (value == null && !context.includeNulls) continue;
                    gen.writeFieldName(entry.getKey());
                    _writeNode(gen, value, context);
                }
                gen.writeEndObject();
                return;
            }

            if (node instanceof JsonArray) {
                gen.writeStartArray();
                JsonArray ja = (JsonArray) node;
                for (int i = 0, len = ja.size(); i < len; i++) {
                    _writeNode(gen, ja.getNode(i), context);
                }
                gen.writeEndArray();
                return;
            }

            if (rawClazz.isArray()) {
                gen.writeStartArray();
                int len = Array.getLength(node);
                for (int i = 0; i < len; i++) {
                    _writeNode(gen, Array.get(node, i), context);
                }
                gen.writeEndArray();
                return;
            }

            if (node instanceof Set) {
                gen.writeStartArray();
                for (Object v : (Set<?>) node) {
                    _writeNode(gen, v, context);
                }
                gen.writeEndArray();
                return;
            }

            if (node instanceof Character) {
                gen.writeString(node.toString());
                return;
            }
            if (node instanceof Enum) {
                gen.writeString(((Enum<?>) node).name());
                return;
            }

            NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
            if (ti.hasValueCodecs()) {
                String valueFormat = context.defaultValueFormat(rawClazz);
                NodeRegistry.ValueCodecInfo vci = ti.getValueCodecInfo(valueFormat);
                if (vci != null) {
                    Object raw = vci.valueToRaw(node);
                    _writeNode(gen, raw, context);
                    return;
                }
            }

            NodeRegistry.PojoInfo pi = ti.pojoInfo;
            if (pi != null) {
                writePojo(gen, node, pi, context);
                return;
            }

            throw new BindingException("unsupported node type '" + Types.name(node) + "'");
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to write node of type '" + Types.name(node) + "'", null, e);
        }
    }

    public static void writePojo(JsonGenerator gen, Object node, NodeRegistry.PojoInfo pi,
                                 StreamingContext context) throws IOException {
        gen.writeStartObject();
        for (Map.Entry<String, NodeRegistry.PropertyInfo> entry : pi.readableProperties.entrySet()) {
            Object vv = entry.getValue().invokeGetter(node);
            if (vv == null && !context.includeNulls) continue;
            String key = entry.getKey();
            gen.writeFieldName(key);
            if (vv == null) {
                gen.writeNull();
            } else {
                NodeRegistry.PropertyInfo fi = entry.getValue();
                if (fi.resolvedValueCodec != null) {
                    vv = fi.resolvedValueCodec.valueToRaw(vv);
                }
                _writeNode(gen, vv, context);
            }
        }
        if (pi.isJojo && pi.writeDynamic) {
            Map<String, Object> dynamicMap = ((JsonObject) node)._dynamicMap();
            if (dynamicMap != null) {
                for (Map.Entry<String, Object> entry : dynamicMap.entrySet()) {
                    Object value = entry.getValue();
                    if (value == null && !context.includeNulls) continue;
                    gen.writeFieldName(entry.getKey());
                    _writeNode(gen, value, context);
                }
            }
        }
        gen.writeEndObject();
    }

    private static void _writeNumber(JsonGenerator gen, Number value) throws IOException {
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

    /// Support

}
