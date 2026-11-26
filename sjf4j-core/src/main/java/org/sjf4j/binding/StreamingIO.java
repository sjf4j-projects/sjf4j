package org.sjf4j.binding;

import org.sjf4j.InternalAccess;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.NodeConfig;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.node.NodeMapper;
import org.sjf4j.meta.PropertyInfo;
import org.sjf4j.meta.OneOfInfo;
import org.sjf4j.meta.PojoInfo;
import org.sjf4j.meta.CreatorInfo;
import org.sjf4j.meta.LazyTypeInfo;
import org.sjf4j.meta.CreatorState;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.util.Types;
import org.sjf4j.util.Asserts;
import org.sjf4j.value.ValueInfo;
import org.sjf4j.value.ValueSet;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Set;

/**
 * Shared target-directed streaming binding for POJOs, containers, logical
 * values, and raw OBNT nodes. Backend readers and writers provide format
 * tokens while this class applies the common Java binding rules.
 */
public final class StreamingIO {

    private static final Object UNSET = new Object();

    private StreamingIO() {
    }


    /* Read */

    /**
     * Reads one pending value into the requested class or generic type.
     * The caller owns document start/end and parser lifecycle.
     */
    public static Object readNode(StreamingParser parser, Type type,
                                  NodeConfig config) throws IOException {
        Asserts.notNull(parser, "parser");
        Asserts.notNull(type, "type");
        Asserts.notNull(config, "config");

        Class<?> boxed = Types.rawBox(type);
        // The common scalar and collection paths need no registered metadata.
        return readNode(parser, type, boxed, null, config);
    }


    static Object readNode(StreamingParser parser, Type type, Class<?> boxed, TypeInfo typeInfo,
                           NodeConfig config) throws IOException {
        try {
            /*
             * Do NOT probe null here.
             *
             * A failed nextIfNull() would physically advance token-based
             * parsers such as Jackson and force all following reads through
             * their prefetched path.
             *
             * Null handling is therefore delegated to the target-specific
             * operation below.
             */

            if (typeInfo != null && typeInfo != TypeInfo.NONE) {
                return _readRegistered(parser, type, boxed, typeInfo, config);
            }

            if (boxed == Object.class) {
                return parser.readRaw(config);
            }

            /*
             * Scalars.
             *
             * Primitive and boxed targets share the boxed dispatch below;
             * the scalar readers used here are nullable.
             */

            if (boxed == String.class) {
                return parser.readString();
            }

            if (boxed == Character.class) {
                return parser.readChar();
            }

            if (boxed == Boolean.class) {
                return parser.readBoolean();
            }

            if (boxed == Integer.class) {
                return parser.readInt();
            }

            if (boxed == Long.class) {
                return parser.readLong();
            }

            if (boxed == Short.class) {
                return parser.readShort();
            }

            if (boxed == Byte.class) {
                return parser.readByte();
            }

            if (boxed == Float.class) {
                return parser.readFloat();
            }

            if (boxed == Double.class) {
                return parser.readDouble();
            }

            if (boxed == Number.class) {
                return config.exactNumbers ? parser.readNumberExact() : parser.readNumber();
            }

            if (boxed == BigInteger.class) {
                return parser.readBigInteger();
            }

            if (boxed == BigDecimal.class) {
                return parser.readBigDecimal();
            }

            if (boxed.isEnum()) {
                return readEnum(parser, boxed, config);
            }

            /*
             * Object nodes.
             */

            if (Map.class.isAssignableFrom(boxed)) {
                return readMap(parser, type, boxed, null, null, null, config);
            }

            if (boxed == JsonObject.class) {
                return readJsonObject(parser, config);
            }

            /*
             * Array nodes.
             */

            if (List.class.isAssignableFrom(boxed)) {
                return readList(parser, type, boxed, null, null, null, config);
            }

            if (boxed == JsonArray.class) {
                return readJsonArray(parser, config);
            }

            if (Set.class.isAssignableFrom(boxed)) {
                return readSet(parser, type, boxed, null, null, null, config);
            }

            if (boxed.isArray()) {
                return readArray(parser, type, boxed, null, null, null, config);
            }

            // A null TypeInfo means metadata has not been resolved. NONE
            // means it was resolved and there is no registered binding.
            if (typeInfo == null) {
                TypeInfo resolved = TypeRegistry.registerTypeInfo(boxed);
                if (resolved != TypeInfo.NONE) {
                    return _readRegistered(parser, type, boxed, resolved, config);
                }
            }

            /*
             * Preserve null semantics even for otherwise unsupported
             * reference targets.
             */
            if (parser.nextIfNull()) {
                return null;
            }

            if (!boxed.isInterface() && Modifier.isAbstract(boxed.getModifiers())) {
                throw new BindingException("cannot read object value into abstract type '" + boxed.getName() + "'");
            }

            throw new BindingException("cannot read value into type '" + boxed.getName() + "'");
        } catch (BindingException | IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new BindingException("failed to read node as '" + type.getTypeName() + "'", e);
        }
    }


