package org.sjf4j.binding;

import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.NodeMapper;
import org.sjf4j.meta.CreatorInfo;
import org.sjf4j.meta.LazyTypeInfo;
import org.sjf4j.meta.CreatorState;
import org.sjf4j.meta.PropertyInfo;
import org.sjf4j.value.ValueInfo;
import org.sjf4j.meta.OneOfInfo;
import org.sjf4j.meta.PojoInfo;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.util.Numbers;
import org.sjf4j.util.Types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;

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
        LazyTypeInfo target = oneOfInfo.lazyTypeInfoByJsonType(jsonType);
        if (target != null) {
            return StreamingIO.readNode(reader, target.clazz, target.clazz, target.get(), context);
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
        final boolean fallbackNull = oneOfInfo.fallbackNull;
        final String discriminatorKey = oneOfInfo.discriminatorKey;
        String[] pendingNames = null;
        Object[] pendingValues = null;
        int pendingSize = 0;

        NameMatcher matcher = _cacheDiscriminatorMatcher(reader, oneOfInfo);
        reader.startObject();
        int index;
        while ((index = reader.nextNameMatch(matcher, 0)) != NameMatcher.OBJECT_END) {
            if (index == 0) {
                Object discriminatorValue = reader.readRaw();
                if (discriminatorValue == null) {
                    if (fallbackNull) {
                        _skipRemainingObject(reader);
                        return null;
                    }
                    throw new BindingException("not found value for discriminator key '" + discriminatorKey + "'");
                }

                LazyTypeInfo target = oneOfInfo.lazyTypeInfoByWhen(discriminatorValue);
                if (target == null) {
                    if (fallbackNull) {
                        _skipRemainingObject(reader);
                        return null;
                    }
                    throw new BindingException("oneOf discriminator has no matching mapping: value='" + discriminatorValue + "'");
                }

                return _readPojoRemaining(reader, target, pendingNames, pendingValues, pendingSize,
                        discriminatorKey, discriminatorValue, context);
            }

            String name = reader.currentName();
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


    private static Object _readPojoRemaining(StreamingReader reader, LazyTypeInfo target,
                                             String[] pendingNames, Object[] pendingValues, int pendingSize,
                                             String discriminatorKey, Object discriminatorValue,
                                             RuntimeContext context) throws IOException {

        Class<?> targetClazz = target.clazz;
        PojoInfo pi = target.requirePojoInfo();
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

        // Creator-bound types already have an index-aligned matcher. Let the
        // backend recognize field names directly, rather than allocating names
        // and looking them up again in CreatorInfo and propertyLookup.
        if (pi.creatorMatchNames != null) {
            NameMatcher matcher = StreamingIO.cacheCreatorNameMatcher(reader, pi);
            int discriminatorIndex = matcher.fallback(discriminatorKey);
            int expectedIndex = 0;
            int index;
            while ((index = reader.nextNameMatch(matcher, expectedIndex)) != NameMatcher.OBJECT_END) {
                if (index == discriminatorIndex
                        && ((index >= 0 && discriminatorKey.equals(matcher.name(index)))
                        || discriminatorKey.equals(reader.currentName()))) {
                    throw new BindingException("duplicate oneOf discriminator key '" + discriminatorKey + "'");
                }

                if (index >= 0) {
                    expectedIndex = index + 1;
                    int argIdx = pi.creatorMatchArgs[index];
                    if (argIdx >= 0) {
                        OneOfInfo argOneOf = ci.argOneOfInfos[argIdx];
                        ValueInfo codec = ci.argValueCodecs[argIdx];
                        Object value;
                        if (argOneOf != null) {
                            value = readOneOf(reader, argOneOf, context);
                        } else if (codec != null) {
                            value = StreamingIO.readValueCodec(reader, codec, context);
                        } else {
                            LazyTypeInfo lazy = ci.argTypeInfos[argIdx];
                            if (lazy != null) {
                                value = StreamingIO.readNode(reader, lazy.clazz, Types.box(lazy.clazz),
                                        lazy.get(), context);
                            } else {
                                Type argType = ci.argGenericDependent[argIdx]
                                        ? Types.resolveMemberType(targetClazz, targetClazz, ci.argTypes[argIdx])
                                        : ci.argTypes[argIdx];
                                Class<?> argBoxed = Types.rawBox(argType);
                                TypeInfo argTi = TypeRegistry.registerTypeInfo(argBoxed);
                                value = StreamingIO.readNode(reader, argType, argBoxed, argTi, context);
                            }
                        }
                        state.acceptCtorArg(argIdx, value);
                        continue;
                    }

                    PropertyInfo fi = pi.creatorMatchProperties[index];
                    if (fi != null && fi.reader != null) {
                        if (state.isCreated()) {
                            fi.reader.read(reader, state.pojo(), targetClazz, targetClazz, context);
                        } else {
                            Object value = StreamingIO.readProperty(reader, fi, targetClazz, targetClazz, context);
                            state.bufferProperty(fi, value);
                        }
                        continue;
                    }
                }

                if (pi.isJojo && pi.readDynamic) {
                    state.acceptDynamic(reader.currentName(), reader.readRaw());
                } else {
                    reader.skipNode();
                }
            }
            return state.finish();
        }

        // No-argument POJOs can use the same prepared property matcher as
        // ordinary streaming binding, without creator lookups per field.
        NameMatcher matcher = StreamingIO.cacheNameMatcher(reader, pi);
        int discriminatorIndex = matcher.fallback(discriminatorKey);
        int expectedIndex = 0;
        int index;
        while ((index = reader.nextNameMatch(matcher, expectedIndex)) != NameMatcher.OBJECT_END) {
            if (index == discriminatorIndex
                        && ((index >= 0 && discriminatorKey.equals(matcher.name(index)))
                        || discriminatorKey.equals(reader.currentName()))) {
                throw new BindingException("duplicate oneOf discriminator key '" + discriminatorKey + "'");
            }

            if (index >= 0) {
                pi.propertyReaders[index].read(reader, state.pojo(), targetClazz, targetClazz, context);
                expectedIndex = index + 1;
            } else if (pi.isJojo && pi.readDynamic) {
                String key = reader.currentName();
                if (pi.propertyLookup.containsKey(key)) {
                    reader.skipNode();
                } else {
                    state.acceptDynamic(key, reader.readRaw());
                }
            } else {
                reader.skipNode();
            }
        }

        return state.finish();
    }


    private static NameMatcher _cacheDiscriminatorMatcher(StreamingReader reader, OneOfInfo info) {
        BackendCache cache = info.backendCache(reader.backend);
        NameMatcher matcher = cache.nameMatcher;
        if (matcher == null) {
            matcher = reader.createNameMatcher(new String[]{info.discriminatorKey});
            cache.nameMatcher = matcher;
        }
        return matcher;
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
            OneOfInfo argOneOf = ci.argOneOfInfos[argIdx];
            ValueInfo codec = ci.argValueCodecs[argIdx];
            Object value;
            if (argOneOf != null) {
                value = _convertRawOneOf(rawValue, argOneOf, context);
            } else if (codec != null) {
                if (rawValue instanceof Number && Number.class.isAssignableFrom(codec.rawClazz)) {
                    rawValue = Numbers.to((Number) rawValue, codec.rawClazz);
                }
                value = codec.rawToValue(rawValue);
            } else {
                Type argType = ci.argGenericDependent[argIdx]
                        ? Types.resolveMemberType(ownerClazz, ownerClazz, ci.argTypes[argIdx])
                        : ci.argTypes[argIdx];
                if (rawValue instanceof Number) {
                    Class<?> boxed = Types.rawBox(argType);
                    TypeInfo ti = TypeRegistry.registerTypeInfo(boxed);
                    if (ti.valueSet != null) {
                        ValueInfo selected = ti.valueSet.selectForRead(JsonType.NUMBER, context.valueFormat(boxed));
                        if (Number.class.isAssignableFrom(selected.rawClazz)) {
                            rawValue = Numbers.to((Number) rawValue, selected.rawClazz);
                        }
                    }
                }
                value = NodeMapper.convert(rawValue, argType, false, context);
            }
            state.acceptCtorArg(argIdx, value);
            return;
        }

        PropertyInfo fi = pi.propertyLookup.get(key);
        if (fi != null && fi.writable) {
            Object value;
            if (fi.oneOfInfo != null) {
                value = _convertRawOneOf(rawValue, fi.oneOfInfo, context);
            } else if (fi.valueInfo != null) {
                ValueInfo codec = fi.valueInfo;
                if (rawValue instanceof Number && Number.class.isAssignableFrom(codec.rawClazz)) {
                    rawValue = Numbers.to((Number) rawValue, codec.rawClazz);
                }
                value = codec.rawToValue(rawValue);
            } else {
                Type argType = Types.resolveMemberType(ownerClazz, ownerClazz, fi.type);
                if (rawValue instanceof Number) {
                    Class<?> boxed = Types.rawBox(argType);
                    TypeInfo ti = TypeRegistry.registerTypeInfo(boxed);
                    if (ti.valueSet != null) {
                        ValueInfo selected = ti.valueSet.selectForRead(JsonType.NUMBER, context.valueFormat(boxed));
                        if (Number.class.isAssignableFrom(selected.rawClazz)) {
                            rawValue = Numbers.to((Number) rawValue, selected.rawClazz);
                        }
                    }
                }
                value = NodeMapper.convert(rawValue, argType, false, context);
            }
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

        LazyTypeInfo target;
        JsonType shape = JsonType.of(raw);
        if (!info.hasDiscriminator) {
            target = info.lazyTypeInfoByJsonType(shape);
        } else {
            if (info.scope != OneOf.Scope.CURRENT) {
                throw new BindingException("oneOf discriminator scope must be CURRENT here, but was " + info.scope);
            }
            if (shape != JsonType.OBJECT) {
                if (info.fallbackNull) return null;
                throw new BindingException("node must be an object, when OneOf has a CURRENT discriminator");
            }
            Object discriminator = info.keyDiscriminator
                    ? Nodes.getInObject(raw, info.discriminatorKey)
                    : info.compiledPath.getNode(raw);
            if (discriminator == null) {
                if (info.fallbackNull) return null;
                throw new BindingException("not found value for discriminator "
                        + (info.keyDiscriminator ? "key '" + info.discriminatorKey + "'" : "path '" + info.discriminatorPath + "'"));
            }
            target = info.lazyTypeInfoByWhen(discriminator);
        }

        if (target != null) return NodeMapper.convert(raw, target.clazz, false, context);
        if (info.fallbackNull) return null;
        throw new BindingException("oneOf mapping has no matching target for type '" + info.clazz.getName() + "'");
    }

}
