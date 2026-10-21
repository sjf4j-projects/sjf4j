package org.sjf4j.binding;

import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.NodeMapper;
import org.sjf4j.meta.CreatorInfo;
import org.sjf4j.meta.CreatorState;
import org.sjf4j.meta.PropertyInfo;
import org.sjf4j.value.ValueInfo;
import org.sjf4j.meta.OneOfInfo;
import org.sjf4j.meta.PojoInfo;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.util.Types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OneOfIO {

    private static final int INITIAL_PENDING_CAPACITY = 4;

    /**
     * Reads one polymorphic node from the current stream position.
     * The caller owns document boundaries and reader lifecycle.
     */
    public static Object readOneOf(StreamingReader reader, OneOfInfo oneOfInfo,
                                   RuntimeContext context) throws IOException {
        if (reader.peekToken() == StreamingReader.Token.NULL) {
            reader.nextIfNull();
            return null;
        }
        return oneOfInfo.hasDiscriminator
                ? readOneOfByDtor(reader, oneOfInfo, context)
                : readOneOfByJsonType(reader, oneOfInfo, context);
    }

    static Object readOneOfByJsonType(StreamingReader reader, OneOfInfo oneOfInfo,
                                      RuntimeContext context) throws IOException {
        JsonType jsonType = reader.peekToken().jsonType();
        Class<?> targetClazz = oneOfInfo.matchByJsonType(jsonType);
        if (targetClazz != null) {
            return StreamingIO.readNode(reader, targetClazz, context);
        }

        if (oneOfInfo.fallbackNull) {
            reader.skipNode();
            return null;
        }

        throw new BindingException("oneOf mapping does not support jsonType=" + jsonType +
                " for type '" + oneOfInfo.clazz.getName() + "'");
    }


    static Object readOneOfByDtor(StreamingReader reader, OneOfInfo oneOfInfo,
                                  RuntimeContext context) throws IOException {
        if (oneOfInfo.scope != OneOf.Scope.CURRENT) {
            throw new BindingException("oneOf discriminator scope must be CURRENT here, but was " + oneOfInfo.scope);
        }

        final boolean fallbackNull = oneOfInfo.fallbackNull;
        if (reader.peekToken().jsonType() != JsonType.OBJECT) {
            if (fallbackNull) {
                reader.skipNode();
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
                                          RuntimeContext context) throws IOException {
        return _convertRawOneOf(reader.readRaw(), oneOfInfo, context);
    }


    private static Object _readByDtorKey(StreamingReader reader, OneOfInfo oneOfInfo,
                                         RuntimeContext context) throws IOException {
        reader.startObject();
        return _readByDtorKeyContent(reader, oneOfInfo, context, null, null);
    }

    /**
     * Continues a CURRENT/key OneOf object after the generated reader has
     * consumed its first non-discriminator member. The reader is inside the
     * object; this method never consumes another START_OBJECT.
     */
    public static Object readOneOfAfterFirstField(StreamingReader reader, OneOfInfo oneOfInfo,
                                                 RuntimeContext context, String name, Object value)
            throws IOException {
        return _readByDtorKeyContent(reader, oneOfInfo, context, name, value);
    }

    private static Object _readByDtorKeyContent(StreamingReader reader, OneOfInfo oneOfInfo,
                                                RuntimeContext context, String firstName, Object firstValue)
            throws IOException {
        final boolean fallbackNull = oneOfInfo.fallbackNull;
        final String discriminatorKey = oneOfInfo.key;
        String[] pendingNames = null;
        Object[] pendingValues = null;
        int pendingSize = 0;
        if (firstName != null) {
            pendingNames = new String[INITIAL_PENDING_CAPACITY];
            pendingValues = new Object[INITIAL_PENDING_CAPACITY];
            pendingNames[0] = firstName;
            pendingValues[0] = firstValue;
            pendingSize = 1;
        }

        while (!reader.nextIfObjectEnd()) {
            String name = reader.nextName();
            if (discriminatorKey.equals(name)) {
                Object discriminatorValue = reader.readRaw();
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

            Object value = reader.readRaw();
            if (pendingNames == null) {
                pendingNames = new String[INITIAL_PENDING_CAPACITY];
                pendingValues = new Object[INITIAL_PENDING_CAPACITY];
            } else if (pendingSize == pendingNames.length) {
                int newSize = pendingSize << 1;
                pendingNames = Arrays.copyOf(pendingNames, newSize);
                pendingValues = Arrays.copyOf(pendingValues, newSize);
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
                                               RuntimeContext context) throws IOException {

        TypeInfo ti = TypeRegistry.registerTypeInfo(targetClazz);
        PojoInfo pi = ti.pojoInfo;
        if (pi == null) {
            // Containers and other object-shaped targets cannot be incrementally
            // populated as a POJO. Materialize only this uncommon subtype.
            Map<String, Object> raw = new LinkedHashMap<>();
            for (int i = 0; i < pendingSize; i++) {
                raw.put(pendingNames[i], pendingValues[i]);
            }
            raw.put(discriminatorKey, discriminatorValue);
            while (!reader.nextIfObjectEnd()) {
                raw.put(reader.nextName(), reader.readRaw());
            }
            return NodeMapper.convert(raw, targetClazz, false, context);
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
                ValueInfo codec = ci.argValueCodecs[argIdx];
                if (codec != null) {
                    value = StreamingIO.readValueCodec(reader, codec, context);
                } else {
                    value = StreamingIO.readNode(reader, argType, argBoxed, argTi, context);
                }
                state.acceptCtorArg(argIdx, value);
                continue;
            }

            PropertyInfo fi = pi.propertyLookup.get(key);
            if (fi != null && fi.reader != null) {
                if (state.isCreated()) {
                    fi.reader.read(reader, state.pojo(), targetClazz, targetClazz, context);
                } else {
                    Object value = StreamingIO.readProperty(reader, fi, targetClazz, targetClazz, context);
                    state.bufferProperty(fi, value);
                }
                continue;
            }

            if (pi.isJojo && pi.readDynamic) {
                Object value = reader.readRaw();
                state.acceptDynamic(key, value);
                continue;
            }

            reader.skipNode();
        }

        return state.finish();
    }


    private static void _skipRemainingObject(StreamingReader reader) throws IOException {
        while (!reader.nextIfObjectEnd()) {
            reader.nextName();
            reader.skipNode();
        }
    }


    private static void _acceptRawField(String key, Object rawValue, Class<?> ownerClazz,
                                        PojoInfo pi, CreatorState state, RuntimeContext context) {

        CreatorInfo ci = pi.creatorInfo;
        int argIdx = ci.getArgIndexOrAlias(key);
        if (argIdx >= 0) {
            Type argType = Types.resolveMemberType(ownerClazz, ownerClazz, ci.argTypes[argIdx]);
            ValueInfo codec = ci.argValueCodecs[argIdx];
            Object value = codec != null
                    ? codec.rawToValue(rawValue)
                    : NodeMapper.convert(rawValue, argType, false, context);
            state.acceptCtorArg(argIdx, value);
            return;
        }

        PropertyInfo fi = pi.propertyLookup.get(key);
        if (fi != null && fi.writable) {
            Type argType = Types.resolveMemberType(ownerClazz, ownerClazz, fi.type);
            Object value = fi.oneOfInfo != null
                    ? _convertRawOneOf(rawValue, fi.oneOfInfo, context)
                    : fi.valueInfo != null
                            ? fi.valueInfo.rawToValue(rawValue)
                            : NodeMapper.convert(rawValue, argType, false, context);
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


    /** Converts an already-buffered OneOf node using its explicit member metadata. */
    private static Object _convertRawOneOf(Object raw, OneOfInfo info, RuntimeContext context) {
        if (raw == null) return null;

        Class<?> target;
        JsonType shape = JsonType.of(raw);
        if (!info.hasDiscriminator) {
            target = info.matchByJsonType(shape);
        } else {
            if (info.scope != OneOf.Scope.CURRENT) {
                throw new BindingException("oneOf discriminator scope must be CURRENT here, but was " + info.scope);
            }
            if (shape != JsonType.OBJECT) {
                if (info.fallbackNull) return null;
                throw new BindingException("node must be an object, when OneOf has a CURRENT discriminator");
            }
            Object discriminator = info.keyDiscriminator
                    ? Nodes.getInObject(raw, info.key)
                    : info.compiledPath.getNode(raw);
            if (discriminator == null) {
                if (info.fallbackNull) return null;
                throw new BindingException("not found value for discriminator "
                        + (info.keyDiscriminator ? "key '" + info.key + "'" : "path '" + info.path + "'"));
            }
            target = info.matchByWhen(discriminator);
        }

        if (target != null) return NodeMapper.convert(raw, target, false, context);
        if (info.fallbackNull) return null;
        throw new BindingException("oneOf mapping has no matching target for type '" + info.clazz.getName() + "'");
    }

}