    /**
     * Direct path for already-resolved types. A known TypeInfo bypasses all
     * scalar/container checks in readNode; container metadata is handled here.
     */
    private static Object _readRegistered(StreamingParser parser, Type type,
                                              Class<?> boxed, TypeInfo ti,
                                              NodeConfig config) throws IOException {
        if (ti.oneOfInfo != null) {
            return OneOfIO.readOneOf(parser, ti.oneOfInfo, config);
        }
        if (ti.valueSet != null) {
            return readValueSet(parser, type, boxed, ti.valueSet, config);
        }
        if (ti.containerInfo != null) {
            // Streaming binding only constructs Java containers that can be
            // populated with put/add. Native builder construction is mapping-only.
            switch (ti.containerInfo.kind) {
                case OBJECT_MAP:
                    return readMap(parser, type, boxed, null, null, null, config);
                case ARRAY_LIST:
                    return readList(parser, type, boxed, null, null, null, config);
                case ARRAY_SET:
                    return readSet(parser, type, boxed, null, null, null, config);
                default:
                    throw new BindingException("unsupported container type '" + boxed.getName() + "'");
            }
        }
        if (ti.externalNode != null) {
            // ExternalNode.convert may use a builder and requires a materialized
            // source. Binding must not silently fall back to Raw + Mapping.
            // JSON null still maps to Java null for reference targets.
            if (parser.nextIfNull()) return null;
            throw new BindingException("streaming binding does not support external target type '" +
                    boxed.getName() + "'");
        }
        if (ti.pojoInfo != null) {
            if (ti.pojoInfo.isJajo) {
                return readJsonArray(parser, type, boxed, ti, config);
            }
            return readPojo(parser, type, boxed, ti.pojoInfo, config);
        }
        if (parser.nextIfNull()) {
            return null;
        }
        throw new BindingException("cannot read value into type '" + boxed.getName() + "'");
    }


    /**
     * --------------------------------------------------------------
     * Enum
     * --------------------------------------------------------------
     */

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object readEnum(StreamingParser parser, Class<?> enumType,
                                  NodeConfig config) throws IOException {

        StreamingParser.Token token = parser.peekToken();
        if (token == StreamingParser.Token.NULL) {
            parser.nextIfNull();
            return null;
        }

        if (token == StreamingParser.Token.STRING) {
            return Enum.valueOf((Class<? extends Enum>) enumType, parser.readString());
        }

        if (token == StreamingParser.Token.NUMBER) {
            int ordinal = parser.readIntValue();
            Enum[] values = ((Class<? extends Enum>) enumType).getEnumConstants();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            throw new BindingException("enum ordinal '" + ordinal + "' out of range for type '" + enumType.getName() + "'");
        }

        throw new BindingException("cannot read enum '" + enumType.getName() + "' from token '" + token + "'");
    }


    /**
     * --------------------------------------------------------------
     * ValueCodec
     * --------------------------------------------------------------
     */

    /**
     * Selects by input JSON shape, with the configured output format serving as
     * an input preference within that shape. Single-shape types avoid peeking,
     * preserving each backend's fused scalar parser.
     */
    public static Object readValueSet(StreamingParser parser, Type type, Class<?> boxed, ValueSet valueSet,
                                      NodeConfig config) throws IOException {
        String preferredFormat = config.valueFormat(boxed);
        JsonType jsonType = valueSet.multiples
                ? parser.peekToken().jsonType()
                : valueSet.defaultInfo.rawJsonType;
        ValueInfo codec = valueSet.selectForRead(jsonType, preferredFormat);
        return readValueCodec(parser, codec, config);
    }

    public static Object readValueCodec(StreamingParser parser, ValueInfo valueInfo,
                                        NodeConfig config) throws IOException {
        if (valueInfo.streamParser != null) {
            if (valueInfo.runtimeClazz != null && valueInfo.runtimeClazz != valueInfo.valueClazz) {
                throw new BindingException("cannot decode value type '" + valueInfo.valueClazz.getName()
                        + "' as runtime subtype '" + valueInfo.runtimeClazz.getName() + "'");
            }
            try {
                return valueInfo.streamParser.read(parser);
            } catch (BindingException | IOException e) {
                throw e;
            } catch (Exception e) {
                throw new BindingException("failed to decode value type '" + valueInfo.valueClazz.getName()
                        + "' with streaming ValueCodec", e);
            }
        }
        Class<?> raw = valueInfo.rawClazz;

        /*
         * Use nullable read APIs directly instead of probing null first.
         *
         * This preserves backend fused read paths for non-null scalar
         * values.
         */

        if (raw == String.class) {
            return valueInfo.rawToValue(parser.readString());
        }

        if (raw == Boolean.class) {
            return valueInfo.rawToValue(parser.readBoolean());
        }

        if (raw == Integer.class) {
            return valueInfo.rawToValue(parser.readInt());
        }

        if (raw == Long.class) {
            return valueInfo.rawToValue(parser.readLong());
        }

        if (raw == Double.class) {
            return valueInfo.rawToValue(parser.readDouble());
        }

        if (raw == Float.class) {
            return valueInfo.rawToValue(parser.readFloat());
        }

        if (raw == Short.class) {
            return valueInfo.rawToValue(parser.readShort());
        }

        if (raw == Byte.class) {
            return valueInfo.rawToValue(parser.readByte());
        }

        if (raw == BigInteger.class) {
            return valueInfo.rawToValue(parser.readBigInteger());
        }

        if (raw == BigDecimal.class) {
            return valueInfo.rawToValue(parser.readBigDecimal());
        }

        if (raw == Number.class) {
            return valueInfo.rawToValue(config.exactNumbers ? parser.readNumberExact() : parser.readNumber());
        }

        if (raw == Map.class) {
            return valueInfo.rawToValue(parser.readRawObject(config));
        }

        if (raw == List.class) {
            return valueInfo.rawToValue(parser.readRawArray(config));
        }

        throw new BindingException("cannot read value with ValueCodec into type '" + raw.getName() + "'");
    }

    /**
     * --------------------------------------------------------------
     * POJO
     * --------------------------------------------------------------
     */

