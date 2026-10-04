package org.sjf4j.binding;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.mapping.NodeMapper;
import org.sjf4j.node.FieldInfo;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/** Target-directed V4 streaming binding helpers. */
public final class StreamingIOV4 {

    private static final Object UNSET = new Object();

    private StreamingIOV4() {
    }


    public static Object readNode(StreamingReaderV4 reader, Type type,
                                  RuntimeContext context) throws IOException {
        Asserts.notNull(reader, "reader");
        Asserts.notNull(type, "type");
        Asserts.notNull(context, "context");

        Class<?> boxed = Types.rawBox(type);
        TypeInfo ti = TypeRegistry.registerTypeInfo(boxed);
        return readNode(reader, type, boxed, ti, context);
    }


    static Object readNode(StreamingReaderV4 reader, Type type, Class<?> boxed, TypeInfo typeInfo,
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

            if (typeInfo.isNodeValue()) {
                return readValueWithCodec(reader, typeInfo.requireValueInfo(context.defaultValueFormat(boxed)));
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
                return readEnum(reader, boxed);
            }

            /*
             * Object nodes.
             */

            if (Map.class.isAssignableFrom(boxed)) {
                return readMapOrNull(reader, type, boxed, context);
            }

            if (boxed == JsonObject.class) {
                Object raw = reader.readRawNode();
                if (raw == null) return null;
                return new JsonObject(asObject(raw));
            }

            /*
             * Array nodes.
             */

            if (List.class.isAssignableFrom(boxed)) {
                return readListOrNull(reader, type, boxed, context);
            }

            if (Set.class.isAssignableFrom(boxed)) {
                return readSetOrNull(reader, type, boxed, context);
            }

            if (boxed.isArray()) {
                return readArrayOrNull(reader, boxed, context);
            }

            if (boxed == JsonArray.class) {
                Object raw = reader.readRawNode();
                if (raw == null) return null;
                return new JsonArray(asArray(raw));
            }

            if (JsonArray.class.isAssignableFrom(boxed)) {
                return readJsonArrayOrNull(reader, boxed, typeInfo, context);
            }

            /*
             * POJO nodes.
             */

            PojoInfo pojoInfo = typeInfo.pojoInfo;

            if (pojoInfo != null && !pojoInfo.isJajo) {
                return readPojoOrNull(reader, type, boxed, pojoInfo, context);
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
        } catch (Exception e) {
            throw new BindingException("failed to read streaming into '" + type + "'", e);
        }
    }


    /**
     * --------------------------------------------------------------
     * OneOf
     * --------------------------------------------------------------
     */

