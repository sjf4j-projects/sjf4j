package org.sjf4j.binding;

import org.sjf4j.JsonType;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.CreatorInfo;
import org.sjf4j.node.CreatorState;
import org.sjf4j.node.FieldInfo;
import org.sjf4j.value.NodeValueInfo;
import org.sjf4j.node.OneOfInfo;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeInfo;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

public final class OneOfIO {

    private static final int INITIAL_PENDING_CAPACITY = 4;

    static Object readOneOf(StreamingReader reader, OneOfInfo oneOfInfo,
                            StreamingContext context) throws IOException {
        return oneOfInfo.hasDiscriminator
                ? readOneOfByDtor(reader, oneOfInfo, context)
                : readOneOfByJsonType(reader, oneOfInfo, context);
    }

    static Object readOneOfByJsonType(StreamingReader reader, OneOfInfo oneOfInfo,
                                      StreamingContext context) throws IOException {
        JsonType jsonType = reader.peekToken().jsonType();

        Class<?> targetClazz = oneOfInfo.matchByJsonType(jsonType);
        if (targetClazz != null) {
            return StreamingIO.readNode(reader, targetClazz, context);
        }

        if (oneOfInfo.fallbackNull) {
            reader.skipNext();
            return null;
        }
        throw new BindingException("oneOf mapping does not support jsonType=" + jsonType +
                " for type '" + oneOfInfo.clazz.getName() + "'");
    }


    static Object readOneOfByDtor(StreamingReader reader, OneOfInfo oneOfInfo,
                                  StreamingContext context) throws IOException {
        if (oneOfInfo.scope != OneOf.Scope.CURRENT) {
            throw new BindingException("oneOf discriminator scope must be CURRENT here, but was " + oneOfInfo.scope);
        }

        final boolean fallbackNull = oneOfInfo.fallbackNull;
        if (reader.peekToken().jsonType() != JsonType.OBJECT) {
            if (fallbackNull) {
                reader.skipNext();
                return null;
            }
            throw new BindingException("node must be an object, when OneOf has a CURRENT discriminator");
        }

        if (oneOfInfo.keyDiscriminator) {
            return _readByDtorKey(reader, oneOfInfo, context);
        }
        return _readByDtorPath(reader, oneOfInfo, context);
    }


    private static Object _readByDtorPath(StreamingReader reader, OneOfInfo oneOfInfo,
                                         StreamingContext context) throws IOException {
        Map<String, Object> rawMap = StreamingIO.readRawObject(reader);

        Object discriminatorValue = oneOfInfo.compiledPath.getNode(rawMap);
        if (discriminatorValue == null) {
            if (oneOfInfo.fallbackNull) return null;
            throw new BindingException("not found value for discriminator path '" + oneOfInfo.path + "'");
        }

        Class<?> targetClazz = oneOfInfo.matchByWhen(discriminatorValue);
        if (targetClazz == null) {
            if (oneOfInfo.fallbackNull) return null;
            throw new BindingException("oneOf discriminator has no matching mapping: value='" + discriminatorValue + "'");
        }
        return context.nodeBinder.readNode(rawMap, targetClazz);
    }


    private static Object _readByDtorKey(StreamingReader reader, OneOfInfo oneOfInfo,
                                         StreamingContext context) throws IOException {

        final boolean fallbackNull = oneOfInfo.fallbackNull;
        final String discriminatorKey = oneOfInfo.key;
        String[] pendingNames = null;
        Object[] pendingValues = null;
        int pendingSize = 0;

        reader.startObject();
        while (!reader.nextIfObjectEnd()) {
            String name = reader.nextName();

            if (discriminatorKey.equals(name)) {
                Object discriminatorValue = StreamingIO.readRawNode(reader);
                if (discriminatorValue == null) {
                    if (fallbackNull) {
                        _skipRemainingObject(reader);
                        return null;
                    }
                    throw new BindingException("not found value for discriminator key '" + discriminatorKey + "'");
                }

                Class<?> targetClazz = oneOfInfo.matchByWhen(discriminatorValue);
                if (targetClazz == null) {
                    if (fallbackNull) {
                        _skipRemainingObject(reader);
                        return null;
                    }
                    throw new BindingException("oneOf discriminator has no matching mapping: value='" + discriminatorValue + "'");
                }

                return _readRemainingObject(reader, targetClazz, pendingNames, pendingValues, pendingSize,
                        discriminatorKey, discriminatorValue, context);
            }

            Object value = StreamingIO.readRawNode(reader);
            if (pendingNames == null) {
                pendingNames = new String[INITIAL_PENDING_CAPACITY];
                pendingValues = new Object[INITIAL_PENDING_CAPACITY];
            } else if (pendingSize == pendingNames.length) {
                int newSize = pendingSize << 1;
                pendingNames = java.util.Arrays.copyOf(pendingNames, newSize);
                pendingValues = java.util.Arrays.copyOf(pendingValues, newSize);
            }

            pendingNames[pendingSize] = name;
            pendingValues[pendingSize] = value;
            pendingSize++;
        }// while

        if (fallbackNull) {
            return null;
        }
        throw new BindingException("not found value for discriminator key '" + discriminatorKey + "'");
    }