    public static Object readPojo(StreamingParser parser, Type type, Class<?> boxed, PojoInfo pojoInfo,
                                   NodeConfig config) throws IOException {
        // JSON null needs no construction, even for write-only POJO types.
        if (pojoInfo.creatorInfo == null) {
            if (parser.nextIfNull()) return null;
            if (!parser.nextIfObjectStart()) {
                throw new BindingException("cannot read token '" + parser.peekToken()
                        + "' as object type '" + type.getTypeName() + "'");
            }
            pojoInfo.requireCreatorInfo(); // fail before reading any object member
        }

        /*
         * Creator/parent-discriminator POJOs need streaming access to the
         * object members, so consume START_OBJECT here.
         */
        if (pojoInfo.hasParentScopeOneOf || !pojoInfo.creatorInfo.hasNoArgsCreator()) {
            if (!parser.nextIfObjectStart()) {
                if (parser.nextIfNull()) {
                    return null;
                }
                throw new BindingException("cannot read token '" + parser.peekToken() + "' as object type '" +
                        type.getTypeName() + "'");
            }
            return readPojoWithCreator(parser, type, boxed, pojoInfo, config);
        }

        /*
         * Fast non-null path.
         */
        if (!parser.nextIfObjectStart()) {
            if (parser.nextIfNull()) {
                return null;
            }
            throw new BindingException("cannot read token '" + parser.peekToken() + "' as object type '" +
                    type.getTypeName() + "'");
        }

        Object pojo = pojoInfo.creatorInfo.newPojoNoArgs();
        PropertyParser[] propertyParsers = pojoInfo.propertyParsers;
        NameMatcher matcher = cacheNameMatcher(parser, pojoInfo);

        // Both POJO and JOJO use the same cached name matcher and property
        // readers. Only a JOJO with dynamic reads enabled needs unknown keys.
        boolean retainDynamic = pojoInfo.isJojo && pojoInfo.readDynamic;
        LinkedHashMap<String, Object> dynamic = null;

        int expectedIndex = 0;
        int index;
        while ((index = parser.nextNameMatch(matcher, expectedIndex)) != NameMatcher.OBJECT_END) {
            if (index >= 0) {
                propertyParsers[index].parse(parser, pojo, type, boxed, config);
                expectedIndex = index + 1;
            } else if (retainDynamic) {
                String name = parser.currentName();
                // Read-only declared members are not present in the settable
                // matcher. They must not silently become dynamic members.
                if (pojoInfo.propertyLookup.containsKey(name)) {
                    parser.skipNode();
                    continue;
                }
                if (dynamic == null) {
                    JsonObject jojo = (JsonObject) pojo;
                    dynamic = InternalAccess.dynamicMembers(jojo);
                    if (dynamic == null) {
                        dynamic = new LinkedHashMap<>();
                        InternalAccess.dynamicMembers(jojo, dynamic);
                    }
                }
                dynamic.put(name, parser.readRaw(config));
            } else {
                parser.skipNode();
            }
        }

        return pojo;
    }