    private static Object readOneOf(StreamingReaderV4 reader, OneOfInfo oneOfInfo,
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
    private static Object readEnum(StreamingReaderV4 reader, Class<?> enumType) throws IOException {
        StreamingReaderV4.Token token = reader.peekToken();
        if (token == StreamingReaderV4.Token.NULL) {
            reader.readNull();
            return null;
        }

        if (token == StreamingReaderV4.Token.STRING) {
            return Enum.valueOf((Class<? extends Enum>) enumType, reader.readString());
        }

        if (token == StreamingReaderV4.Token.NUMBER) {
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

    private static Object readValueWithCodec(StreamingReaderV4 reader, ValueInfo valueInfo) throws IOException {
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
            return valueInfo.rawToValue(asObject(value));
        }

        if (raw == List.class) {
            Object value = reader.readRawNode();
            if (value == null) {
                return valueInfo.rawToValue(null);
            }
            return valueInfo.rawToValue(asArray(value));
        }

        throw new BindingException("cannot read value with ValueCodec into type '" + raw.getName() + "'");
    }


    /**
     * --------------------------------------------------------------
     * POJO
     * --------------------------------------------------------------
     */

    private static Object readPojoOrNull(StreamingReaderV4 reader, Type type, Class<?> boxed, PojoInfo pojoInfo,
                                         RuntimeContext context) throws IOException {

        /*
         * Creator/parent-discriminator POJOs need streaming access to the
         * object members, so consume START_OBJECT here.
         */
        if (pojoInfo.hasParentScopeOneOf || !pojoInfo.creatorInfo.hasNoArgsCreator()) {
            if (!startObjectOrNull(reader, boxed)) {
                return null;
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
         * Fast non-null path:
         *
         * nextIfObjectStart() directly succeeds for Jackson and therefore
         * avoids creating prefetched state.
         */
        if (!startObjectOrNull(reader, boxed)) {
            return null;
        }

        Object pojo = pojoInfo.creatorInfo.newPojoNoArgs();
        StreamingReaderV4.NameMatcher matcher = pojoInfo.aliasProperties == null ? reader.nameMatcher(boxed) : null;
        if (matcher != null) {
            int expected = 0;
            int index;
            while ((index = reader.nextNameMatch(matcher, expected)) != StreamingReaderV4.NameMatcher.END_OF_OBJECT) {
                FieldInfo field = index >= 0 ? pojoInfo.properties.get(matcher.name(index)) : null;
                if (field == null || !field.hasSetter()) {
                    reader.skipNode();
                } else {
                    field.invokeSetterIfPresent(pojo, readField(reader, field, type, boxed, null, context));
                }

                /*
                 * Keep the ordered-name optimization enabled only while
                 * every matched property remains exactly in sequence.
                 *
                 * Important: UNKNOWN == -1, so never compare index against
                 * expected when expected has already been disabled.
                 */
                if (expected >= 0 && index == expected) {
                    expected++;
                    if (expected >= pojoInfo.properties.size()) {
                        expected = -1;
                    }
                } else {
                    expected = -1;
                }
            }
        } else {
            String name;
            while ((name = reader.nextName()) != null) {
                FieldInfo field = pojoInfo.aliasProperties != null ? pojoInfo.aliasProperties.get(name)
                        : pojoInfo.properties.get(name);

                if (field == null || !field.hasSetter()) {
                    reader.skipNode();
                } else {
                    field.invokeSetterIfPresent(pojo, readField(reader, field, type, boxed, null, context));
                }
            }
        }

        return pojo;
    }


    /**
     * Reads a POJO whose object start has already been consumed.
     */
    private static Object readParentOneOfPojo(StreamingReaderV4 reader, Type type, Class<?> boxed, PojoInfo pojoInfo,
                                              RuntimeContext context) throws IOException {

        CreatorInfo creator = pojoInfo.creatorInfo;
        CreatorState state = new CreatorState(creator);
        FieldInfo deferred = null;
        Object deferredRaw = null;
        String parentKey = null;
        Object parentValue = UNSET;

        String name;
        while ((name = reader.nextName()) != null) {
            int argIndex = creator.getArgIndexOrAlias(name);
            if (argIndex >= 0) {
                Type argType = Types.resolveMemberType(type, boxed, creator.argTypes[argIndex]);
                Class<?> argBoxed = Types.rawBox(argType);
                ValueInfo codec = creator.argValueCodecs[argIndex];
                Object value = codec == null
                        ? readNode(reader, argType, argBoxed, TypeRegistry.registerTypeInfo(argBoxed), context)
                        : readValueWithCodec(reader, codec);

                state.acceptCtorArg(argIndex, value);
                if (parentKey != null && parentKey.equals(name)) {
                    parentValue = value;
                }
                continue;
            }

            FieldInfo field = pojoInfo.aliasProperties != null
                    ? pojoInfo.aliasProperties.get(name)
                    : pojoInfo.properties.get(name);

            if (field == null) {
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

            OneOfInfo oneOfInfo = field.oneOfInfo;
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
                    accept(state, field, readNode(reader, target, context));
                } else if (deferred == null) {
                    deferred = field;
                    deferredRaw = reader.readRawNode();
                } else {
                    throw new BindingException("at most one OneOf field with scope=PARENT is supported per class");
                }

                continue;
            }

            if (parentKey != null && parentKey.equals(name)) {
                Object value = readField(reader, field, type, boxed, null, context);
                parentValue = value;
                accept(state, field, value);
                continue;
            }

            if (!field.hasSetter()) {
                reader.skipNode();
            } else {
                accept(state, field, readField(reader, field, type, boxed, null, context));
            }
        }

        Object pojo = state.finish();
        if (deferred == null) {
            return pojo;
        }

        OneOfInfo oneOfInfo = deferred.oneOfInfo;
        if (parentValue == UNSET) {
            FieldInfo parentField = pojoInfo.aliasProperties != null
                    ? pojoInfo.aliasProperties.get(parentKey)
                    : pojoInfo.properties.get(parentKey);

            if (parentField != null) {
                parentValue = parentField.invokeGetter(pojo);
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


    private static void accept(CreatorState state, FieldInfo field, Object value) {
        if (state.isCreated()) {
            field.invokeSetterIfPresent(state.pojo(), value);
        } else {
            state.bufferProperty(field, value);
        }
    }


    /**
     * --------------------------------------------------------------
     * Field
     * --------------------------------------------------------------
     */

    private static Object readField(StreamingReaderV4 reader, FieldInfo field, Type ownerType, Class<?> ownerBoxed,
                                    TypeInfo cachedTypeInfo, RuntimeContext context) throws IOException {
        Type type = field.genericDependent
                ? Types.resolveMemberType(ownerType, ownerBoxed, field.type)
                : field.type;

        Class<?> boxed = field.genericDependent
                ? Types.rawBox(type)
                : field.boxed;

        if (field.oneOfInfo != null) {
            return readOneOf(reader, field.oneOfInfo, context);
        }

        if (field.valueInfo != null) {
            return readValueWithCodec(reader, field.valueInfo);
        }

        return readNode(reader, type, boxed,
                cachedTypeInfo == null || field.genericDependent ? TypeRegistry.registerTypeInfo(boxed) : cachedTypeInfo,
                context);
    }


    /**
     * --------------------------------------------------------------
     * Map
     * --------------------------------------------------------------
     */

    private static Map<String, Object> readMapOrNull(StreamingReaderV4 reader, Type type, Class<?> boxed,
                                                     RuntimeContext context) throws IOException {

        if (!startObjectOrNull(reader, boxed)) {
            return null;
        }

        Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
        Class<?> valueBoxed = Types.rawBox(valueType);
        TypeInfo valueInfo = TypeRegistry.registerTypeInfo(valueBoxed);
        Map<String, Object> map = boxed == Map.class || boxed == LinkedHashMap.class
                ? new LinkedHashMap<String, Object>()
                : TypeRegistry.newMapContainer(boxed, 0, false);

        String name;
        while ((name = reader.nextName()) != null) {
            map.put(name, readNode(reader, valueType, valueBoxed, valueInfo, context));
        }
        return map;
    }


    /**
     * --------------------------------------------------------------
     * List
     * --------------------------------------------------------------
     */

    private static List<Object> readListOrNull(StreamingReaderV4 reader, Type type, Class<?> boxed,
                                               RuntimeContext context) throws IOException {

        if (!startArrayOrNull(reader, boxed)) {
            return null;
        }

        Type elementType = Types.resolveTypeArgument(type, List.class, 0);
        Class<?> elementBoxed = Types.rawBox(elementType);
        TypeInfo elementInfo = TypeRegistry.registerTypeInfo(elementBoxed);
        List<Object> list = boxed == List.class || boxed == ArrayList.class
                ? new ArrayList<>()
                : TypeRegistry.newListContainer(boxed, 0, false);

        while (!reader.nextIfArrayEnd()) {
            list.add(readNode(reader, elementType, elementBoxed, elementInfo, context));
        }
        return list;
    }


    /**
     * --------------------------------------------------------------
     * Set
     * --------------------------------------------------------------
     */

    private static Set<Object> readSetOrNull(StreamingReaderV4 reader, Type type, Class<?> boxed,
                                             RuntimeContext context) throws IOException {

        if (!startArrayOrNull(reader, boxed)) {
            return null;
        }

        Type elementType = Types.resolveTypeArgument(type, Set.class, 0);
        Class<?> elementBoxed = Types.rawBox(elementType);
        TypeInfo elementInfo = TypeRegistry.registerTypeInfo(elementBoxed);
        Set<Object> set = boxed == Set.class || boxed == LinkedHashSet.class
                ? new LinkedHashSet<>()
                : TypeRegistry.newSetContainer(boxed, 0, false);

        while (!reader.nextIfArrayEnd()) {
            set.add(readNode(reader, elementType, elementBoxed, elementInfo, context));
        }
        return set;
    }


    /**
     * --------------------------------------------------------------
     * Java Array
     * --------------------------------------------------------------
     */

    private static Object readArrayOrNull(StreamingReaderV4 reader, Class<?> arrayType,
                                          RuntimeContext context) throws IOException {

        if (!startArrayOrNull(reader, arrayType)) {
            return null;
        }

        Class<?> component = arrayType.getComponentType();
        Class<?> componentBoxed = Types.box(component);
        TypeInfo componentInfo = TypeRegistry.registerTypeInfo(componentBoxed);
        Object array = null;
        int size = 0;

        while (!reader.nextIfArrayEnd()) {
            if (array == null) {
                array = Array.newInstance(component, 8);
            } else if (size == Array.getLength(array)) {
                Object expanded = Array.newInstance(component, size << 1);
                System.arraycopy(array, 0, expanded, 0, size);
                array = expanded;
            }

            Array.set(array, size++, readNode(reader, component, componentBoxed, componentInfo, context));
        }

        if (array == null) {
            return Array.newInstance(component, 0);
        }

        if (size == Array.getLength(array)) {
            return array;
        }

        Object exact = Array.newInstance(component, size);
        System.arraycopy(array, 0, exact, 0, size);
        return exact;
    }


    /**
     * --------------------------------------------------------------
     * JsonArray subclasses
     * --------------------------------------------------------------
     */

    private static JsonArray readJsonArrayOrNull(StreamingReaderV4 reader, Class<?> boxed, TypeInfo typeInfo,
                                                 RuntimeContext context) throws IOException {

        if (!startArrayOrNull(reader, boxed)) {
            return null;
        }

        JsonArray array = (JsonArray) typeInfo.pojoInfo.creatorInfo.forceNewPojo();
        Class<?> element = array.elementClass();
        TypeInfo elementInfo = TypeRegistry.registerTypeInfo(element);
        while (!reader.nextIfArrayEnd()) {
            array.add(readNode(reader, element, element, elementInfo, context));
        }
        return array;
    }


    /**
     * --------------------------------------------------------------
     * Structure helpers
     * --------------------------------------------------------------
     */

    /**
     * Consumes an object start or a nullable node.
     *
     * <p>The object-start test is intentionally performed before the null
     * test. For the normal non-null path this allows token-based backends
     * such as Jackson to consume START_OBJECT directly without creating a
     * prefetched token.</p>
     *
     * @return {@code true} when an object was started,
     *         {@code false} when JSON null was consumed
     */
    private static boolean startObjectOrNull(StreamingReaderV4 reader, Class<?> targetType) throws IOException {
        if (reader.nextIfObjectStart()) {
            return true;
        }

        if (reader.nextIfNull()) {
            return false;
        }

        throw new BindingException("cannot read token '" + reader.peekToken() + "' as object type '" +
                targetType.getName() + "'");
    }

    /**
     * Consumes an array start or a nullable node.
     *
     * <p>The array-start test is intentionally performed before the null
     * test so that the normal non-null path does not require lookahead
     * compensation on token-based parsers.</p>
     *
     * @return {@code true} when an array was started,
     *         {@code false} when JSON null was consumed
     */
    private static boolean startArrayOrNull(StreamingReaderV4 reader, Class<?> targetType) throws IOException {
        if (reader.nextIfArrayStart()) {
            return true;
        }

        if (reader.nextIfNull()) {
            return false;
        }

        throw new BindingException("cannot read token '" + reader.peekToken() + "' as array type '" +
                targetType.getName() + "'");
    }


    /**
     * --------------------------------------------------------------
     * Raw node validation
     * --------------------------------------------------------------
     */

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        throw new BindingException("cannot read non-object value as JsonObject");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asArray(Object value) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        throw new BindingException("cannot read non-array value as JsonArray");
    }

}