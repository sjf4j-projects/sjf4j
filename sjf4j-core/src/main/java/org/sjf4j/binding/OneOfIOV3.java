package org.sjf4j.binding;

import org.sjf4j.JsonType;
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
import org.sjf4j.value.ValueInfo;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

/** Standalone V3 OneOf reader. */
final class OneOfIOV3 {

    private OneOfIOV3() {
    }

    static Object readOneOf(StreamingReaderV3 reader, OneOfInfo info,
                            RuntimeContext context) throws IOException {
        if (!info.hasDiscriminator) return readByJsonType(reader, info, context);
        if (info.scope != OneOf.Scope.CURRENT) {
            throw new BindingException("oneOf discriminator scope must be CURRENT here, but was " + info.scope);
        }
        if (reader.currentToken() != StreamingReaderV3.Token.START_OBJECT) {
            if (info.fallbackNull) {
                reader.skipValue();
                return null;
            }
            throw new BindingException("node must be an object, when OneOf has a CURRENT discriminator");
        }
        return info.keyDiscriminator ? readByKey(reader, info, context) : readByPath(reader, info, context);
    }

    private static Object readByJsonType(StreamingReaderV3 reader, OneOfInfo info,
                                         RuntimeContext context) throws IOException {
        Class<?> target = info.matchByJsonType(jsonType(reader.currentToken()));
        if (target != null) return StreamingIOV3.readNode(reader, target, context);
        if (info.fallbackNull) {
            reader.skipValue();
            return null;
        }
        throw new BindingException("oneOf mapping does not support jsonType="
                + jsonType(reader.currentToken()) + " for type '" + info.clazz.getName() + "'");
    }

    private static Object readByPath(StreamingReaderV3 reader, OneOfInfo info,
                                     RuntimeContext context) throws IOException {
        Map<String, Object> raw = StreamingIOV3.readRawObject(reader);
        Object discriminator = info.compiledPath.getNode(raw);
        if (discriminator == null) {
            if (info.fallbackNull) return null;
            throw new BindingException("not found value for discriminator path '" + info.path + "'");
        }
        Class<?> target = info.matchByWhen(discriminator);
        if (target != null) return NodeMapper.convert(raw, target, false, context);
        if (info.fallbackNull) return null;
        throw new BindingException("oneOf discriminator has no matching mapping: value='" + discriminator + "'");
    }

    private static Object readByKey(StreamingReaderV3 reader, OneOfInfo info,
                                    RuntimeContext context) throws IOException {
        String[] pendingNames = null;
        Object[] pendingValues = null;
        int pendingSize = 0;
        reader.beginObject();
        String name;
        while ((name = reader.nextObjectName()) != null) {
            Object value = StreamingIOV3.readRawNode(reader);
            if (!info.key.equals(name)) {
                if (pendingNames == null) {
                    pendingNames = new String[4];
                    pendingValues = new Object[4];
                } else if (pendingSize == pendingNames.length) {
                    pendingNames = java.util.Arrays.copyOf(pendingNames, pendingSize << 1);
                    pendingValues = java.util.Arrays.copyOf(pendingValues, pendingSize << 1);
                }
                pendingNames[pendingSize] = name;
                pendingValues[pendingSize++] = value;
                continue;
            }
            if (value == null) {
                if (info.fallbackNull) {
                    skipRemaining(reader);
                    return null;
                }
                throw new BindingException("not found value for discriminator key '" + info.key + "'");
            }
            Class<?> target = info.matchByWhen(value);
            if (target == null) {
                if (info.fallbackNull) {
                    skipRemaining(reader);
                    return null;
                }
                throw new BindingException("oneOf discriminator has no matching mapping: value='" + value + "'");
            }
            return readRemaining(reader, target, pendingNames, pendingValues, pendingSize,
                    info.key, value, context);
        }
        reader.endObject();
        if (info.fallbackNull) return null;
        throw new BindingException("not found value for discriminator key '" + info.key + "'");
    }