    /**
     * Reads a POJO with creator arguments or deferred parent-scope resolution.
     * The object start has already been consumed.
     */
    public static Object readPojoWithCreator(StreamingParser parser, Type type, Class<?> boxed, PojoInfo pojoInfo,
                                             NodeConfig config) throws IOException {

        CreatorInfo creator = pojoInfo.requireCreatorInfo();
        CreatorState state = new CreatorState(creator);

        PropertyInfo deferred = null;
        Object deferredRaw = null;
        String parentKey = null;
        Object parentValue = UNSET;

        // The creator matcher covers constructor names, aliases, and all
        // declared properties, including read-only ones. Match before asking
        // the backend to materialize any field name.
        NameMatcher matcher = cacheCreatorNameMatcher(parser, pojoInfo);
        int expectedIndex = 0;
        int index;
        while ((index = parser.nextNameMatch(matcher, expectedIndex)) != NameMatcher.OBJECT_END) {
            if (index == NameMatcher.UNKNOWN) {
                if (pojoInfo.isJojo && pojoInfo.readDynamic) {
                    String name = parser.currentName();
                    Object value = parser.readRaw(config);
                    state.acceptDynamic(name, value);
                    if (parentKey != null && parentKey.equals(name)) {
                        parentValue = value;
                    }
                } else {
                    parser.skipNode();
                }
                continue;
            }

            expectedIndex = index + 1;
            String name = matcher.name(index);

            /*
             * Creator arguments have priority over ordinary properties.
             */
            int argIndex = pojoInfo.creatorMatchArgs[index];
            if (argIndex >= 0) {
                Object value;
                if (creator.argOneOfInfos[argIndex] != null) {
                    value = OneOfIO.readOneOf(parser, creator.argOneOfInfos[argIndex], config);
                } else if (creator.argValueCodecs[argIndex] != null) {
                    value = readValueCodec(parser, creator.argValueCodecs[argIndex], config);
                } else {
                    LazyTypeInfo lazy = creator.argTypeInfos[argIndex];
                    if (lazy != null) {
                        value = readNode(parser, lazy.clazz, Types.box(lazy.clazz), lazy.get(), config);
                    } else {
                        Type argType = creator.argGenericDependent[argIndex]
                                ? Types.resolveMemberType(type, boxed, creator.argTypes[argIndex])
                                : creator.argTypes[argIndex];
                        Class<?> argBoxed = Types.rawBox(argType);
                        value = readNode(parser, argType, argBoxed, null, config);
                    }
                }
                state.acceptCtorArg(argIndex, value);
                if (parentKey != null && parentKey.equals(name)) {
                    parentValue = value;
                }
                continue;
            }

            PropertyInfo property = pojoInfo.creatorMatchProperties[index];
            if (property == null) {
                parser.skipNode();
                continue;
            }

            /*
             * Parent-scope OneOf property.
             */
            OneOfInfo oneOfInfo = property.oneOfInfo;
            if (oneOfInfo != null && oneOfInfo.scope == OneOf.Scope.PARENT) {
                if (!oneOfInfo.discriminatorPath.isEmpty()) {
                    throw new BindingException("oneOf scope=PARENT does not support path discriminator");
                }

                if (parentKey == null) {
                    parentKey = oneOfInfo.discriminatorKey;
                } else if (!parentKey.equals(oneOfInfo.discriminatorKey)) {
                    throw new BindingException("at most one OneOf parent discriminator key is supported per class");
                }

                LazyTypeInfo target = oneOfInfo.lazyTypeInfoByWhen(parentValue == UNSET ? null : parentValue);
                if (target != null) {
                    Object value = readNode(parser, target.clazz, target.clazz, target.get(), config);
                    if (state.isCreated()) {
                        property.invokeSetterIfPresent(state.pojo(), value);
                    } else {
                        state.bufferProperty(property, value);
                    }
                } else if (deferred == null) {
                    deferred = property;
                    deferredRaw = parser.readRaw(config);
                } else {
                    throw new BindingException("at most one OneOf field with scope=PARENT is supported per class");
                }
                continue;
            }

            /*
             * The discriminator property itself must be read even if it has
             * no setter, because its value may still select a deferred OneOf.
             */
            if (parentKey != null && parentKey.equals(name)) {
                Object value = readProperty(parser, property, type, boxed, config);
                parentValue = value;

                if (state.isCreated()) {
                    property.invokeSetterIfPresent(state.pojo(), value);
                } else {
                    state.bufferProperty(property, value);
                }
                continue;
            }

            /*
             * Ordinary property.
             */
            if (!property.settable) {
                parser.skipNode();
                continue;
            }

            if (state.isCreated() && property.parser != null) {
                property.parser.parse(parser, state.pojo(), type, boxed, config);
            } else {
                Object value = readProperty(parser, property, type, boxed, config);
                if (state.isCreated()) {
                    property.invokeSetter(state.pojo(), value);
                } else {
                    state.bufferProperty(property, value);
                }
            }
        }

        Object pojo = state.finish();
        if (deferred == null) {
            return pojo;
        }

        OneOfInfo oneOfInfo = deferred.oneOfInfo;

        /*
         * The discriminator may have been supplied by a constructor/default
         * property value rather than appearing in the input stream.
         */
        if (parentValue == UNSET) {
            PropertyInfo parentProperty = pojoInfo.propertyLookup.get(parentKey);

            if (parentProperty != null && parentProperty.gettable) {
                parentValue = parentProperty.invokeGetter(pojo);
            } else if (pojoInfo.isJojo) {
                parentValue = ((JsonObject) pojo).getNode(parentKey);
            }
        }

        LazyTypeInfo target = oneOfInfo.lazyTypeInfoByWhen(parentValue == UNSET ? null : parentValue);
        Object value;
        if (target != null) {
            value = NodeMapper.convert(deferredRaw, target.clazz, false, config);
        } else if (oneOfInfo.fallbackNull) {
            value = null;
        } else {
            throw new BindingException("oneOf discriminator has no matching mapping: key='" + oneOfInfo.discriminatorKey +
                    "', value='" + (parentValue == UNSET ? null : parentValue) + "'");
        }
        deferred.invokeSetterIfPresent(pojo, value);
        return pojo;
    }


    /**
     * --------------------------------------------------------------
     * Property
     * --------------------------------------------------------------
     */

    public static Object readProperty(StreamingParser parser, PropertyInfo property, Type ownerType, Class<?> ownerBoxed,
                                      NodeConfig config) throws IOException {
        Type type = property.genericDependent
                ? Types.resolveMemberType(ownerType, ownerBoxed, property.type)
                : property.type;

        Class<?> boxed = property.genericDependent
                ? Types.rawBox(type)
                : property.boxed;

        if (property.oneOfInfo != null) {
            return OneOfIO.readOneOf(parser, property.oneOfInfo, config);
        }

        if (property.valueInfo != null) {
            return readValueCodec(parser, property.valueInfo, config);
        }

        TypeInfo declaredInfo = property.typeInfo == null ? null : property.typeInfo.get();
        return readNode(parser, type, boxed, declaredInfo, config);
    }


    /**
     * --------------------------------------------------------------
     * Map
     * --------------------------------------------------------------
     */
    public static Map<String, Object> readMap(StreamingParser parser, Type type, Class<?> boxed,
                                              Type valueType, Class<?> valueBoxed, TypeInfo valueTi,
                                              NodeConfig config) throws IOException {
        // Exact raw container declarations have no generic work to do.
        // Route them before token probes or type metadata initialization.
        if (valueType == null && (type == Map.class || type == LinkedHashMap.class)) {
            return parser.readRawObject(config);
        }

        if (valueType == null) {
            valueType = Types.resolveTypeArgument(type, Map.class, 1);
            valueBoxed = Types.rawBox(valueType);
            valueTi = valueBoxed == Object.class
                    ? TypeInfo.NONE : TypeRegistry.registerTypeInfo(valueBoxed);
        }

        // Raw object parsing owns the entire untyped graph. Do not re-enter
        // readNode() for every member or resolve its generic type per read.
        if ((boxed == Map.class || boxed == LinkedHashMap.class)
                && valueType == Object.class
                && (valueTi == null || valueTi == TypeInfo.NONE)) {
            return parser.readRawObject(config);
        }

        if (!parser.nextIfObjectStart()) {
            if (parser.nextIfNull()) {
                return null;
            }
            throw new BindingException("cannot read token '" + parser.peekToken() + "' as object type '" +
                    type.getTypeName() + "'");
        }

        Map<String, Object> map = boxed == Map.class || boxed == LinkedHashMap.class
                ? new LinkedHashMap<>()
                : TypeRegistry.newMapContainer(boxed, 0, false);
        String name;
        while ((name = parser.nextName()) != null) {
            map.put(name, readNode(parser, valueType, valueBoxed, valueTi, config));
        }
        return map;
    }


