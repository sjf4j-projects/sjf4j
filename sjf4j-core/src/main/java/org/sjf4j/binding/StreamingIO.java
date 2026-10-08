package org.sjf4j.binding;

import org.sjf4j.InternalAccess;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.mapping.NodeMapper;
import org.sjf4j.node.PropertyInfo;
import org.sjf4j.node.OneOfInfo;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.CreatorInfo;
import org.sjf4j.node.CreatorState;
import org.sjf4j.node.TypeInfo;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.node.Types;
import org.sjf4j.util.Asserts;
import org.sjf4j.value.ValueInfo;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Set;

/** Target-directed V4 streaming binding helpers. */
public final class StreamingIO {

    private static final Object UNSET = new Object();

    private StreamingIO() {
    }


    /**
     * --------------------------------------------------------------
     * Read
     * --------------------------------------------------------------
     */

    public static Object readNode(StreamingReader reader, Type type,
                                  RuntimeContext context) throws IOException {
        Asserts.notNull(reader, "reader");
        Asserts.notNull(type, "type");
        Asserts.notNull(context, "context");

        Class<?> boxed = Types.rawBox(type);
        TypeInfo ti = TypeRegistry.registerTypeInfo(boxed);
        return readNode(reader, type, boxed, ti, context);
    }


    static Object readNode(StreamingReader reader, Type type, Class<?> boxed, TypeInfo typeInfo,
                           RuntimeContext context) throws IOException {
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

            if (boxed == Object.class) {
                return reader.readRawNode();
            }

            // Polymorphic resolution is uncommon and needs the complete source shape.
            if (typeInfo.oneOfInfo != null) {
                return readOneOf(reader, typeInfo.oneOfInfo, context);
            }

            if (typeInfo.valueInfos != null) {
                return readValueCodec(reader, typeInfo.requireValueInfo(context.defaultValueFormat(boxed)), context);
            }

            /*
             * Scalars.
             *
             * Primitive targets use the non-null Value variant directly.
             * Boxed/reference targets use the nullable variant.
             */

            if (boxed == String.class) {
                return reader.readString();
            }

            if (boxed == Character.class) {
                return reader.readChar();
            }

            if (boxed == Boolean.class) {
                return reader.readBoolean();
            }

            if (boxed == Integer.class) {
                return reader.readInt();
            }

            if (boxed == Long.class) {
                return reader.readLong();
            }

            if (boxed == Short.class) {
                return reader.readShort();
            }

            if (boxed == Byte.class) {
                return reader.readByte();
            }

            if (boxed == Float.class) {
                return reader.readFloat();
            }

            if (boxed == Double.class) {
                return reader.readDouble();
            }

            if (boxed == Number.class) {
                return reader.readNumber();
            }

            if (boxed == BigInteger.class) {
                return reader.readBigInteger();
            }

            if (boxed == BigDecimal.class) {
                return reader.readBigDecimal();
            }

            if (boxed.isEnum()) {
                return readEnum(reader, boxed, context);
            }

            /*
             * Object nodes.
             */

            if (Map.class.isAssignableFrom(boxed)) {
                return readMap(reader, type, boxed, null, null, null, context);
            }

            if (boxed == JsonObject.class) {
                Object raw = reader.readRawNode();
                if (raw == null) return null;
                return new JsonObject(castMap(raw));
            }

            /*
             * Array nodes.
             */

            if (List.class.isAssignableFrom(boxed)) {
                return readList(reader, type, boxed, null, null, null, context);
            }

            if (boxed == JsonArray.class) {
                Object raw = reader.readRawNode();
                if (raw == null) return null;
                return new JsonArray(castList(raw));
            }

            if (JsonArray.class.isAssignableFrom(boxed)) {
                return readJsonArray(reader, type, boxed, typeInfo, context);
            }

            if (Set.class.isAssignableFrom(boxed)) {
                return readSet(reader, type, boxed, null, null, null, context);
            }

            if (boxed.isArray()) {
                return readArray(reader, type, boxed, null, null, null, context);
            }

            /*
             * POJO nodes.
             */

            PojoInfo pojoInfo = typeInfo.pojoInfo;
            if (pojoInfo != null && !pojoInfo.isJajo) {
                return readPojo(reader, type, boxed, pojoInfo, context);
            }

            /*
             * Preserve null semantics even for otherwise unsupported
             * reference targets.
             */
            if (reader.nextIfNull()) {
                return null;
            }

            if (!boxed.isInterface() && Modifier.isAbstract(boxed.getModifiers())) {
                throw new BindingException("cannot read object value into abstract type '" + boxed.getName() + "'");
            }

            throw new BindingException("cannot read value into type '" + boxed.getName() + "'");
        } catch (BindingException e) {
            throw e;
        } catch (Throwable e) {
            throw new BindingException("failed to read streaming into '" + type + "'", e);
        }
    }


    private static Object readOneOf(StreamingReader reader, OneOfInfo oneOfInfo,
                                    RuntimeContext context) throws IOException {

        /*
         * readRawNode() is already nullable, so probing null beforehand
         * would only create unnecessary lookahead state.
         */
        Object raw = reader.readRawNode();
        if (raw == null) {
            return null;
        }

        Class<?> target;
        if (!oneOfInfo.hasDiscriminator) {
            target = oneOfInfo.matchByJsonType(JsonType.of(raw));
        } else {
            if (oneOfInfo.scope != OneOf.Scope.CURRENT) {
                throw new BindingException("oneOf discriminator scope must be CURRENT here, but was " + oneOfInfo.scope);
            }
            if (!JsonType.of(raw).isObject()) {
                if (oneOfInfo.fallbackNull) {
                    return null;
                }
                throw new BindingException("node must be an object, when OneOf has a CURRENT discriminator");
            }
            Object discriminator = oneOfInfo.keyDiscriminator ? Nodes.getInObject(raw, oneOfInfo.key)
                    : oneOfInfo.compiledPath.getNode(raw);
            target = oneOfInfo.matchByWhen(discriminator);
        }

        if (target != null) {
            return NodeMapper.convert(raw, target, false, context);
        }

        if (oneOfInfo.fallbackNull) {
            return null;
        }

        throw new BindingException("oneOf mapping has no matching target for type '" + oneOfInfo.clazz.getName() + "'");
    }


    /**
     * --------------------------------------------------------------
     * Enum
     * --------------------------------------------------------------
     */

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object readEnum(StreamingReader reader, Class<?> enumType,
                                  RuntimeContext context) throws IOException {

        StreamingReader.Token token = reader.peekToken();
        if (token == StreamingReader.Token.NULL) {
            reader.nextIfNull();
            return null;
        }

        if (token == StreamingReader.Token.STRING) {
            return Enum.valueOf((Class<? extends Enum>) enumType, reader.readString());
        }

        if (token == StreamingReader.Token.NUMBER) {
            int ordinal = reader.readIntValue();
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

    public static Object readValueCodec(StreamingReader reader, ValueInfo valueInfo,
                                        RuntimeContext context) throws IOException {
        Class<?> raw = valueInfo.rawClazz;

        /*
         * Use nullable read APIs directly instead of probing null first.
         *
         * This preserves backend fused read paths for non-null scalar
         * values.
         */

        if (raw == String.class) {
            return valueInfo.rawToValue(reader.readString());
        }

        if (raw == Boolean.class) {
            return valueInfo.rawToValue(reader.readBoolean());
        }

        if (raw == Integer.class) {
            return valueInfo.rawToValue(reader.readInt());
        }

        if (raw == Long.class) {
            return valueInfo.rawToValue(reader.readLong());
        }

        if (raw == Double.class) {
            return valueInfo.rawToValue(reader.readDouble());
        }

        if (raw == Float.class) {
            return valueInfo.rawToValue(reader.readFloat());
        }

        if (raw == Short.class) {
            return valueInfo.rawToValue(reader.readShort());
        }

        if (raw == Byte.class) {
            return valueInfo.rawToValue(reader.readByte());
        }

        if (raw == BigInteger.class) {
            return valueInfo.rawToValue(reader.readBigInteger());
        }

        if (raw == BigDecimal.class) {
            return valueInfo.rawToValue(reader.readBigDecimal());
        }

        if (raw == Number.class) {
            return valueInfo.rawToValue(reader.readNumber());
        }

        if (raw == Map.class) {
            Object value = reader.readRawNode();
            if (value == null) {
                return valueInfo.rawToValue(null);
            }
            return valueInfo.rawToValue(castMap(value));
        }

        if (raw == List.class) {
            Object value = reader.readRawNode();
            if (value == null) {
                return valueInfo.rawToValue(null);
            }
            return valueInfo.rawToValue(castList(value));
        }

        throw new BindingException("cannot read value with ValueCodec into type '" + raw.getName() + "'");
    }

    /**
     * --------------------------------------------------------------
     * POJO
     * --------------------------------------------------------------
     */

    public static Object readPojo(StreamingReader reader, Type type, Class<?> boxed, PojoInfo pojoInfo,
                                   RuntimeContext context) throws IOException {
        /*
         * Creator/parent-discriminator POJOs need streaming access to the
         * object members, so consume START_OBJECT here.
         */
        if (pojoInfo.hasParentScopeOneOf || !pojoInfo.creatorInfo.hasNoArgsCreator()) {
            if (!reader.nextIfObjectStart()) {
                if (reader.nextIfNull()) {
                    return null;
                }
                throw new BindingException("cannot read token '" + reader.peekToken() + "' as object type '" +
                        type.getTypeName() + "'");
            }
            return readParentOneOfPojo(reader, type, boxed, pojoInfo, context);
        }

        /*
         * Dynamic objects remain a raw fallback because property names
         * themselves are data.
         *
         * Do not pre-consume START_OBJECT here: readRawNode() owns the
         * complete node.
         */
        if (pojoInfo.isJojo) {
            Object raw = reader.readRawNode();
            if (raw == null) {
                return null;
            }
            return NodeMapper.convert(raw, type, false, context);
        }

        /*
         * Fast non-null path.
         */
        if (!reader.nextIfObjectStart()) {
            if (reader.nextIfNull()) {
                return null;
            }
            throw new BindingException("cannot read token '" + reader.peekToken() + "' as object type '" +
                    type.getTypeName() + "'");
        }

        Object pojo = pojoInfo.creatorInfo.newPojoNoArgs();
        PropertyReader[] propertyReaders = pojoInfo.propertyReaders;
        NameMatcher matcher = reader.nameMatcher(pojoInfo);

        int expectedIndex = 0;
        int index;
        while ((index = reader.nextNameMatch(matcher, expectedIndex)) != NameMatcher.OBJECT_END) {
            if (index >= 0) {
                propertyReaders[index].read(reader, pojo, type, boxed, context);
                expectedIndex = index + 1;
            } else {
                reader.skipNode();
            }
        }

        return pojo;
    }


    /**
     * Reads a POJO whose object start has already been consumed.
     */
    public static Object readParentOneOfPojo(StreamingReader reader, Type type, Class<?> boxed, PojoInfo pojoInfo,
                                             RuntimeContext context) throws IOException {

        CreatorInfo creator = pojoInfo.creatorInfo;
        CreatorState state = new CreatorState(creator);

        PropertyInfo deferred = null;
        Object deferredRaw = null;
        String parentKey = null;
        Object parentValue = UNSET;

        String name;
        while ((name = reader.nextName()) != null) {

            /*
             * Creator arguments have priority over ordinary properties.
             */
            int argIndex = creator.getArgIndexOrAlias(name);
            if (argIndex >= 0) {
                Type argType = Types.resolveMemberType(type, boxed, creator.argTypes[argIndex]);
                Class<?> argBoxed = Types.rawBox(argType);
                ValueInfo codec = creator.argValueCodecs[argIndex];

                Object value = codec == null
                        ? readNode(reader, argType, argBoxed, TypeRegistry.registerTypeInfo(argBoxed), context)
                        : readValueCodec(reader, codec, context);
                state.acceptCtorArg(argIndex, value);
                if (parentKey != null && parentKey.equals(name)) {
                    parentValue = value;
                }
                continue;
            }

            /*
             * Unknown property.
             */
            PropertyInfo property = pojoInfo.propertyLookup.get(name);
            if (property == null) {
                if (pojoInfo.isJojo && pojoInfo.readDynamic) {
                    Object value = reader.readRawNode();
                    state.acceptDynamic(name, value);
                    if (parentKey != null && parentKey.equals(name)) {
                        parentValue = value;
                    }
                    continue;
                }
                reader.skipNode();
                continue;
            }

            /*
             * Parent-scope OneOf property.
             */
            OneOfInfo oneOfInfo = property.oneOfInfo;
            if (oneOfInfo != null && oneOfInfo.scope == OneOf.Scope.PARENT) {
                if (!oneOfInfo.path.isEmpty()) {
                    throw new BindingException("oneOf scope=PARENT does not support path discriminator");
                }

                if (parentKey == null) {
                    parentKey = oneOfInfo.key;
                } else if (!parentKey.equals(oneOfInfo.key)) {
                    throw new BindingException("at most one OneOf parent discriminator key is supported per class");
                }

                Class<?> target = oneOfInfo.matchByWhen(parentValue == UNSET ? null : parentValue);
                if (target != null) {
                    Object value = readNode(reader, target, context);
                    if (state.isCreated()) {
                        property.invokeSetterIfPresent(state.pojo(), value);
                    } else {
                        state.bufferProperty(property, value);
                    }
                } else if (deferred == null) {
                    deferred = property;
                    deferredRaw = reader.readRawNode();
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
                Object value = readProperty(reader, property, type, boxed, context);
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
            if (!property.writable) {
                reader.skipNode();
                continue;
            }

            Object value = readProperty(reader, property, type, boxed, context);
            if (state.isCreated()) {
                property.invokeSetter(state.pojo(), value);
            } else {
                state.bufferProperty(property, value);
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

            if (parentProperty != null && parentProperty.readable) {
                parentValue = parentProperty.invokeGetter(pojo);
            } else if (pojoInfo.isJojo) {
                parentValue = ((JsonObject) pojo).getNode(parentKey);
            }
        }

        Class<?> target = oneOfInfo.matchByWhen(parentValue == UNSET ? null : parentValue);
        Object value;
        if (target != null) {
            value = NodeMapper.convert(deferredRaw, target, false, context);
        } else if (oneOfInfo.fallbackNull) {
            value = null;
        } else {
            throw new BindingException("oneOf discriminator has no matching mapping: key='" + oneOfInfo.key +
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

    public static Object readProperty(StreamingReader reader, PropertyInfo property, Type ownerType, Class<?> ownerBoxed,
                                      RuntimeContext context) throws IOException {
        Type type = property.genericDependent
                ? Types.resolveMemberType(ownerType, ownerBoxed, property.type)
                : property.type;

        Class<?> boxed = property.genericDependent
                ? Types.rawBox(type)
                : property.boxed;

        if (property.oneOfInfo != null) {
            return readOneOf(reader, property.oneOfInfo, context);
        }

        if (property.valueInfo != null) {
            return readValueCodec(reader, property.valueInfo, context);
        }

        return readNode(reader, type, boxed, TypeRegistry.registerTypeInfo(boxed), context);
    }


    /**
     * --------------------------------------------------------------
     * Map
     * --------------------------------------------------------------
     */
    public static Map<String, Object> readMap(StreamingReader reader, Type type, Class<?> boxed,
                                              Type valueType, Class<?> valueBoxed, TypeInfo valueTi,
                                              RuntimeContext context) throws IOException {
        if (!reader.nextIfObjectStart()) {
            if (reader.nextIfNull()) {
                return null;
            }
            throw new BindingException("cannot read token '" + reader.peekToken() + "' as object type '" +
                    type.getTypeName() + "'");
        }

        if (valueType == null) {
            valueType = Types.resolveTypeArgument(type, Map.class, 1);
            valueBoxed = Types.rawBox(valueType);
            valueTi = TypeRegistry.registerTypeInfo(valueBoxed);
        }

        Map<String, Object> map = boxed == Map.class || boxed == LinkedHashMap.class
                ? new LinkedHashMap<>()
                : TypeRegistry.newMapContainer(boxed, 0, false);
        String name;
        while ((name = reader.nextName()) != null) {
            map.put(name, readNode(reader, valueType, valueBoxed, valueTi, context));
        }
        return map;
    }


    /**
     * --------------------------------------------------------------
     * List
     * --------------------------------------------------------------
     */

    public static List<Object> readList(StreamingReader reader, Type type, Class<?> boxed,
                                        Type elementType, Class<?> elementBoxed, TypeInfo elementTi,
                                        RuntimeContext context) throws IOException {
        if (!reader.nextIfArrayStart()) {
            if (reader.nextIfNull()) {
                return null;
            }
            throw new BindingException("cannot read token '" + reader.peekToken() + "' as array type '" +
                    type.getTypeName() + "'");
        }

        if (elementType == null) {
            elementType = Types.resolveTypeArgument(type, List.class, 0);
            elementBoxed = Types.rawBox(elementType);
            elementTi = TypeRegistry.registerTypeInfo(elementBoxed);
        }

        List<Object> list = boxed == List.class || boxed == ArrayList.class
                ? new ArrayList<>()
                : TypeRegistry.newListContainer(boxed, 0, false);
        while (!reader.nextIfArrayEnd()) {
            list.add(readNode(reader, elementType, elementBoxed, elementTi, context));
        }
        return list;
    }

    /**
     * --------------------------------------------------------------
     * Set
     * --------------------------------------------------------------
     */

    public static Set<Object> readSet(StreamingReader reader, Type type, Class<?> boxed,
                                      Type elementType, Class<?> elementBoxed, TypeInfo elementTi,
                                      RuntimeContext context) throws IOException {
        if (!reader.nextIfArrayStart()) {
            if (reader.nextIfNull()) {
                return null;
            }
            throw new BindingException("cannot read token '" + reader.peekToken() + "' as array type '" +
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
        while (!reader.nextIfArrayEnd()) {
            Object value = readNode(reader, elementType, elementBoxed, elementTi, context);
            set.add(value);
        }
        return set;
    }

    /**
     * --------------------------------------------------------------
     * Java Array
     * --------------------------------------------------------------
     */

    public static Object readArray(StreamingReader reader, Type type, Class<?> boxed,
                                      Class<?> componentClazz, Class<?> componentBoxed, TypeInfo componentTi,
                                      RuntimeContext context) throws IOException {
        if (!reader.nextIfArrayStart()) {
            if (reader.nextIfNull()) {
                return null;
            }
            throw new BindingException("cannot read token '" + reader.peekToken() + "' as array type '" +
                    type.getTypeName() + "'");
        }

        if (componentClazz == null) {
            componentClazz = boxed.getComponentType();
            componentBoxed = Types.box(componentClazz);
            componentTi = TypeRegistry.registerTypeInfo(componentClazz);
        }

        if (componentClazz == int.class) {
            return _readIntArray(reader);
        } else if (componentClazz == long.class) {
            return _readLongArray(reader);
        } else if (componentClazz == double.class) {
            return _readDoubleArray(reader);
        } else if (componentClazz == float.class) {
            return _readFloatArray(reader);
        } else if (componentClazz == boolean.class) {
            return _readBooleanArray(reader);
        } else if (componentClazz == short.class) {
            return _readShortArray(reader);
        } else if (componentClazz == byte.class) {
            return _readByteArray(reader);
        } else if (componentClazz == char.class) {
            return _readCharArray(reader);
        }

        Object arr = null;
        int size = 0;
        while (!reader.nextIfArrayEnd()) {
            if (arr == null) {
                arr = Array.newInstance(componentClazz, 8);
            } else if (size == Array.getLength(arr)) {
                Object expanded = Array.newInstance(componentClazz, size << 1);
                System.arraycopy(arr, 0, expanded, 0, size);
                arr = expanded;
            }

            Object value = readNode(reader, componentClazz, componentBoxed, componentTi, context);
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

    private static boolean[] _readBooleanArray(StreamingReader reader) throws IOException {
        boolean[] array = new boolean[8];
        int size = 0;
        while (!reader.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = reader.readBooleanValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    private static int[] _readIntArray(StreamingReader reader) throws IOException {
        int[] array = new int[8];
        int size = 0;
        while (!reader.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = reader.readIntValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    private static long[] _readLongArray(StreamingReader reader) throws IOException {
        long[] array = new long[8];
        int size = 0;
        while (!reader.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = reader.readLongValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    private static double[] _readDoubleArray(StreamingReader reader) throws IOException {
        double[] array = new double[8];
        int size = 0;
        while (!reader.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = reader.readDoubleValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    private static float[] _readFloatArray(StreamingReader reader) throws IOException {
        float[] array = new float[8];
        int size = 0;
        while (!reader.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = reader.readFloatValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    private static short[] _readShortArray(StreamingReader reader) throws IOException {
        short[] array = new short[8];
        int size = 0;
        while (!reader.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = reader.readShortValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    private static byte[] _readByteArray(StreamingReader reader) throws IOException {
        byte[] array = new byte[8];
        int size = 0;
        while (!reader.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = reader.readByteValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    private static char[] _readCharArray(StreamingReader reader) throws IOException {
        char[] array = new char[8];
        int size = 0;
        while (!reader.nextIfArrayEnd()) {
            if (size == array.length) {
                array = Arrays.copyOf(array, size << 1);
            }
            array[size++] = reader.readCharValue();
        }
        return size == array.length ? array : Arrays.copyOf(array, size);
    }

    /**
     * --------------------------------------------------------------
     * JsonArray subclasses
     * --------------------------------------------------------------
     */

    public static JsonArray readJsonArray(StreamingReader reader, Type type, Class<?> boxed, TypeInfo ti,
                                          RuntimeContext context) throws IOException {
        if (!reader.nextIfArrayStart()) {
            if (reader.nextIfNull()) {
                return null;
            }
            throw new BindingException("cannot read token '" + reader.peekToken() + "' as array type '" +
                    type.getTypeName() + "'");
        }
        JsonArray array = (JsonArray) ti.pojoInfo.creatorInfo.forceNewPojo();
        Class<?> element = array.elementClass();
        TypeInfo elementInfo = TypeRegistry.registerTypeInfo(element);
        while (!reader.nextIfArrayEnd()) {
            Object value = readNode(reader, element, element, elementInfo, context);
            array.add(value);
        }
        return array;
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


    /**
     * --------------------------------------------------------------
     * Write
     * --------------------------------------------------------------
     */

    public static void writeNode(StreamingWriter writer, Object node,
                                 RuntimeContext context) throws IOException {
        Asserts.notNull(writer, "writer");
        Asserts.notNull(context, "context");

        try {
            _writeNode(writer, node, TypeInfo.NONE, context);
        } catch (BindingException | IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new BindingException("failed to write node of type '" + Types.name(node) + "'", e);
        }
    }


    private static void _writeNode(StreamingWriter writer, Object node, TypeInfo typeInfo,
                                   RuntimeContext context) throws IOException {
        if (node == null) {
            writer.writeNull();
            return;
        }

        Class<?> clazz = node.getClass();
        if (typeInfo.clazz == clazz) {
            if (typeInfo.pojoInfo != null && !typeInfo.pojoInfo.isJajo) {
                writePojo(writer, node, typeInfo.pojoInfo, context);
                return;
            }

            if (typeInfo.externalNode != null) {
                writeExternalNode(writer, node, typeInfo.externalNode, context);
                return;
            }

            if (typeInfo.valueInfos != null) {
                String valueFormat = context.defaultValueFormat(clazz);
                ValueInfo vi = typeInfo.requireValueInfo(valueFormat);
                _writeNode(writer, vi.valueToRaw(node), TypeInfo.NONE, context);
                return;
            }
        }

        if (clazz == String.class) {
            writer.writeStringValue((String) node);
            return;
        }
        if (clazz == Boolean.class) {
            writer.writeBooleanValue((Boolean) node);
            return;
        }
        if (clazz == Integer.class) {
            writer.writeIntValue((Integer) node);
            return;
        }
        if (clazz == Long.class) {
            writer.writeLongValue((Long) node);
            return;
        }
        if (clazz == Double.class) {
            writer.writeDoubleValue((Double) node);
            return;
        }
        if (clazz == Float.class) {
            writer.writeFloatValue((Float) node);
            return;
        }
        if (clazz == Short.class) {
            writer.writeShortValue((Short) node);
            return;
        }
        if (clazz == Byte.class) {
            writer.writeByteValue((Byte) node);
            return;
        }
        if (clazz == BigInteger.class) {
            writer.writeBigIntegerValue((BigInteger) node);
            return;
        }
        if (clazz == BigDecimal.class) {
            writer.writeBigDecimalValue((BigDecimal) node);
            return;
        }
        if (node instanceof Number) {
            writer.writeNumberValue((Number) node);
            return;
        }

        if (clazz == Character.class) {
            writer.writeCharValue((Character) node);
            return;
        }

        if (node instanceof Enum) {
            writer.writeStringValue(((Enum<?>) node).name());
            return;
        }


        /*
         * --------------------------------------------------------------
         * Built-in object / array nodes
         * --------------------------------------------------------------
         */

        if (node instanceof Map) {
            writeMap(writer, (Map<?, ?>) node, TypeInfo.NONE, context);
            return;
        }

        if (node instanceof List) {
            writeList(writer, (List<?>) node, TypeInfo.NONE, context);
            return;
        }

        /*
         * Exact JsonObject only.
         *
         * A JsonObject subclass is a JOJO and must go through PojoInfo so
         * declared properties are written before dynamic properties.
         */
        if (clazz == JsonObject.class) {
            writeJsonObject(writer, (JsonObject) node, context);
            return;
        }

        if (node instanceof JsonArray) {
            writeJsonArray(writer, (JsonArray) node, context);
            return;
        }

        if (node instanceof Set) {
            writeSet(writer, (Set<?>) node, TypeInfo.NONE, context);
            return;
        }

        if (clazz.isArray()) {
            writeArray(writer, node, clazz, TypeInfo.NONE, context);
            return;
        }


        /*
         * --------------------------------------------------------------
         * Registered nodes
         * --------------------------------------------------------------
         */

        TypeInfo ti = TypeRegistry.registerTypeInfo(clazz);

        /*
         * External JSON tree.
         *
         * Do this before POJO fallback. An external JsonNode / JsonElement
         * is already an OBNT tree and must not be reflected as a POJO.
         */
        if (ti.externalNode != null) {
            writeExternalNode(writer, node, ti.externalNode, context);
            return;
        }

        /*
         * NodeValue / ValueCodec.
         */
        if (ti.valueInfos != null) {
            String valueFormat = context.defaultValueFormat(clazz);
            ValueInfo vi = ti.requireValueInfo(valueFormat);
            _writeNode(writer, vi.valueToRaw(node), TypeInfo.NONE, context);
            return;
        }

        /*
         * POJO / JOJO.
         */
        if (ti.pojoInfo != null && !ti.pojoInfo.isJajo) {
            writePojo(writer, node, ti.pojoInfo, context);
            return;
        }

        throw new BindingException("unsupported node type '" + Types.name(node) + "'");
    }


    /*
     * --------------------------------------------------------------
     * Object nodes
     * --------------------------------------------------------------
     */

    public static void writeMap(StreamingWriter writer, Map<?, ?> map, TypeInfo valueTi,
                                RuntimeContext context) throws IOException {
        writer.startObject();
        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value == null && !context.includeNulls) {
                continue;
            }
            writer.writeName(entry.getKey().toString(), count > 0);
            count++;
            _writeNode(writer, value, valueTi, context);
        }
        writer.endObject();
    }


    public static void writeJsonObject(StreamingWriter writer, JsonObject object,
                                       RuntimeContext context) throws IOException {
        writer.startObject();
        int count = 0;
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            Object value = entry.getValue();
            if (value == null && !context.includeNulls) {
                continue;
            }
            writer.writeName(entry.getKey(), count > 0);
            count++;
            _writeNode(writer, value, TypeInfo.NONE, context);
        }
        writer.endObject();
    }


    /*
     * --------------------------------------------------------------
     * Array nodes
     * --------------------------------------------------------------
     */

    public static void writeList(StreamingWriter writer, List<?> list, TypeInfo elementTi,
                                 RuntimeContext context) throws IOException {
        writer.startArray();
        if (list instanceof RandomAccess) {
            for (int i = 0, size = list.size(); i < size; i++) {
                if (i > 0) {
                    writer.separateElement();
                }
                _writeNode(writer, list.get(i), elementTi, context);
            }
        } else {
            boolean separated = false;
            for (Object value : list) {
                if (separated) {
                    writer.separateElement();
                } else {
                    separated = true;
                }
                _writeNode(writer, value, elementTi, context);
            }
        }
        writer.endArray();
    }


    public static void writeJsonArray(StreamingWriter writer, JsonArray array,
                                      RuntimeContext context) throws IOException {
        writer.startArray();
        for (int i = 0, size = array.size(); i < size; i++) {
            if (i > 0) {
                writer.separateElement();
            }
            _writeNode(writer, array.getNode(i), TypeInfo.NONE, context);
        }
        writer.endArray();
    }


    public static void writeSet(StreamingWriter writer, Set<?> set, TypeInfo elementTi,
                                RuntimeContext context) throws IOException {
        writer.startArray();
        boolean separated = false;
        for (Object value : set) {
            if (separated) {
                writer.separateElement();
            } else {
                separated = true;
            }
            _writeNode(writer, value, elementTi, context);
        }
        writer.endArray();
    }


    /*
     * --------------------------------------------------------------
     * Java arrays
     * --------------------------------------------------------------
     */

    public static void writeArray(StreamingWriter writer, Object node, Class<?> clazz, TypeInfo compTi,
                                  RuntimeContext context) throws IOException {
        writer.startArray();
        if (clazz == boolean[].class) {
            boolean[] array = (boolean[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeBooleanValue(array[i]);
            }
        } else if (clazz == byte[].class) {
            byte[] array = (byte[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeByteValue(array[i]);
            }
        } else if (clazz == short[].class) {
            short[] array = (short[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeShortValue(array[i]);
            }
        } else if (clazz == int[].class) {
            int[] array = (int[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeIntValue(array[i]);
            }
        } else if (clazz == long[].class) {
            long[] array = (long[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeLongValue(array[i]);
            }
        } else if (clazz == float[].class) {
            float[] array = (float[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeFloatValue(array[i]);
            }
        } else if (clazz == double[].class) {
            double[] array = (double[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeDoubleValue(array[i]);
            }
        } else if (clazz == char[].class) {
            char[] array = (char[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                writer.writeCharValue(array[i]);
            }
        } else {
            if (compTi.isNone()) {
                compTi = TypeRegistry.registerTypeInfo(clazz.getComponentType());
            }
            Object[] array = (Object[]) node;
            for (int i = 0; i < array.length; i++) {
                if (i > 0) writer.separateElement();
                _writeNode(writer, array[i], compTi, context);
            }
        }
        writer.endArray();
    }


    /*
     * --------------------------------------------------------------
     * External nodes
     * --------------------------------------------------------------
     */

    public static void writeExternalNode(StreamingWriter writer, Object node, ExternalNode<Object> external,
                                         RuntimeContext context) throws IOException {
        JsonType type = external.jsonType(node);
        switch (type) {
            case OBJECT: {
                writer.startObject();
                int count = 0;
                for (Map.Entry<String, Object> entry : external.entrySetInObject(node)) {
                    /*
                     * External nodes represent an already materialized JSON tree.
                     * Do not apply includeNulls filtering here.
                     *
                     * Native JSON null should normally be represented by a native
                     * null node anyway, rather than Java null.
                     */
                    writer.writeName(entry.getKey(), count > 0);
                    count++;
                    _writeNode(writer, entry.getValue(), TypeInfo.NONE, context);
                }
                writer.endObject();
                return;
            }
            case ARRAY: {
                writer.startArray();
                Iterator<Object> it = external.iteratorInArray(node);
                boolean separated = false;
                while (it.hasNext()) {
                    if (separated) {
                        writer.separateElement();
                    } else {
                        separated = true;
                    }
                    _writeNode(writer, it.next(), TypeInfo.NONE, context);
                }
                writer.endArray();
                return;
            }
            case STRING:
                writer.writeString(external.toString(node));
                return;
            case NUMBER:
                writer.writeNumber(external.toNumber(node));
                return;
            case BOOLEAN:
                writer.writeBoolean(external.toBoolean(node));
                return;
            case NULL:
                writer.writeNull();
                return;
            default:
                throw new BindingException("unsupported external node type '" + Types.name(node) + "'");
        }
    }


    /*
     * --------------------------------------------------------------
     * POJO / JOJO
     * --------------------------------------------------------------
     */

    public static void writePojo(StreamingWriter writer, Object node, PojoInfo pojoInfo,
                                 RuntimeContext context) throws IOException {
        writer.startObject();
        PropertyWriter[] propertyWriters = pojoInfo.propertyWriters;
        CompiledName[] compiledNames = writer.compiledNames(pojoInfo);
        int count = 0;
        for (int i = 0, len = propertyWriters.length; i < len; i++) {
            count = propertyWriters[i].write(writer, compiledNames[i], node, context, count);
        }

        /*
         * JOJO dynamic properties come after declared properties.
         */
        if (pojoInfo.isJojo && pojoInfo.writeDynamic) {
            Map<String, Object> dynamic = InternalAccess.dynamicProperties((JsonObject) node);
            if (dynamic != null) {
                for (Map.Entry<String, Object> entry : dynamic.entrySet()) {
                    Object value = entry.getValue();
                    if (value == null && !context.includeNulls) {
                        continue;
                    }

                    writer.writeName(entry.getKey(), count > 0);
                    count++;
                    _writeNode(writer, value, TypeInfo.NONE, context);
                }
            }
        }
        writer.endObject();
    }



}