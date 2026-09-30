package org.sjf4j.binding;

import org.sjf4j.InternalAccess;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.mapping.NodeMapper;
import org.sjf4j.node.CreatorInfo;
import org.sjf4j.node.CreatorState;
import org.sjf4j.node.FieldInfo;
import org.sjf4j.node.OneOfInfo;
import org.sjf4j.node.PojoInfo;
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

/** Standalone V3 streaming read helpers. */
public final class StreamingIOV3 {

    static final Object UNSET = new Object();

    private static final ClassValue<MatchedPojoInfo> MATCHED_POJO_INFOS =
            new ClassValue<MatchedPojoInfo>() {
                @Override
                protected MatchedPojoInfo computeValue(Class<?> type) {
                    PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(type);
                    FieldInfo[] fields = pojoInfo.properties.values().toArray(new FieldInfo[0]);
                    TypeInfo[] typeInfos = new TypeInfo[fields.length];
                    for (int i = 0; i < fields.length; i++) {
                        FieldInfo field = fields[i];
                        if (!field.genericDependent) {
                            typeInfos[i] = TypeRegistry.registerTypeInfo(field.boxed);
                        }
                    }
                    return new MatchedPojoInfo(fields, typeInfos);
                }
            };

    private StreamingIOV3() {
    }

    public static Object readNode(StreamingReaderV3 reader, Type nodeType,
                                  RuntimeContext context) throws IOException {
        Asserts.notNull(reader, "reader");
        Asserts.notNull(context, "context");
        if (nodeType == Object.class) {
            return readRawNode(reader);
        }
        Class<?> nodeBoxed = Types.rawBox(nodeType);
        return readNode(reader, nodeType, nodeBoxed,
                TypeRegistry.registerTypeInfo(nodeBoxed), context);
    }

    static Object readNode(StreamingReaderV3 reader, Type nodeType, Class<?> nodeBoxed,
                           TypeInfo ti, RuntimeContext context) {
        try {
            if (ti.oneOfInfo != null) {
                return OneOfIOV3.readOneOf(reader, ti.oneOfInfo, context);
            }
            if (nodeBoxed == Object.class) {
                return readRawNode(reader);
            }
            if (ti.isNodeValue()) {
                return readValueWithCodec(reader, nodeType, nodeBoxed,
                        ti.requireValueInfo(context.defaultValueFormat(nodeBoxed)), context);
            }
            switch (reader.currentToken()) {
                case START_OBJECT:
                    return readObject(reader, nodeType, nodeBoxed, ti, context);
                case START_ARRAY:
                    return readArray(reader, nodeType, nodeBoxed, ti, context);
                case STRING:
                    return readString(reader, nodeBoxed, ti, context);
                case NUMBER:
                    return readNumber(reader, nodeBoxed, ti, context);
                case BOOLEAN:
                    return readBoolean(reader, nodeBoxed, ti, context);
                case NULL:
                    reader.readNull();
                    return null;
                default:
                    throw new BindingException("unexpected token '" + reader.currentToken() + "'");
            }
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to read streaming into '" + nodeType + "'", e);
        }
    }

    public static Object readRawNode(StreamingReaderV3 reader) throws IOException {
        return reader.readRawNode();
    }

    static Object readBoolean(StreamingReaderV3 reader, Class<?> boxed, TypeInfo ti,
                              RuntimeContext context) throws IOException {
        if (boxed == Boolean.class) return reader.readBooleanValue();
        if (ti.isNodeValue()) {
            return ti.requireValueInfo(context.defaultValueFormat(boxed))
                    .rawToValue(reader.readBooleanValue());
        }
        throw new BindingException("cannot read boolean value into type '" + boxed + "'");
    }