    /**
     * --------------------------------------------------------------
     * List
     * --------------------------------------------------------------
     */

    public static List<Object> readList(StreamingParser parser, Type type, Class<?> boxed,
                                        Type elementType, Class<?> elementBoxed, TypeInfo elementTi,
                                        NodeConfig config) throws IOException {
        // Exact raw List targets have no generic metadata to resolve.
        if (elementType == null && (type == List.class || type == ArrayList.class)) {
            return parser.readRawArray(config);
        }

        if (elementType == null) {
            elementType = Types.resolveTypeArgument(type, List.class, 0);
            elementBoxed = Types.rawBox(elementType);
            elementTi = elementBoxed == Object.class
                    ? TypeInfo.NONE : TypeRegistry.registerTypeInfo(elementBoxed);
        }

        // Preserve concrete list implementations and typed element bindings.
        if ((boxed == List.class || boxed == ArrayList.class)
                && elementType == Object.class
                && (elementTi == null || elementTi == TypeInfo.NONE)) {
            return parser.readRawArray(config);
        }

        if (!parser.nextIfArrayStart()) {
            if (parser.nextIfNull()) {
                return null;
            }
            throw new BindingException("cannot read token '" + parser.peekToken() + "' as array type '" +
                    type.getTypeName() + "'");
        }

        List<Object> list = boxed == List.class || boxed == ArrayList.class
                ? new ArrayList<>()
                : TypeRegistry.newListContainer(boxed, 0, false);
        while (!parser.nextIfArrayEnd()) {
            list.add(readNode(parser, elementType, elementBoxed, elementTi, config));
        }
        return list;
    }

    /**
     * --------------------------------------------------------------
     * Set
     * --------------------------------------------------------------
     */

    public static Set<Object> readSet(StreamingParser parser, Type type, Class<?> boxed,
                                      Type elementType, Class<?> elementBoxed, TypeInfo elementTi,
                                      NodeConfig config) throws IOException {
        if (!parser.nextIfArrayStart()) {
            if (parser.nextIfNull()) {
                return null;
            }
            throw new BindingException("cannot read token '" + parser.peekToken() + "' as array type '" +
                    type.getTypeName() + "'");
        }

        if (elementType == null) {
            elementType = Types.resolveTypeArgument(type, Set.class, 0);
            elementBoxed = Types.rawBox(elementType);
            elementTi = TypeRegistry.registerTypeInfo(elementBoxed);
        }

        Set<Object> set = boxed == Set.class || boxed == LinkedHashSet.class
                ? new LinkedHashSet<>()
                : TypeRegistry.newSetContainer(boxed, 0, false);
        while (!parser.nextIfArrayEnd()) {
            Object value = readNode(parser, elementType, elementBoxed, elementTi, config);
            set.add(value);
        }
        return set;
    }

    /**
     * --------------------------------------------------------------
     * Java Array
     * --------------------------------------------------------------
     */

    public static Object readArray(StreamingParser parser, Type type, Class<?> boxed,
                                      Class<?> componentClazz, Class<?> componentBoxed, TypeInfo componentTi,
                                      NodeConfig config) throws IOException {
        if (!parser.nextIfArrayStart()) {
            if (parser.nextIfNull()) {
                return null;
            }
            throw new BindingException("cannot read token '" + parser.peekToken() + "' as array type '" +
                    type.getTypeName() + "'");
        }

        if (componentClazz == null) {
            componentClazz = boxed.getComponentType();
            componentBoxed = Types.box(componentClazz);
            componentTi = TypeRegistry.registerTypeInfo(componentClazz);
        }

        if (componentClazz == int.class) {
            return _readIntArray(parser);
        } else if (componentClazz == long.class) {
            return _readLongArray(parser);
        } else if (componentClazz == double.class) {
            return _readDoubleArray(parser);
        } else if (componentClazz == float.class) {
            return _readFloatArray(parser);
        } else if (componentClazz == boolean.class) {
            return _readBooleanArray(parser);
        } else if (componentClazz == short.class) {
            return _readShortArray(parser);
        } else if (componentClazz == byte.class) {
            return _readByteArray(parser);
        } else if (componentClazz == char.class) {
            return _readCharArray(parser);
        }

        Object arr = null;
        int size = 0;
        while (!parser.nextIfArrayEnd()) {
            if (arr == null) {
                arr = Array.newInstance(componentClazz, 8);
            } else if (size == Array.getLength(arr)) {
                Object expanded = Array.newInstance(componentClazz, size << 1);
                System.arraycopy(arr, 0, expanded, 0, size);
                arr = expanded;
            }

            Object value = readNode(parser, componentClazz, componentBoxed, componentTi, config);
            Array.set(arr, size++, value);
        }

        if (arr == null) {
            return Array.newInstance(componentClazz, 0);
        }

        if (size == Array.getLength(arr)) {
            return arr;
        }

        Object exact = Array.newInstance(componentClazz, size);
        System.arraycopy(arr, 0, exact, 0, size);
        return exact;
    }