    private static Object readRemaining(StreamingReaderV3 reader, Class<?> target,
                                        String[] pendingNames, Object[] pendingValues, int pendingSize,
                                        String discriminatorKey,
                                        Object discriminator, RuntimeContext context) throws IOException {
        TypeInfo type = TypeRegistry.registerTypeInfo(target);
        PojoInfo pojoInfo = type.pojoInfo;
        if (pojoInfo == null) throw new BindingException("oneOf target type '" + target + "' is not a POJO");
        CreatorState state = new CreatorState(pojoInfo.creatorInfo);
        for (int i = 0; i < pendingSize; i++) {
            acceptRaw(pendingNames[i], pendingValues[i], target, pojoInfo, state, context);
        }
        acceptRaw(discriminatorKey, discriminator, target, pojoInfo, state, context);
        String key;
        while ((key = reader.nextObjectName()) != null) {
            if (discriminatorKey.equals(key)) {
                throw new BindingException("duplicate oneOf discriminator key '" + discriminatorKey + "'");
            }
            acceptStream(key, reader, target, pojoInfo, state, context);
        }
        reader.endObject();
        return state.finish();
    }

    private static void acceptStream(String key, StreamingReaderV3 reader, Class<?> owner,
                                     PojoInfo info, CreatorState state,
                                     RuntimeContext context) throws IOException {
        CreatorInfo creator = info.creatorInfo;
        int arg = creator.getArgIndexOrAlias(key);
        if (arg >= 0) {
            Type argType = Types.resolveMemberType(owner, owner, creator.argTypes[arg]);
            Class<?> boxed = Types.rawBox(argType);
            ValueInfo codec = creator.argValueCodecs[arg];
            Object value = codec == null ? StreamingIOV3.readNode(reader, argType, boxed,
                    TypeRegistry.registerTypeInfo(boxed), context)
                    : StreamingIOV3.readValueWithCodec(reader, argType, boxed, codec, context);
            state.acceptCtorArg(arg, value);
            return;
        }
        FieldInfo field = info.aliasProperties != null ? info.aliasProperties.get(key) : info.properties.get(key);
        if (field != null) {
            if (state.isCreated() && !field.hasSetter()) {
                reader.skipValue();
                return;
            }
            Object value = StreamingIOV3.readFieldValue(reader, field, owner, owner, context);
            accept(state, field, value);
        } else if (info.isJojo && info.readDynamic) {
            state.acceptDynamic(key, StreamingIOV3.readRawNode(reader));
        } else reader.skipValue();
    }

    private static void acceptRaw(String key, Object value, Class<?> owner, PojoInfo info,
                                  CreatorState state, RuntimeContext context) {
        CreatorInfo creator = info.creatorInfo;
        int arg = creator.getArgIndexOrAlias(key);
        if (arg >= 0) {
            Type type = Types.resolveMemberType(owner, owner, creator.argTypes[arg]);
            ValueInfo codec = creator.argValueCodecs[arg];
            state.acceptCtorArg(arg, codec == null ? NodeMapper.convert(value, type, false, context)
                    : codec.rawToValue(value));
            return;
        }
        FieldInfo field = info.aliasProperties != null ? info.aliasProperties.get(key) : info.properties.get(key);
        if (field != null) {
            Type type = Types.resolveMemberType(owner, owner, field.type);
            accept(state, field, field.valueInfo == null ? NodeMapper.convert(value, type, false, context)
                    : field.valueInfo.rawToValue(value));
        } else if (info.isJojo && info.readDynamic) state.acceptDynamic(key, value);
    }

    private static void accept(CreatorState state, FieldInfo field, Object value) {
        if (state.isCreated()) field.invokeSetterIfPresent(state.pojo(), value);
        else state.bufferProperty(field, value);
    }

    private static void skipRemaining(StreamingReaderV3 reader) throws IOException {
        while (reader.nextObjectName() != null) reader.skipValue();
        reader.endObject();
    }

    private static JsonType jsonType(StreamingReaderV3.Token token) {
        switch (token) {
            case START_OBJECT: return JsonType.OBJECT;
            case START_ARRAY: return JsonType.ARRAY;
            case STRING: return JsonType.STRING;
            case NUMBER: return JsonType.NUMBER;
            case BOOLEAN: return JsonType.BOOLEAN;
            case NULL: return JsonType.NULL;
            default: return JsonType.UNKNOWN;
        }
    }
}