    private static Object _readRemainingObject(StreamingReader reader, Class<?> targetClazz,
                                               String[] pendingNames, Object[] pendingValues, int pendingSize,
                                               String discriminatorKey, Object discriminatorValue,
                                               StreamingContext context) throws IOException {

        TypeInfo ti = TypeRegistry.registerTypeInfo(targetClazz);
        PojoInfo pi = ti.pojoInfo;
        if (pi == null) {
            throw new BindingException("oneOf target type '" + targetClazz + "' is not a POJO");
        }

        CreatorInfo ci = pi.creatorInfo;
        CreatorState state = new CreatorState(ci);

        /*
         * {
         *   "a": 1,             <- pending
         *   "b": 2,             <- pending
         *   "type": "dog",      <- pending key
         *   "name": "...",      <- here we go
         *   ...
         * }
         */

        for (int i = 0; i < pendingSize; i++) {
            _acceptRawField(pendingNames[i], pendingValues[i], targetClazz, pi, state, context);
        }
        _acceptRawField(discriminatorKey, discriminatorValue, targetClazz, pi, state, context);

        while (!reader.nextIfObjectEnd()) {

            String key = reader.nextName();
            if (discriminatorKey.equals(key)) {
                throw new BindingException("duplicate oneOf discriminator key '" + discriminatorKey + "'");
            }

            int argIdx = ci.getArgIndexOrAlias(key);
            if (argIdx >= 0) {
                Type argType = Types.resolveMemberType(targetClazz, targetClazz, ci.argTypes[argIdx]);
                Class<?> argBoxed = Types.rawBox(argType);
                TypeInfo argTi = TypeRegistry.registerTypeInfo(argBoxed);

                Object value;
                NodeValueInfo codec = ci.argValueCodecs[argIdx];
                if (codec != null) {
                    value = StreamingIO.readValueWithCodec(reader, argType, argBoxed, codec, context);
                } else {
                    value = StreamingIO.readNode(reader, argType, argBoxed, argTi, context);
                }
                state.acceptCtorArg(argIdx, value);
                continue;
            }

            FieldInfo fi = pi.aliasProperties != null ? pi.aliasProperties.get(key) : pi.properties.get(key);
            if (fi != null) {
                if (state.isCreated()) {
                    fi.binder.bind(reader, state.pojo(), targetClazz, targetClazz, context);
                } else {
                    Object value = StreamingIO.readFieldValue(reader, fi, targetClazz, targetClazz, context);
                    state.bufferProperty(fi, value);
                }
                continue;
            }

            if (pi.isJojo && pi.readDynamic) {
                Object value = StreamingIO.readRawNode(reader);
                state.acceptDynamic(key, value);
                continue;
            }

            reader.skipNext();
        }

        return state.finish();
    }


    private static void _skipRemainingObject(StreamingReader reader) throws IOException {
        while (!reader.nextIfObjectEnd()) {
            reader.nextName();
            reader.skipNext();
        }
    }


    private static void _acceptRawField(String key, Object rawValue, Class<?> ownerClazz,
                                        PojoInfo pi, CreatorState state, StreamingContext context) {

        CreatorInfo ci = pi.creatorInfo;
        int argIdx = ci.getArgIndexOrAlias(key);
        if (argIdx >= 0) {
            Type argType = Types.resolveMemberType(ownerClazz, ownerClazz, ci.argTypes[argIdx]);
            Object value = context.nodeBinder.readNode(rawValue, argType);
            state.acceptCtorArg(argIdx, value);
            return;
        }

        FieldInfo fi = pi.aliasProperties != null ? pi.aliasProperties.get(key) : pi.properties.get(key);
        if (fi != null) {
            Type argType = Types.resolveMemberType(ownerClazz, ownerClazz, fi.type);
            Object value = context.nodeBinder.readNode(rawValue, argType);
            if (state.isCreated()) {
                fi.invokeSetter(state.pojo(), value);
            } else {
                state.bufferProperty(fi, value);
            }
            return;
        }

        if (pi.isJojo && pi.readDynamic) {
            state.acceptDynamic(key, rawValue);
        }
    }


}