    private static boolean[] _readBooleanArray(StreamingParser parser) throws IOException {
        boolean[] array = new boolean[8];
        int size = 0;
        while (!parser.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = parser.readBooleanValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    private static int[] _readIntArray(StreamingParser parser) throws IOException {
        int[] array = new int[8];
        int size = 0;
        while (!parser.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = parser.readIntValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    private static long[] _readLongArray(StreamingParser parser) throws IOException {
        long[] array = new long[8];
        int size = 0;
        while (!parser.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = parser.readLongValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    private static double[] _readDoubleArray(StreamingParser parser) throws IOException {
        double[] array = new double[8];
        int size = 0;
        while (!parser.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = parser.readDoubleValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    private static float[] _readFloatArray(StreamingParser parser) throws IOException {
        float[] array = new float[8];
        int size = 0;
        while (!parser.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = parser.readFloatValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    private static short[] _readShortArray(StreamingParser parser) throws IOException {
        short[] array = new short[8];
        int size = 0;
        while (!parser.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = parser.readShortValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    private static byte[] _readByteArray(StreamingParser parser) throws IOException {
        byte[] array = new byte[8];
        int size = 0;
        while (!parser.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = parser.readByteValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    private static char[] _readCharArray(StreamingParser parser) throws IOException {
        char[] array = new char[8];
        int size = 0;
        while (!parser.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = parser.readCharValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    /**
     * --------------------------------------------------------------
     * JsonArray subclasses
     * --------------------------------------------------------------
     */

    public static JsonArray readJsonArray(StreamingParser parser, Type type, Class<?> boxed, TypeInfo ti,
                                          NodeConfig config) throws IOException {
        if (!parser.nextIfArrayStart()) {
            if (parser.nextIfNull()) {
                return null;
            }
            throw new BindingException("cannot read token '" + parser.peekToken() + "' as array type '" +
                    type.getTypeName() + "'");
        }
        JsonArray array = (JsonArray) ti.pojoInfo.requireCreatorInfo().forceNewPojo();
        Class<?> element = array.elementClass();
        TypeInfo elementInfo = TypeRegistry.registerTypeInfo(element);
        while (!parser.nextIfArrayEnd()) {
            Object value = readNode(parser, element, element, elementInfo, config);
            array.add(value);
        }
        return array;
    }


    /** Adopts the owned raw LinkedHashMap without copying its members. */
    public static JsonObject readJsonObject(StreamingParser parser) throws IOException {
        return readJsonObject(parser, NodeConfig.DEFAULT);
    }

    /** Adopts a raw container read with the supplied numeric policy. */
    public static JsonObject readJsonObject(StreamingParser parser, NodeConfig config) throws IOException {
        Map<String, Object> raw = parser.readRawObject(config);
        if (raw == null) return null;
        JsonObject result = new JsonObject();
        InternalAccess.dynamicMembers(result, (LinkedHashMap<String, Object>) raw);
        return result;
    }

    /** Adopts the owned raw ArrayList without copying its elements. */
    public static JsonArray readJsonArray(StreamingParser parser) throws IOException {
        return readJsonArray(parser, NodeConfig.DEFAULT);
    }

    /** Adopts a raw container read with the supplied numeric policy. */
    public static JsonArray readJsonArray(StreamingParser parser, NodeConfig config) throws IOException {
        List<Object> raw = parser.readRawArray(config);
        if (raw == null) return null;
        JsonArray result = new JsonArray();
        InternalAccess.dynamicElements(result, (ArrayList<Object>) raw);
        return result;
    }

    /**
     * --------------------------------------------------------------
     * Raw node validation
     * --------------------------------------------------------------
     */

    @SuppressWarnings("unchecked")
    public static Map<String, Object> castMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        throw new BindingException("cannot read non-object value as JsonObject");
    }

    @SuppressWarnings("unchecked")
    public static List<Object> castList(Object value) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        throw new BindingException("cannot read non-array value as JsonArray");
    }

    public static NameMatcher cacheNameMatcher(StreamingParser parser, PojoInfo pojoInfo) {
        BackendCache cache = pojoInfo.backendCache(parser.backend);
        NameMatcher matcher = cache.nameMatcher;
        if (matcher == null) {
            matcher = parser.createNameMatcher(pojoInfo.settableProperties);
            cache.nameMatcher = matcher;
        }
        return matcher;
    }

    static NameMatcher cacheCreatorNameMatcher(StreamingParser parser, PojoInfo pojoInfo) {
        BackendCache cache = pojoInfo.backendCache(parser.backend);
        NameMatcher matcher = cache.creatorNameMatcher;
        if (matcher == null) {
            matcher = parser.createNameMatcher(pojoInfo.creatorMatchNames);
            cache.creatorNameMatcher = matcher;
        }
        return matcher;
    }


    /* Write */

    /**
     * Writes one node value to a streaming generator. The caller controls the
     * document boundaries, flushing, and generator lifecycle.
     */
    public static void writeNode(StreamingGenerator generator, Object node,
                                 NodeConfig config) throws IOException {
        Asserts.notNull(generator, "generator");
        Asserts.notNull(config, "config");

        try {
            _writeNode(generator, node, TypeInfo.NONE, config);
        } catch (BindingException | IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new BindingException("failed to write node of type '" + Types.name(node) + "'", e);
        }
    }


    private static void _writeNode(StreamingGenerator generator, Object node, TypeInfo typeInfo,
                                   NodeConfig config) throws IOException {
        if (node == null) {
            generator.writeNull();
            return;
        }

        Class<?> clazz = node.getClass();
        if (typeInfo.clazz == clazz) {
            if (typeInfo.pojoInfo != null && !typeInfo.pojoInfo.isJajo) {
                writePojo(generator, node, typeInfo.pojoInfo, config);
                return;
            }

            if (typeInfo.valueSet != null) {
                ValueInfo vi = typeInfo.requireValueInfo(config.valueFormat(clazz));
                if (vi.streamGenerator != null) {
                    vi.streamGenerator.write(generator, node);
                } else {
                    _writeNode(generator, vi.valueToRaw(node), TypeInfo.NONE, config);
                }
                return;
            }
        }

        if (clazz == String.class) {
            generator.writeStringValue((String) node);
            return;
        }
        if (clazz == Boolean.class) {
            generator.writeBooleanValue((Boolean) node);
            return;
        }
        if (clazz == Integer.class) {
            generator.writeIntValue((Integer) node);
            return;
        }
        if (clazz == Long.class) {
            generator.writeLongValue((Long) node);
            return;
        }
        if (clazz == Double.class) {
            generator.writeDoubleValue((Double) node);
            return;
        }
        if (clazz == Float.class) {
            generator.writeFloatValue((Float) node);
            return;
        }
        if (clazz == Short.class) {
            generator.writeShortValue((Short) node);
            return;
        }
        if (clazz == Byte.class) {
            generator.writeByteValue((Byte) node);
            return;
        }
        if (clazz == BigInteger.class) {
            generator.writeBigIntegerValue((BigInteger) node);
            return;
        }
        if (clazz == BigDecimal.class) {
            generator.writeBigDecimalValue((BigDecimal) node);
            return;
        }
        if (node instanceof Number) {
            generator.writeNumberValue((Number) node);
            return;
        }

        if (clazz == Character.class) {
            generator.writeCharValue((Character) node);
            return;
        }

        if (node instanceof Enum) {
            generator.writeStringValue(((Enum<?>) node).name());
            return;
        }


        /*
         * --------------------------------------------------------------
         * Built-in object / array nodes
         * --------------------------------------------------------------
         */

        if (node instanceof Map) {
            writeMap(generator, (Map<?, ?>) node, TypeInfo.NONE, config);
            return;
        }

        if (node instanceof List) {
            writeList(generator, (List<?>) node, TypeInfo.NONE, config);
            return;
        }

        /*
         * Exact JsonObject only.
         *
         * A JsonObject subclass is a JOJO and must go through PojoInfo so
         * declared properties are written before dynamic members.
         */
        if (clazz == JsonObject.class) {
            writeJsonObject(generator, (JsonObject) node, config);
            return;
        }

        if (node instanceof JsonArray) {
            writeJsonArray(generator, (JsonArray) node, config);
            return;
        }

        if (node instanceof Set) {
            writeSet(generator, (Set<?>) node, TypeInfo.NONE, config);
            return;
        }

        if (clazz.isArray()) {
            writeArray(generator, node, clazz, TypeInfo.NONE, config);
            return;
        }


        /*
         * --------------------------------------------------------------
         * Registered nodes
         * --------------------------------------------------------------
         */

        TypeInfo ti = typeInfo.clazz == clazz
                ? typeInfo : TypeRegistry.registerTypeInfo(clazz);

        /*
         * NodeValue / ValueCodec.
         */
        if (ti.valueSet != null) {
            ValueInfo vi = ti.requireValueInfo(config.valueFormat(clazz));
            if (vi.streamGenerator != null) {
                vi.streamGenerator.write(generator, node);
            } else {
                _writeNode(generator, vi.valueToRaw(node), TypeInfo.NONE, config);
            }
            return;
        }

        /*
         * Stream non-collection external representations directly.
         * External object members apply the backend's JSON null semantics.
         */
        if (ti.externalNode != null) {
            _writeExternalNode(generator, node, ti.externalNode, config);
            return;
        }

        /*
         * POJO / JOJO.
         */
        if (ti.pojoInfo != null && !ti.pojoInfo.isJajo) {
            writePojo(generator, node, ti.pojoInfo, config);
            return;
        }

        throw new BindingException("unsupported node type '" + Types.name(node) + "'");
    }


    /**
     * Streams an external JSON tree without creating intermediate raw nodes.
     * Object members with JSON null are omitted when includeNulls is false;
     * null array elements are preserved.
     */
    private static void _writeExternalNode(StreamingGenerator generator, Object node,
                                           ExternalNode<Object> external,
                                           NodeConfig config) throws IOException {
        switch (external.jsonType(node)) {
            case NULL:
                generator.writeNull();
                return;
            case STRING:
                generator.writeStringValue(external.toString(node));
                return;
            case NUMBER:
                generator.writeNumberValue(external.toNumber(node));
                return;
            case BOOLEAN:
                generator.writeBooleanValue(external.toBoolean(node));
                return;
            case OBJECT:
                generator.writeObjectStart();
                int count = 0;
                for (Map.Entry<String, Object> entry : external.entrySetInObject(node)) {
                    Object value = entry.getValue();
                    if (!config.includeNulls && (value == null || external.jsonType(value) == JsonType.NULL)) {
                        continue;
                    }
                    generator.writeName(entry.getKey(), count > 0);
                    count++;
                    _writeNode(generator, value, TypeInfo.NONE, config);
                }
                generator.writeObjectEnd();
                return;
            case ARRAY:
                generator.writeArrayStart();
                int size = external.sizeInArray(node);
                for (int i = 0; i < size; i++) {
                    if (i > 0) {
                        generator.writeArraySeparator();
                    }
                    _writeNode(generator, external.getInArray(node, i), TypeInfo.NONE, config);
                }
                generator.writeArrayEnd();
                return;
            default:
                throw new BindingException("unsupported external node type '" + Types.name(node) + "'");
        }
    }


    /*
     * --------------------------------------------------------------
     * Object nodes
     * --------------------------------------------------------------
     */

    public static void writeMap(StreamingGenerator generator, Map<?, ?> map, TypeInfo valueTi,
                                NodeConfig config) throws IOException {
        generator.writeObjectStart();
        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value == null && !config.includeNulls) {
                continue;
            }
            generator.writeName(entry.getKey().toString(), count > 0);
            count++;
            _writeNode(generator, value, valueTi, config);
        }
        generator.writeObjectEnd();
    }


    public static void writeJsonObject(StreamingGenerator generator, JsonObject object,
                                       NodeConfig config) throws IOException {
        generator.writeObjectStart();
        int count = 0;
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            Object value = entry.getValue();
            if (value == null && !config.includeNulls) {
                continue;
            }
            generator.writeName(entry.getKey(), count > 0);
            count++;
            _writeNode(generator, value, TypeInfo.NONE, config);
        }
        generator.writeObjectEnd();
    }


    /*
     * --------------------------------------------------------------
     * Array nodes
     * --------------------------------------------------------------
     */

    public static void writeList(StreamingGenerator generator, List<?> list, TypeInfo elementTi,
                                 NodeConfig config) throws IOException {
        generator.writeArrayStart();
        if (list instanceof RandomAccess) {
            for (int i = 0, size = list.size(); i < size; i++) {
                if (i > 0) {
                    generator.writeArraySeparator();
                }
                _writeNode(generator, list.get(i), elementTi, config);
            }
        } else {
            boolean separated = false;
            for (Object value : list) {
                if (separated) {
                    generator.writeArraySeparator();
                } else {
                    separated = true;
                }
                _writeNode(generator, value, elementTi, config);
            }
        }
        generator.writeArrayEnd();
    }


    public static void writeJsonArray(StreamingGenerator generator, JsonArray array,
                                      NodeConfig config) throws IOException {
        generator.writeArrayStart();
        for (int i = 0, size = array.size(); i < size; i++) {
            if (i > 0) {
                generator.writeArraySeparator();
            }
            _writeNode(generator, array.getNode(i), TypeInfo.NONE, config);
        }
        generator.writeArrayEnd();
    }


    public static void writeSet(StreamingGenerator generator, Set<?> set, TypeInfo elementTi,
                                NodeConfig config) throws IOException {
        generator.writeArrayStart();
        boolean separated = false;
        for (Object value : set) {
            if (separated) {
                generator.writeArraySeparator();
            } else {
                separated = true;
            }
            _writeNode(generator, value, elementTi, config);
        }
        generator.writeArrayEnd();
    }


    /*
     * --------------------------------------------------------------
     * Java arrays
     * --------------------------------------------------------------
     */

    public static void writeArray(StreamingGenerator generator, Object node, Class<?> clazz, TypeInfo compTi,
                                  NodeConfig config) throws IOException {
        generator.writeArrayStart();
        if (clazz == boolean[].class) {
            boolean[] array = (boolean[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) generator.writeArraySeparator();
                generator.writeBooleanValue(array[i]);
            }
        } else if (clazz == byte[].class) {
            byte[] array = (byte[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) generator.writeArraySeparator();
                generator.writeByteValue(array[i]);
            }
        } else if (clazz == short[].class) {
            short[] array = (short[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) generator.writeArraySeparator();
                generator.writeShortValue(array[i]);
            }
        } else if (clazz == int[].class) {
            int[] array = (int[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) generator.writeArraySeparator();
                generator.writeIntValue(array[i]);
            }
        } else if (clazz == long[].class) {
            long[] array = (long[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) generator.writeArraySeparator();
                generator.writeLongValue(array[i]);
            }
        } else if (clazz == float[].class) {
            float[] array = (float[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) generator.writeArraySeparator();
                generator.writeFloatValue(array[i]);
            }
        } else if (clazz == double[].class) {
            double[] array = (double[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) generator.writeArraySeparator();
                generator.writeDoubleValue(array[i]);
            }
        } else if (clazz == char[].class) {
            char[] array = (char[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) generator.writeArraySeparator();
                generator.writeCharValue(array[i]);
            }
        } else {
            if (compTi.isNone()) {
                compTi = TypeRegistry.registerTypeInfo(clazz.getComponentType());
            }
            Object[] array = (Object[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) generator.writeArraySeparator();
                _writeNode(generator, array[i], compTi, config);
            }
        }
        generator.writeArrayEnd();
    }


    /*
     * --------------------------------------------------------------
     * POJO / JOJO
     * --------------------------------------------------------------
     */

    public static void writePojo(StreamingGenerator generator, Object node, PojoInfo pojoInfo,
                                 NodeConfig config) throws IOException {
        generator.writeObjectStart();
        PropertyGenerator[] propertyGenerators = pojoInfo.propertyGenerators;
        CompiledName[] compiledNames = generator.compiledNames(pojoInfo);
        int count = 0;
        for (int i = 0, len = propertyGenerators.length; i < len; i++) {
            count = propertyGenerators[i].generate(generator, compiledNames[i], node, config, count);
        }

        /*
         * JOJO dynamic members come after declared properties.
         */
        if (pojoInfo.isJojo && pojoInfo.writeDynamic) {
            Map<String, Object> dynamic = InternalAccess.dynamicMembers((JsonObject) node);
            if (dynamic != null) {
                for (Map.Entry<String, Object> entry : dynamic.entrySet()) {
                    Object value = entry.getValue();
                    if (value == null && !config.includeNulls) {
                        continue;
                    }

                    generator.writeName(entry.getKey(), count > 0);
                    count++;
                    _writeNode(generator, value, TypeInfo.NONE, config);
                }
            }
        }
        generator.writeObjectEnd();
    }



}