    static Object readNumber(StreamingReaderV3 reader, Class<?> boxed, TypeInfo ti,
                             RuntimeContext context) throws IOException {
        if (boxed == Number.class) return reader.readNumber();
        if (boxed == Integer.class) return reader.readIntValue();
        if (boxed == Long.class) return reader.readLongValue();
        if (boxed == Float.class) return reader.readFloatValue();
        if (boxed == Double.class) return reader.readDoubleValue();
        if (boxed == Short.class) return reader.readShortValue();
        if (boxed == Byte.class) return reader.readByteValue();
        if (boxed == BigInteger.class) return reader.readBigInteger();
        if (boxed == BigDecimal.class) return reader.readBigDecimal();
        if (boxed.isEnum()) return enumByOrdinal(boxed, reader.readIntValue());
        if (ti.isNodeValue()) {
            return ti.requireValueInfo(context.defaultValueFormat(boxed))
                    .rawToValue(reader.readNumber());
        }
        throw new BindingException("cannot read number value into type '" + boxed + "'");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object readString(StreamingReaderV3 reader, Class<?> boxed, TypeInfo ti,
                             RuntimeContext context) throws IOException {
        if (boxed == String.class) return reader.readString();
        if (boxed == Character.class) {
            String value = reader.readString();
            return value.isEmpty() ? null : value.charAt(0);
        }
        if (boxed.isEnum()) return Enum.valueOf((Class<? extends Enum>) boxed, reader.readString());
        if (ti.isNodeValue()) {
            return ti.requireValueInfo(context.defaultValueFormat(boxed))
                    .rawToValue(reader.readString());
        }
        throw new BindingException("cannot read string value into type '" + boxed + "'");
    }

    static Object readObject(StreamingReaderV3 reader, Type nodeType, Class<?> boxed,
                             TypeInfo ti, RuntimeContext context) throws IOException {
        if (Map.class.isAssignableFrom(boxed)) {
            Type valueType = Types.resolveTypeArgument(nodeType, Map.class, 1);
            Class<?> valueBoxed = Types.rawBox(valueType);
            return readMap(reader, boxed, valueType, valueBoxed,
                    TypeRegistry.registerTypeInfo(valueBoxed), context);
        }
        if (boxed == JsonObject.class) return new JsonObject(readRawObject(reader));
        if (!boxed.isInterface() && Modifier.isAbstract(boxed.getModifiers())) {
            throw new BindingException("cannot read object value into abstract type '" + boxed.getName() + "'");
        }
        if (ti.isNodeValue()) {
            return ti.requireValueInfo(context.defaultValueFormat(boxed))
                    .rawToValue(readRawObject(reader));
        }
        if (ti.pojoInfo != null && !ti.pojoInfo.isJajo) {
            return readPojo(reader, nodeType, boxed, ti.pojoInfo, context);
        }
        throw new BindingException("cannot read object value into type '" + boxed + "'");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumByOrdinal(Class<?> enumClass, int ordinal) {
        Enum[] values = ((Class<? extends Enum>) enumClass).getEnumConstants();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new BindingException("enum ordinal '" + ordinal + "' out of range for type '"
                    + enumClass.getName() + "'");
        }
        return values[ordinal];
    }

    static Map<String, Object> readRawObject(StreamingReaderV3 reader) throws IOException {
        StreamingReaderV3.Token token = reader.currentToken();
        if (token != StreamingReaderV3.Token.START_OBJECT) {
            throw new BindingException("unexpected token '" + token + "'");
        }
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        reader.beginObject();
        String name;
        while ((name = reader.nextObjectName()) != null) map.put(name, readRawNode(reader));
        reader.endObject();
        return map;
    }

    static List<Object> readRawArray(StreamingReaderV3 reader) throws IOException {
        StreamingReaderV3.Token token = reader.currentToken();
        if (token != StreamingReaderV3.Token.START_ARRAY) {
            throw new BindingException("unexpected token '" + token + "'");
        }
        List<Object> list = new ArrayList<Object>();
        reader.beginArray();
        while (reader.nextArrayElement()) list.add(readRawNode(reader));
        reader.endArray();
        return list;
    }

    static Object readPojo(StreamingReaderV3 reader, Type pojoType, Class<?> pojoBoxed,
                           PojoInfo pi, RuntimeContext context) throws IOException {
        CreatorInfo ci = pi.creatorInfo;
        boolean standardNoArgs = !pi.hasParentScopeOneOf && ci.hasNoArgsCreator()
                && (ci.argNames == null || ci.argNames.length == 0);
        if (standardNoArgs) {
            Object pojo = ci.newPojoNoArgs();
            boolean dynamic = pi.isJojo && pi.readDynamic;
            NameMatcher matcher = pi.aliasProperties == null && !dynamic
                    ? reader.nameMatcher(pojoBoxed) : null;
            if (matcher != null) {
                readMatchedPojo(reader, pojo, pojoType, pojoBoxed, matcher, context);
            } else {
                readNamedPojo(reader, pojo, pojoType, pojoBoxed, pi, context);
            }
            return pojo;
        }
        return readCreatorPojo(reader, pojoType, pojoBoxed, pi, context);
    }

    private static void readMatchedPojo(StreamingReaderV3 reader, Object pojo, Type pojoType,
                                         Class<?> pojoBoxed, NameMatcher matcher,
                                         RuntimeContext context) throws IOException {
        MatchedPojoInfo info = MATCHED_POJO_INFOS.get(pojoBoxed);
        reader.beginObject();
        int expectedIndex = 0;
        for (;;) {
            int index = reader.nextObjectField(matcher, expectedIndex);
            if (index == StreamingReaderV3.END_OF_OBJECT) break;
            if (index < 0) {
                expectedIndex = StreamingReaderV3.NO_EXPECTED_FIELD;
                reader.skipValue();
                continue;
            }
            FieldInfo field = info.fields[index];
            if (field.hasSetter()) {
                field.invokeSetter(pojo,
                        readMatchedFieldValue(reader, field, info.typeInfos[index],
                                pojoType, pojoBoxed, context));
            } else {
                reader.skipValue();
            }
            if (index != expectedIndex || ++expectedIndex >= info.fields.length) {
                expectedIndex = StreamingReaderV3.NO_EXPECTED_FIELD;
            }
        }
        reader.endObject();
    }

    private static Object readMatchedFieldValue(StreamingReaderV3 reader, FieldInfo field,
                                                TypeInfo typeInfo, Type ownerType,
                                                Class<?> ownerBoxed,
                                                RuntimeContext context) throws IOException {
        if (field.genericDependent) {
            return readFieldValue(reader, field, ownerType, ownerBoxed, context);
        }
        if (field.oneOfInfo != null) return OneOfIOV3.readOneOf(reader, field.oneOfInfo, context);
        if (field.valueInfo != null) {
            return readValueWithCodec(reader, field.type, field.boxed, field.valueInfo, context);
        }
        return readNode(reader, field.type, field.boxed, typeInfo, context);
    }

    private static void readNamedPojo(StreamingReaderV3 reader, Object pojo, Type pojoType,
                                      Class<?> pojoBoxed, PojoInfo pi,
                                      RuntimeContext context) throws IOException {
        Map<String, Object> dynamicMap = null;
        reader.beginObject();
        String key;
        while ((key = reader.nextObjectName()) != null) {
            FieldInfo field = pi.aliasProperties != null ? pi.aliasProperties.get(key) : pi.properties.get(key);
            if (field != null) {
                if (field.hasSetter()) {
                    field.invokeSetter(pojo,
                            readFieldValue(reader, field, pojoType, pojoBoxed, context));
                } else {
                    reader.skipValue();
                }
            } else if (pi.isJojo && pi.readDynamic) {
                if (dynamicMap == null) dynamicMap = new LinkedHashMap<String, Object>();
                dynamicMap.put(key, readRawNode(reader));
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (pi.isJojo) InternalAccess.dynamicProperties((JsonObject) pojo, dynamicMap);
    }

    private static Object readCreatorPojo(StreamingReaderV3 reader, Type pojoType,
                                          Class<?> pojoBoxed, PojoInfo pi,
                                          RuntimeContext context) throws IOException {
        CreatorInfo ci = pi.creatorInfo;
        CreatorState state = new CreatorState(ci);
        FieldInfo deferred = null;
        Object deferredRaw = null;
        String parentKey = null;
        Object parentValue = UNSET;
        reader.beginObject();
        String key;
        while ((key = reader.nextObjectName()) != null) {
            int argIndex = ci.getArgIndexOrAlias(key);
            if (argIndex >= 0) {
                Type argType = Types.resolveMemberType(pojoType, pojoBoxed, ci.argTypes[argIndex]);
                Class<?> argBoxed = Types.rawBox(argType);
                TypeInfo argInfo = TypeRegistry.registerTypeInfo(argBoxed);
                ValueInfo codec = ci.argValueCodecs[argIndex];
                if (codec == null && argInfo.isNodeValue()) {
                    codec = argInfo.requireValueInfo(context.defaultValueFormat(argBoxed));
                }
                Object value = codec == null ? readNode(reader, argType, argBoxed, argInfo, context)
                        : readValueWithCodec(reader, argType, argBoxed, codec, context);
                state.acceptCtorArg(argIndex, value);
                if (parentKey != null && parentKey.equals(key)) parentValue = value;
                continue;
            }
            FieldInfo field = pi.aliasProperties != null ? pi.aliasProperties.get(key) : pi.properties.get(key);
            if (field == null) {
                if (pi.isJojo && pi.readDynamic) {
                    Object value = readRawNode(reader);
                    state.acceptDynamic(key, value);
                    if (parentKey != null && parentKey.equals(key)) parentValue = value;
                } else reader.skipValue();
                continue;
            }
            OneOfInfo oneOf = field.oneOfInfo;
            if (pi.hasParentScopeOneOf && oneOf != null && oneOf.scope == OneOf.Scope.PARENT) {
                if (!oneOf.path.isEmpty()) throw new BindingException("oneOf scope=PARENT does not support path discriminator");
                if (parentKey == null) parentKey = oneOf.key;
                else if (!parentKey.equals(oneOf.key)) throw new BindingException("at most one OneOf parent discriminator key is supported per class");
                Class<?> target = oneOf.matchByWhen(parentValue == UNSET ? null : parentValue);
                if (target != null) {
                    acceptProperty(state, field, readNode(reader, target, context));
                } else if (deferred == null) {
                    deferred = field;
                    deferredRaw = readRawNode(reader);
                } else throw new BindingException("at most one OneOf field with scope=PARENT is supported per class");
                continue;
            }
            if (parentKey != null && parentKey.equals(key)) {
                Object value = readFieldValue(reader, field, pojoType, pojoBoxed, context);
                parentValue = value;
                if (state.isCreated()) field.invokeSetter(state.pojo(), value);
                else state.bufferProperty(field, value);
                continue;
            }
            if (!field.hasSetter()) {
                reader.skipValue();
                continue;
            }
            Object value = readFieldValue(reader, field, pojoType, pojoBoxed, context);
            acceptProperty(state, field, value);
        }
        reader.endObject();
        Object pojo = state.finish();
        if (deferred != null) {
            OneOfInfo oneOf = deferred.oneOfInfo;
            if (parentValue == UNSET) {
                FieldInfo discriminatorField = pi.aliasProperties != null
                        ? pi.aliasProperties.get(oneOf.key) : pi.properties.get(oneOf.key);
                Object discriminator = discriminatorField != null ? discriminatorField.invokeGetter(pojo)
                        : pi.isJojo ? ((JsonObject) pojo).getNode(oneOf.key) : null;
                if (discriminator != null) parentValue = discriminator;
            }
            Class<?> target = oneOf.matchByWhen(parentValue == UNSET ? null : parentValue);
            Object value;
            if (target != null) value = NodeMapper.convert(deferredRaw, target, false, context);
            else if (oneOf.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) value = null;
            else throw new BindingException("oneOf discriminator has no matching mapping: key='" + oneOf.key
                    + "', value='" + (parentValue == UNSET ? null : parentValue) + "'");
            deferred.invokeSetterIfPresent(pojo, value);
        }
        return pojo;
    }

    private static void acceptProperty(CreatorState state, FieldInfo field, Object value) {
        if (state.isCreated()) field.invokeSetterIfPresent(state.pojo(), value);
        else state.bufferProperty(field, value);
    }

    static Object readFieldValue(StreamingReaderV3 reader, FieldInfo field, Type ownerType,
                                 Class<?> ownerBoxed, RuntimeContext context) throws IOException {
        Type type = field.genericDependent ? Types.resolveMemberType(ownerType, ownerBoxed, field.type) : field.type;
        Class<?> boxed = field.genericDependent ? Types.rawBox(type) : field.boxed;
        if (field.oneOfInfo != null) return OneOfIOV3.readOneOf(reader, field.oneOfInfo, context);
        if (field.valueInfo != null) return readValueWithCodec(reader, type, boxed, field.valueInfo, context);
        return readNode(reader, type, boxed, TypeRegistry.registerTypeInfo(boxed), context);
    }

    static Object readArray(StreamingReaderV3 reader, Type nodeType, Class<?> boxed,
                            TypeInfo ti, RuntimeContext context) throws IOException {
        if (List.class.isAssignableFrom(boxed)) {
            Type type = Types.resolveTypeArgument(nodeType, List.class, 0);
            Class<?> element = Types.rawBox(type);
            return readList(reader, boxed, type, element, TypeRegistry.registerTypeInfo(element), context);
        }
        if (boxed == JsonArray.class) return new JsonArray(readRawArray(reader));
        if (Set.class.isAssignableFrom(boxed)) {
            Type type = Types.resolveTypeArgument(nodeType, Set.class, 0);
            Class<?> element = Types.rawBox(type);
            return readSet(reader, boxed, type, element, TypeRegistry.registerTypeInfo(element), context);
        }
        if (boxed.isArray()) {
            Class<?> component = boxed.getComponentType();
            return readJavaArray(reader, component, Types.box(component),
                    TypeRegistry.registerTypeInfo(component), context);
        }
        if (JsonArray.class.isAssignableFrom(boxed)) {
            JsonArray array = (JsonArray) ti.pojoInfo.creatorInfo.forceNewPojo();
            Class<?> element = array.elementClass();
            reader.beginArray();
            while (reader.nextArrayElement()) array.add(readNode(reader, element, element,
                    TypeRegistry.registerTypeInfo(element), context));
            reader.endArray();
            return array;
        }
        if (ti.isNodeValue()) return ti.requireValueInfo(context.defaultValueFormat(boxed))
                .rawToValue(readRawArray(reader));
        throw new BindingException("cannot read array value into type '" + boxed + "'");
    }

    static Object readValueWithCodec(StreamingReaderV3 reader, Type valueType, Class<?> valueBoxed,
                                     ValueInfo valueInfo, RuntimeContext context) throws IOException {
        if (reader.isNull()) {
            reader.readNull();
            return valueInfo.rawToValue(null);
        }
        Class<?> raw = valueInfo.rawClazz;
        if (raw == Map.class) return valueInfo.rawToValue(readRawObject(reader));
        if (raw == List.class) return valueInfo.rawToValue(readRawArray(reader));
        if (raw == String.class) return valueInfo.rawToValue(reader.readString());
        if (raw == Boolean.class) return valueInfo.rawToValue(reader.readBooleanValue());
        if (raw == Integer.class) return valueInfo.rawToValue(reader.readIntValue());
        if (raw == Long.class) return valueInfo.rawToValue(reader.readLongValue());
        if (raw == Double.class) return valueInfo.rawToValue(reader.readDoubleValue());
        if (raw == Float.class) return valueInfo.rawToValue(reader.readFloatValue());
        if (raw == Short.class) return valueInfo.rawToValue(reader.readShortValue());
        if (raw == Byte.class) return valueInfo.rawToValue(reader.readByteValue());
        if (raw == BigInteger.class) return valueInfo.rawToValue(reader.readBigInteger());
        if (raw == BigDecimal.class) return valueInfo.rawToValue(reader.readBigDecimal());
        if (raw == Number.class) return valueInfo.rawToValue(reader.readNumber());
        throw new BindingException("cannot read value with ValueCodec into type '" + raw.getName() + "'");
    }

    static Map<String, Object> readMap(StreamingReaderV3 reader, Class<?> mapClass, Type valueType,
                                       Class<?> valueBoxed, TypeInfo info,
                                       RuntimeContext context) throws IOException {
        Map<String, Object> map = mapClass == Object.class || mapClass == Map.class || mapClass == LinkedHashMap.class
                ? new LinkedHashMap<String, Object>() : TypeRegistry.newMapContainer(mapClass, 0, false);
        reader.beginObject();
        String key;
        while ((key = reader.nextObjectName()) != null) map.put(key, readNode(reader, valueType, valueBoxed, info, context));
        reader.endObject();
        return map;
    }

    static List<Object> readList(StreamingReaderV3 reader, Class<?> listClass, Type elementType,
                                 Class<?> elementBoxed, TypeInfo info,
                                 RuntimeContext context) throws IOException {
        List<Object> list = listClass == Object.class || listClass == List.class || listClass == ArrayList.class
                ? new ArrayList<Object>() : TypeRegistry.newListContainer(listClass, 0, false);
        reader.beginArray();
        while (reader.nextArrayElement()) list.add(readNode(reader, elementType, elementBoxed, info, context));
        reader.endArray();
        return list;
    }

    static Set<Object> readSet(StreamingReaderV3 reader, Class<?> setClass, Type elementType,
                               Class<?> elementBoxed, TypeInfo info,
                               RuntimeContext context) throws IOException {
        Set<Object> set = setClass == Object.class || setClass == Set.class || setClass == LinkedHashSet.class
                ? new LinkedHashSet<Object>() : TypeRegistry.newSetContainer(setClass, 0, false);
        reader.beginArray();
        while (reader.nextArrayElement()) set.add(readNode(reader, elementType, elementBoxed, info, context));
        reader.endArray();
        return set;
    }

    private static Object readJavaArray(StreamingReaderV3 reader, Class<?> component,
                                        Class<?> componentBoxed, TypeInfo info,
                                        RuntimeContext context) throws IOException {
        Object array = null;
        int size = 0;
        reader.beginArray();
        while (reader.nextArrayElement()) {
            if (array == null) array = Array.newInstance(component, 8);
            else if (size == Array.getLength(array)) {
                Object expanded = Array.newInstance(component, size << 1);
                System.arraycopy(array, 0, expanded, 0, size);
                array = expanded;
            }
            Array.set(array, size++, readNode(reader, component, componentBoxed, info, context));
        }
        reader.endArray();
        if (array == null) return Array.newInstance(component, 0);
        if (size == Array.getLength(array)) return array;
        Object exact = Array.newInstance(component, size);
        System.arraycopy(array, 0, exact, 0, size);
        return exact;
    }

    private static final class MatchedPojoInfo {
        final FieldInfo[] fields;
        final TypeInfo[] typeInfos;

        MatchedPojoInfo(FieldInfo[] fields, TypeInfo[] typeInfos) {
            this.fields = fields;
            this.typeInfos = typeInfos;
        }
    }
}
