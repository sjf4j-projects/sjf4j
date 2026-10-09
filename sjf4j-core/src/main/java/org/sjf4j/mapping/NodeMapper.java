package org.sjf4j.mapping;

import org.sjf4j.InternalAccess;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.node.CreatorInfo;
import org.sjf4j.node.CreatorState;
import org.sjf4j.node.PropertyInfo;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.OneOfInfo;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeInfo;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.node.Types;
import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;
import org.sjf4j.util.Strings;
import org.sjf4j.value.ValueInfo;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Runtime mapper for converting between OBNT node representations.
 *
 * <p>Supports structural conversion between POJOs, JOJOs, maps, lists,
 * arrays, sets, {@link JsonObject}, and {@link JsonArray}, including
 * {@link org.sjf4j.value.ValueCodec ValueCodec} and {@link OneOf} semantics.
 */
public final class NodeMapper {

    private NodeMapper() {
    }



    /**
     * Converts a node to the captured generic target type using the default
     * runtime context.
     */
    public static Object convert(Object node, Type type, boolean deepCopy) {
        return convert(node, type, deepCopy, RuntimeContext.EMPTY);
    }


    /**
     * Root conversion entry with deep-copy control.
     */
    public static Object convert(Object node, Type type, boolean deepCopy, RuntimeContext context) {
        Asserts.notNull(type, "type");
        Asserts.notNull(context, "context");
        try {
            Class<?> rawBox = Types.rawBox(type);
            return _convert(node, type, rawBox, null, deepCopy, PathSegment.Root.INSTANCE, context);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to convert node from '" + Types.name(node) + "' to '" + type + "'", e);
        }
    }

    /**
     * Converts a node to its raw JSON-compatible representation using the
     * supplied runtime context.
     *
     * <p>Object nodes become maps, array nodes become lists, and node values
     * are converted through their configured value codecs.
     */
    public static Object convertToRaw(Object node, RuntimeContext context) {
        Asserts.notNull(context, "context");
        return _convertToRaw(node, PathSegment.Root.INSTANCE, context);
    }


    /**
     * Internal conversion with path support.
     */
    @SuppressWarnings("unchecked")
    private static Object _convert(Object node, Type toType, Class<?> toBoxed,
                                   OneOfInfo oneOfInfo, boolean deepCopy, PathSegment ps, RuntimeContext context) {
        try {
            if (node == null) {
                if (oneOfInfo == null) {
                    TypeInfo ti = TypeRegistry.registerTypeInfo(toBoxed);
                    if (ti.valueInfos != null) {
                        String valueFormat = context.defaultValueFormat(toBoxed);
                        ValueInfo valueInfo = ti.requireValueInfo(valueFormat);
                        return valueInfo.rawToValue(null);
                    }
                }
                return null;
            }

            if (oneOfInfo != null) {
                return _convertOneOf(node, toBoxed, oneOfInfo, deepCopy, ps, context);
            }

            if (toBoxed == Object.class) {
                return deepCopy ? _deepCopy(node, toType, toBoxed, ps, context) : node;
            }

            // Compatible values can only be returned as-is when the target has no
            // generic structure to honor. Parameterized containers/POJOs still need
            // traversal so their declared element/member types are converted.
            if (toBoxed.isInstance(node) && !Types.hasGenericStructure(toType)) {
                // JSON-P native objects/arrays implement Map/List; an explicit
                // conversion to a plain Java container must not reuse the native tree.
                boolean externalContainer = (node instanceof Map || node instanceof List)
                        && TypeRegistry.registerTypeInfo(node.getClass()).externalNode != null
                        && TypeRegistry.registerTypeInfo(toBoxed).externalNode == null;
                if (!externalContainer) {
                    return deepCopy ? _deepCopy(node, toType, toBoxed, ps, context) : node;
                }
            }

            TypeInfo ti = TypeRegistry.registerTypeInfo(toBoxed);
            oneOfInfo = ti.oneOfInfo;
            if (oneOfInfo != null) {
                return _convertOneOf(node, toBoxed, oneOfInfo, deepCopy, ps, context);
            }

            if (ti.valueInfos != null) {
                ValueInfo target = ti.requireValueInfo(context.defaultValueFormat(toBoxed));
                return _convertValue(node, toBoxed, target, deepCopy, ps, context);
            }

            if (ti.externalNode != null) {
                return _convertToExternal(node, toBoxed, ti.externalNode, deepCopy, ps, context);
            }

            if (node instanceof String) {
                return _convertString(node.toString(), toBoxed, ps);
            }

            if (node instanceof Number) {
                if (Number.class.isAssignableFrom(toBoxed)) {
                    return Numbers.to((Number) node, toBoxed);
                }
                throw new BindingException("cannot convert node from '" +
                        Types.name(node) + "' to '" + toType + "'", ps);
            }

            if (node instanceof Boolean) {
                if (toBoxed == Boolean.class) {
                    return node;
                }
                throw new BindingException("cannot convert node from '" +
                        Types.name(node) + "' to '" + toType + "'", ps);
            }

            // External nodes precede Map/List: JSON-P implements both.
            TypeInfo sourceTi = TypeRegistry.registerTypeInfo(node.getClass());
            if (sourceTi.externalNode != null) {
                return _convertFromExternal(node, sourceTi.externalNode,
                        toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof Map) {
                return _convertFromMap((Map<String, Object>) node,
                        toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof List) {
                return _convertFromList((List<Object>) node,
                        toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof JsonObject) {
                return _convertFromJsonObject((JsonObject) node,
                        toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof JsonArray) {
                return _convertFromJsonArray((JsonArray) node,
                        toBoxed, toType, deepCopy, ps, context);
            }

            if (node.getClass().isArray()) {
                return _convertFromArray(node, toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof Set) {
                return _convertFromSet((Set<Object>) node,
                        toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof Character) {
                return _convertString(node.toString(), toBoxed, ps);
            }

            if (node instanceof Enum) {
                return _convertString(((Enum<?>) node).name(), toBoxed, ps);
            }

            PojoInfo sourceInfo = sourceTi.pojoInfo;
            if (sourceInfo != null) {
                return _convertFromPojo(node, sourceInfo,
                        toBoxed, toType, deepCopy, ps, context);
            }

            throw new BindingException("cannot convert node from '" +
                    Types.name(node) + "' to '" + toType + "'", ps);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("cannot convert node from '" +
                    Types.name(node) + "' to '" + toType + "'", ps, e);
        }
    }



    /**
     * Converts into a NodeValue through its declared raw representation.
     * A source NodeValue is encoded once, then decoded using the target codec.
     * Raw types must remain compatible; no coercion is performed at this boundary.
     */
    private static Object _convertValue(Object node, Class<?> toBoxed, ValueInfo target,
                                        boolean deepCopy, PathSegment ps, RuntimeContext context) {
        if (node == null) return target.rawToValue(null);
        if (toBoxed.isInstance(node)) {
            return deepCopy ? target.valueCopy(node) : node;
        }

        TypeInfo sourceInfo = TypeRegistry.registerTypeInfo(node.getClass());
        Object raw = node;
        if (sourceInfo.valueInfos != null) {
            ValueInfo source = sourceInfo.requireValueInfo(
                    context.defaultValueFormat(node.getClass()));
            raw = source.valueToRaw(node);
        } else if (sourceInfo.externalNode != null) {
            raw = _convertToRaw(node, ps, context);
        }
        return target.rawToValue(raw);
    }


    /**
     * Reads an external representation directly through the existing object
     * and indexed source engines, preserving creator/OneOf/generic handling.
     */
    private static Object _convertFromExternal(Object node, ExternalNode<Object> external,
                                               Class<?> toBoxed, Type type, boolean deepCopy,
                                               PathSegment ps, RuntimeContext context) {
        switch (external.jsonType(node)) {
            case NULL:
                return _convert(null, type, toBoxed, null, deepCopy, ps, context);
            case STRING:
                return _convert(external.toString(node), type, toBoxed, null, deepCopy, ps, context);
            case NUMBER:
                return _convert(external.toNumber(node), type, toBoxed, null, deepCopy, ps, context);
            case BOOLEAN:
                return _convert(external.toBoolean(node), type, toBoxed, null, deepCopy, ps, context);
            case OBJECT:
                return _convertFromObjectSource(new ObjectSource() {
                    @Override
                    public Iterable<Map.Entry<String, Object>> entries() {
                        return external.entrySetInObject(node);
                    }

                    @Override
                    public int size() {
                        return external.sizeInObject(node);
                    }

                    @Override
                    public boolean external() {
                        return true;
                    }
                }, "External object", toBoxed, type, deepCopy, ps, context);
            case ARRAY:
                return _convertFromIndexedSource(new IndexedSource() {
                    @Override
                    public int size() {
                        return external.sizeInArray(node);
                    }

                    @Override
                    public Object get(int i) {
                        return external.getInArray(node, i);
                    }

                    @Override
                    public boolean external() {
                        return true;
                    }
                }, "External array", toBoxed, type, deepCopy, ps, context);
            default:
                throw new BindingException("unsupported external node type '" + Types.name(node) + "'", ps);
        }
    }

    /**
     * Recursively constructs the target backend's native tree without
     * allocating intermediate Map/List trees or serializing JSON text.
     */
    @SuppressWarnings("unchecked")
    private static Object _convertToExternal(Object node, Class<?> toBoxed,
                                             ExternalNode<Object> target,
                                             boolean deepCopy, PathSegment ps, RuntimeContext context) {
        try {
            Object result;
            if (node == null) {
                result = target.createValueNode(null);
            } else if (target.nodeType().isInstance(node)) {
                // A compatible native subtree is safe to reuse for a non-copy
                // conversion, even if it is nested in a different Java source.
                result = deepCopy ? target.deepCopy(node) : node;
            } else if (node instanceof String || node instanceof Number || node instanceof Boolean) {
                result = target.createValueNode(node);
            } else if (node instanceof Character) {
                result = target.createValueNode(node.toString());
            } else if (node instanceof Enum) {
                result = target.createValueNode(((Enum<?>) node).name());
            } else {
                TypeInfo sourceTi = TypeRegistry.registerTypeInfo(node.getClass());
                if (sourceTi.valueInfos != null) {
                    ValueInfo vi = sourceTi.requireValueInfo(context.defaultValueFormat(node.getClass()));
                    return _requireExternalTarget(_convertToExternal(
                            vi.valueToRaw(node), target.nodeType(), target, deepCopy, ps, context), toBoxed, ps);
                }

                ExternalNode<Object> sourceExternal = sourceTi.externalNode;
                JsonType shape = sourceExternal != null ? sourceExternal.jsonType(node) : JsonType.UNKNOWN;

                if (sourceExternal != null && shape == JsonType.NULL) {
                    result = target.createValueNode(null);
                } else if (sourceExternal != null && shape == JsonType.STRING) {
                    result = target.createValueNode(sourceExternal.toString(node));
                } else if (sourceExternal != null && shape == JsonType.NUMBER) {
                    result = target.createValueNode(sourceExternal.toNumber(node));
                } else if (sourceExternal != null && shape == JsonType.BOOLEAN) {
                    result = target.createValueNode(sourceExternal.toBoolean(node));
                } else if (sourceExternal != null && shape == JsonType.OBJECT) {
                    Object out = target.createObjectNode(toBoxed);
                    for (Map.Entry<String, Object> entry : sourceExternal.entrySetInObject(node)) {
                        PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                        target.putInObject(out, entry.getKey(), _convertToExternal(
                                entry.getValue(), target.nodeType(), target, deepCopy, cps, context));
                    }
                    result = out;
                } else if (sourceExternal != null && shape == JsonType.ARRAY) {
                    Object out = target.createArrayNode(toBoxed);
                    int len = sourceExternal.sizeInArray(node);
                    for (int i = 0; i < len; i++) {
                        target.addInArray(out, _convertToExternal(sourceExternal.getInArray(node, i),
                                target.nodeType(), target, deepCopy, new PathSegment.Index(ps, i), context));
                    }
                    result = out;
                } else if (node instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) node;
                    Object out = target.createObjectNode(toBoxed);
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        target.putInObject(out, entry.getKey(), _convertToExternal(
                                entry.getValue(), target.nodeType(), target, deepCopy, new PathSegment.Name(ps, entry.getKey()), context));
                    }
                    result = out;
                } else if (node instanceof JsonObject && node.getClass() == JsonObject.class) {
                    JsonObject jo = (JsonObject) node;
                    Object out = target.createObjectNode(toBoxed);
                    for (Map.Entry<String, Object> entry : jo.entrySet()) {
                        target.putInObject(out, entry.getKey(), _convertToExternal(
                                entry.getValue(), target.nodeType(), target, deepCopy, new PathSegment.Name(ps, entry.getKey()), context));
                    }
                    result = out;
                } else if (node instanceof List || node instanceof JsonArray
                        || node.getClass().isArray() || node instanceof Set) {
                    Object out = target.createArrayNode(toBoxed);
                    if (node instanceof List) {
                        List<?> list = (List<?>) node;
                        for (int i = 0; i < list.size(); i++) {
                            target.addInArray(out, _convertToExternal(
                                    list.get(i), target.nodeType(), target, deepCopy, new PathSegment.Index(ps, i), context));
                        }
                    } else if (node instanceof JsonArray) {
                        JsonArray array = (JsonArray) node;
                        for (int i = 0; i < array.size(); i++) {
                            target.addInArray(out, _convertToExternal(
                                    array.getNode(i), target.nodeType(), target, deepCopy, new PathSegment.Index(ps, i), context));
                        }
                    } else if (node.getClass().isArray()) {
                        int len = Array.getLength(node);
                        for (int i = 0; i < len; i++) {
                            target.addInArray(out, _convertToExternal(
                                    Array.get(node, i), target.nodeType(), target, deepCopy, new PathSegment.Index(ps, i), context));
                        }
                    } else {
                        int i = 0;
                        for (Object item : (Set<?>) node) {
                            target.addInArray(out, _convertToExternal(
                                    item, target.nodeType(), target, deepCopy, new PathSegment.Index(ps, i++), context));
                        }
                    }
                    result = out;
                } else if (sourceTi.pojoInfo != null) {
                    PojoInfo pi = sourceTi.pojoInfo;
                    Object out = target.createObjectNode(toBoxed);
                    for (PropertyInfo property : pi.readableProperties) {
                        Object value = property.invokeGetter(node);
                        if (value != null && property.valueInfo != null) {
                            value = property.valueInfo.valueToRaw(value);
                        }
                        target.putInObject(out, property.name, _convertToExternal(
                                value, target.nodeType(), target, deepCopy, new PathSegment.Name(ps, property.name), context));
                    }
                    if (pi.isJojo && pi.writeDynamic) {
                        Map<String, Object> dynamic = InternalAccess.dynamicProperties((JsonObject) node);
                        for (Map.Entry<String, Object> entry : dynamic.entrySet()) {
                            target.putInObject(out, entry.getKey(), _convertToExternal(
                                    entry.getValue(), target.nodeType(), target, deepCopy, new PathSegment.Name(ps, entry.getKey()), context));
                        }
                    }
                    result = out;
                } else {
                    throw new BindingException("unsupported node type '" + Types.name(node) + "'", ps);
                }
            }
            return _requireExternalTarget(result, toBoxed, ps);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("cannot convert node from '" + Types.name(node)
                    + "' to external type '" + toBoxed.getName() + "'", ps, e);
        }
    }

    private static Object _requireExternalTarget(Object result, Class<?> toBoxed, PathSegment ps) {
        if (!toBoxed.isInstance(result)) {
            throw new BindingException("native node does not match target type '"
                    + toBoxed.getName() + "'", ps);
        }
        return result;
    }

    private static Object _convertOneOf(Object node, Class<?> toBoxed,
                                        OneOfInfo oneOfInfo, boolean deepCopy, PathSegment ps, RuntimeContext context) {
        Class<?> targetClazz;

        if (oneOfInfo.hasDiscriminator) {
            if (oneOfInfo.scope != OneOf.Scope.CURRENT) {
                throw new BindingException("oneOf scope '" +
                        oneOfInfo.scope + "' is not supported", ps);
            }

            if (!JsonType.of(node).isObject()) {
                if (oneOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
                    return null;
                }
                throw new BindingException(
                        "node must be a JSON object, when OneOf has a CURRENT discriminator", ps);
            }

            Object discriminatorValue;
            if (!oneOfInfo.key.isEmpty()) {
                discriminatorValue = Nodes.getInObject(node, oneOfInfo.key);
            } else if (!oneOfInfo.path.isEmpty()) {
                discriminatorValue = oneOfInfo.compiledPath.getNode(node);
            } else {
                discriminatorValue = null;
            }

            // Native external discriminator values must be normalized before
            // matching Java @OneOf case values.
            if (discriminatorValue != null) {
                TypeInfo discriminatorTi = TypeRegistry.registerTypeInfo(discriminatorValue.getClass());
                if (discriminatorTi.externalNode != null) {
                    discriminatorValue = _convertToRaw(discriminatorValue, ps, context);
                }
            }

            if (discriminatorValue == null) {
                if (oneOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
                    return null;
                }
                throw new BindingException(
                        "not found value for discriminator key '" + oneOfInfo.key + "'", ps);
            }

            targetClazz = oneOfInfo.matchByWhen(discriminatorValue);
            if (targetClazz == null) {
                if (oneOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
                    return null;
                }
                throw new BindingException(
                        "oneOf discriminator has no matching mapping: value='" +
                                discriminatorValue + "'", ps);
            }
        } else {
            JsonType jsonType = JsonType.of(node);
            targetClazz = oneOfInfo.matchByJsonType(jsonType);
            if (targetClazz == null) {
                if (oneOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
                    return null;
                }
                throw new BindingException(
                        "oneOf mapping does not support jsonType=" + jsonType +
                                " for type '" + toBoxed.getName() + "'", ps);
            }
        }

        return _convert(node, targetClazz, Types.rawBox(targetClazz),
                null, deepCopy, ps, context);
    }

    /**
     * Internal deep structural copy with path support.
     */
    @SuppressWarnings("unchecked")
    private static Object _deepCopy(Object node, Type toType, Class<?> toBoxed, PathSegment ps,
                                    RuntimeContext context) {
        try {
            if (node == null) return null;

            /*
             * Target representation differs from the runtime value.
             *
             * In that case this is no longer a pure copy: let conversion
             * establish the requested target representation first.
             */
            if (toBoxed != Object.class && !toBoxed.isInstance(node)) {
                return _convert(node, toType, toBoxed, null, true, ps, context);
            }

            Class<?> nodeClazz = node.getClass();
            /*
             * Immutable JSON scalar values.
             */
            if (nodeClazz == String.class || nodeClazz == Boolean.class ||
                    nodeClazz == Integer.class || nodeClazz == Long.class ||
                    nodeClazz == Double.class || nodeClazz == Float.class ||
                    nodeClazz == Short.class || nodeClazz == Byte.class) {
                return node;
            }

            TypeInfo ti = TypeRegistry.registerTypeInfo(nodeClazz);

            /*
             * External node.
             */
            if (ti.externalNode != null) {
                return ti.externalNode.deepCopy(node);
            }

            /*
             * Node value.
             */
            if (ti.valueInfos != null) {
                String valueFormat = context.defaultValueFormat(nodeClazz);
                ValueInfo valueInfo = ti.requireValueInfo(valueFormat);
                return valueInfo.valueCopy(node);
            }


            /*
             * Map.
             */
            if (node instanceof Map) {
                Map<String, Object> src = (Map<String, Object>) node;
                Map<String, Object> target = TypeRegistry.newMapContainer(nodeClazz, src.size(), true);
                Type valueType = Types.resolveTypeArgument(toType, Map.class, 1);
                Class<?> valueBoxed = Types.rawBox(valueType);
                for (Map.Entry<String, Object> entry : src.entrySet()) {
                    PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                    target.put(entry.getKey(), _deepCopy(entry.getValue(), valueType, valueBoxed, cps, context));
                }
                return target;
            }

            /*
             * List.
             */
            if (node instanceof List) {
                List<Object> src = (List<Object>) node;
                List<Object> target = TypeRegistry.newListContainer(nodeClazz, src.size(), true);
                Type elemType = Types.resolveTypeArgument(toType, List.class, 0);
                Class<?> elemBoxed = Types.rawBox(elemType);
                for (int i = 0; i < src.size(); i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    target.add(_deepCopy(src.get(i), elemType, elemBoxed, cps, context));
                }
                return target;
            }

            /*
             * Plain JsonObject.
             *
             * JOJO is handled together with normal POJOs below.
             */
            if (nodeClazz == JsonObject.class) {
                JsonObject src = (JsonObject) node;
                JsonObject target = new JsonObject();
                src.forEach((key, value) -> {
                    PathSegment cps = new PathSegment.Name(ps, key);
                    target.put(key, _deepCopy(value, Object.class, Object.class, cps, context));
                });
                return target;
            }

            /*
             * JsonArray / JAJO.
             */
            if (node instanceof JsonArray) {
                JsonArray src = (JsonArray) node;
                JsonArray target = nodeClazz == JsonArray.class
                        ? new JsonArray()
                        : (JsonArray) TypeRegistry.requireRegisteredPojoInfo(nodeClazz).creatorInfo.forceNewPojo();
                Type elemType = Types.resolveTypeArgument(toType, List.class, 0);
                Class<?> elemBoxed = Types.rawBox(elemType);
                for (int i = 0; i < src.size(); i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    target.add(_deepCopy(src.getNode(i), elemType, elemBoxed, cps, context));
                }
                return target;
            }

            /*
             * Java array.
             */
            if (nodeClazz.isArray()) {
                int len = Array.getLength(node);
                Class<?> componentClass = nodeClazz.getComponentType();
                Object target = Array.newInstance(componentClass, len);
                Class<?> componentBoxed = Types.rawBox(componentClass);
                for (int i = 0; i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    Array.set(target, i, _deepCopy(Array.get(node, i), componentClass, componentBoxed, cps, context));
                }
                return target;
            }

            /*
             * Set.
             */
            if (node instanceof Set) {
                Set<Object> src = (Set<Object>) node;
                Set<Object> target = TypeRegistry.newSetContainer(nodeClazz, src.size(), true);
                Type elemType = Types.resolveTypeArgument(toType, Set.class, 0);
                Class<?> elemBoxed = Types.rawBox(elemType);
                int i = 0;
                for (Object value : src) {
                    PathSegment cps = new PathSegment.Index(ps, i++);
                    target.add(_deepCopy(value, elemType, elemBoxed, cps, context));
                }
                return target;
            }

            /*
             * POJO / JOJO.
             */
            PojoInfo pojoInfo = ti.pojoInfo;
            if (pojoInfo != null) {
                /*
                 * If the declared target is a supertype (Object/interface/base
                 * class), the runtime POJO is what is actually being recreated.
                 *
                 * Its member types therefore need to be resolved against the
                 * runtime class rather than against the declared supertype.
                 */
                Type pojoType = nodeClazz == toBoxed ? toType : nodeClazz;
                return _deepCopyPojo(node, pojoType, nodeClazz, pojoInfo, ps, context);
            }

            /*
             * Unknown immutable / unsupported value.
             */
            return node;
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to deep copy node '" + Types.name(node) + "'", ps, e);
        }
    }

    private static Object _deepCopyPojo(Object source, Type type, Class<?> boxed, PojoInfo pojoInfo, PathSegment ps,
                                        RuntimeContext context) {
        CreatorInfo ci = pojoInfo.creatorInfo;
        int expected = pojoInfo.readableProperties.length;
        if (source instanceof JsonObject) {
            expected += ((JsonObject) source).size();
        }
        TypeRegistry.PojoCreationSession session = new TypeRegistry.PojoCreationSession(ci, expected);

        /*
         * Static POJO properties.
         */
        for (PropertyInfo property : pojoInfo.readableProperties) {
            String name = property.name;
            Object value = property.invokeGetter(source);
            PathSegment cps = new PathSegment.Name(ps, name);
            int argIdx = ci.getArgIndexOrAlias(name);
            if (argIdx >= 0) {
                Type argType = Types.resolveMemberType(type, boxed, ci.argTypes[argIdx]);
                Class<?> argBoxed = Types.rawBox(argType);
                ValueInfo valueInfo = ci.argValueCodecs[argIdx];
                Object copied = value != null && valueInfo != null
                        ? valueInfo.valueCopy(value)
                        : _deepCopy(value, argType, argBoxed, cps, context);
                session.acceptCtorArg(argIdx, copied);
                continue;
            }

            if (!property.writable) {
                continue;
            }

            Type propertyType = property.genericDependent
                    ? Types.resolveMemberType(type, boxed, property.type)
                    : property.type;
            Class<?> propertyBoxed = Types.rawBox(propertyType);
            Object copied = value != null && property.oneOfInfo == null && property.valueInfo != null
                    ? property.valueInfo.valueCopy(value)
                    : _deepCopy(value, propertyType, propertyBoxed, cps, context);
            session.acceptProperty(property, copied);
        }

        /*
         * JOJO dynamic properties.
         *
         * JsonObject itself was handled before reaching this method, so a
         * JsonObject here necessarily represents a JOJO.
         */
        if (source instanceof JsonObject) {
            JsonObject jo = (JsonObject) source;
            for (Map.Entry<String, Object> entry : jo.entrySet()) {
                String key = entry.getKey();
                PathSegment cps = new PathSegment.Name(ps, key);
                session.acceptDynamic(key, _deepCopy(entry.getValue(), Object.class, Object.class, cps, context));
            }
        }

        return session.finish();
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object _convertString(String value, Class<?> toBoxed, PathSegment ps) {
        if (toBoxed == String.class) {
            return value;
        }
        if (toBoxed == Character.class) {
            return !value.isEmpty() ? value.charAt(0) : null;
        }
        if (toBoxed.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) toBoxed, value);
        }
        throw new BindingException(
                "cannot convert String '" + Strings.truncate(value) +
                        "' to '" + toBoxed.getName() + "'", ps);
    }


    // Map -> Map/JsonObject/JOJO/POJO
    private static Object _convertFromMap(Map<String, Object> oldMap,
                                          Class<?> toBoxed,
                                          Type type,
                                          boolean deepCopy,
                                          PathSegment ps,
                                          RuntimeContext context) {
        return _convertFromObjectSource(new ObjectSource() {
            @Override
            public Iterable<Map.Entry<String, Object>> entries() {
                return oldMap.entrySet();
            }

            @Override
            public int size() {
                return oldMap.size();
            }
        }, "Map", toBoxed, type, deepCopy, ps, context);
    }

    // JsonObject -> Map/JsonObject/JOJO/POJO
    private static Object _convertFromJsonObject(JsonObject oldJo,
                                                 Class<?> toBoxed,
                                                 Type type,
                                                 boolean deepCopy,
                                                 PathSegment ps,
                                                 RuntimeContext context) {
        return _convertFromObjectSource(new ObjectSource() {
            @Override
            public Iterable<Map.Entry<String, Object>> entries() {
                return oldJo.entrySet();
            }

            @Override
            public int size() {
                return oldJo.size();
            }
        }, "JsonObject", toBoxed, type, deepCopy, ps, context);
    }

    private static Object _convertFromObjectSource(ObjectSource source,
                                                   String sourceName,
                                                   Class<?> toBoxed,
                                                   Type type,
                                                   boolean deepCopy,
                                                   PathSegment ps,
                                                   RuntimeContext context) {
        if (Map.class.isAssignableFrom(toBoxed)) {
            Map<String, Object> map =
                    TypeRegistry.newMapContainer(toBoxed, source.size(), false);

            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            for (Map.Entry<String, Object> entry : source.entries()) {
                PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                Object value = _convertSourceChild(entry.getValue(),
                        valueType, valueRaw, valueOneOf, deepCopy, cps, context, source.external());
                map.put(entry.getKey(), value);
            }
            return map;
        }

        if (toBoxed == JsonObject.class) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, Object> entry : source.entries()) {
                PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                Object value = _convertSourceChild(entry.getValue(),
                        Object.class, Object.class, null, deepCopy, cps, context, source.external());
                jo.put(entry.getKey(), value);
            }
            return jo;
        }

        PojoInfo pi = TypeRegistry.registerTypeInfo(toBoxed).pojoInfo;
        if (pi != null && !pi.isJajo) {
            return _convertPojoFromEntries(
                    source.entries(), type, toBoxed, pi, deepCopy, ps, context, source.external());
        }

        throw new BindingException(
                "cannot convert " + sourceName + " to '" + toBoxed.getName() + "'", ps);
    }

    private static Object _convertPojoFromEntries(Iterable<Map.Entry<String, Object>> entries, Type type, Class<?> toBoxed,
                                                  PojoInfo pi, boolean deepCopy, PathSegment ps,
                                                  RuntimeContext context, boolean externalSource) {
        CreatorInfo ci = pi.creatorInfo;
        CreatorState state = new CreatorState(ci);

        for (Map.Entry<String, Object> entry : entries) {
            String key = entry.getKey();
            Object rawValue = entry.getValue();

            int argIdx = ci.getArgIndexOrAlias(key);
            if (argIdx >= 0) {
                Type argType = Types.resolveMemberType(type, toBoxed, ci.argTypes[argIdx]);
                PathSegment cps = new PathSegment.Name(ps, key);
                Class<?> argRaw = Types.rawBox(argType);

                TypeInfo ti = TypeRegistry.registerTypeInfo(argRaw);
                ValueInfo argValueInfo = ci.argValueCodecs[argIdx];
                if (argValueInfo == null && ti.valueInfos != null) {
                    String valueFormat = context.defaultValueFormat(argRaw);
                    argValueInfo = ti.requireValueInfo(valueFormat);
                }

                if (ti.oneOfInfo == null && argValueInfo != null) {
                    state.acceptCtorArg(argIdx,
                            _convertValue(rawValue, argRaw, argValueInfo, deepCopy, cps, context));
                } else {
                    state.acceptCtorArg(argIdx, _convertSourceChild(rawValue, argType, argRaw, ti.oneOfInfo, deepCopy, cps, context, externalSource));
                }
                continue;
            }

            PropertyInfo propertyInfo = pi.getPropertyNoAlias(key);
            if (propertyInfo != null) {
                if (!propertyInfo.writable) continue;

                PathSegment cps = new PathSegment.Name(ps, key);
                Type fieldType = propertyInfo.genericDependent
                        ? Types.resolveMemberType(type, toBoxed, propertyInfo.type)
                        : propertyInfo.type;
                Class<?> fieldRaw = propertyInfo.genericDependent
                        ? Types.rawBox(fieldType)
                        : propertyInfo.boxed;

                Object value;
                if (propertyInfo.oneOfInfo == null && propertyInfo.valueInfo != null) {
                    value = _convertValue(rawValue, fieldRaw, propertyInfo.valueInfo,
                            deepCopy, cps, context);
                } else {
                    value = _convertSourceChild(rawValue, fieldType, fieldRaw, propertyInfo.oneOfInfo, deepCopy, cps, context, externalSource);
                }

                if (state.isCreated()) {
                    propertyInfo.invokeSetter(state.pojo(), value);
                } else {
                    state.bufferProperty(propertyInfo, value);
                }
                continue;
            }

            if (pi.isJojo && pi.readDynamic) {
                PathSegment cps = new PathSegment.Name(ps, key);
                Object value = _convertSourceChild(rawValue, Object.class, Object.class, null, deepCopy, cps, context, externalSource);
                state.acceptDynamic(key, value);
            }
        }

        return state.finish();
    }


    // List -> List/JsonArray/JAJO/Array/Set
    private static Object _convertFromList(List<?> oldList,
                                           Class<?> toBoxed,
                                           Type type,
                                           boolean deepCopy,
                                           PathSegment ps,
                                           RuntimeContext context) {
        return _convertFromIndexedSource(new IndexedSource() {
            @Override
            public int size() {
                return oldList.size();
            }

            @Override
            public Object get(int i) {
                return oldList.get(i);
            }
        }, "List", toBoxed, type, deepCopy, ps, context);
    }

    // JsonArray -> List/JsonArray/JAJO/Array/Set
    private static Object _convertFromJsonArray(JsonArray oldJa,
                                                Class<?> toBoxed,
                                                Type type,
                                                boolean deepCopy,
                                                PathSegment ps,
                                                RuntimeContext context) {
        return _convertFromIndexedSource(new IndexedSource() {
            @Override
            public int size() {
                return oldJa.size();
            }

            @Override
            public Object get(int i) {
                return oldJa.getNode(i);
            }
        }, "JsonArray", toBoxed, type, deepCopy, ps, context);
    }

    // Array -> List/JsonArray/JAJO/Array/Set
    private static Object _convertFromArray(Object node,
                                            Class<?> toBoxed,
                                            Type type,
                                            boolean deepCopy,
                                            PathSegment ps,
                                            RuntimeContext context) {
        return _convertFromIndexedSource(new IndexedSource() {
            @Override
            public int size() {
                return Array.getLength(node);
            }

            @Override
            public Object get(int i) {
                return Array.get(node, i);
            }
        }, "Array", toBoxed, type, deepCopy, ps, context);
    }

    // Set -> List/JsonArray/JAJO/Array/Set
    private static Object _convertFromSet(Set<Object> oldSet,
                                          Class<?> toBoxed,
                                          Type type,
                                          boolean deepCopy,
                                          PathSegment ps,
                                          RuntimeContext context) {
        int size = oldSet.size();

        if (List.class.isAssignableFrom(toBoxed)) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            List<Object> list =
                    TypeRegistry.newListContainer(toBoxed, size, false);

            int i = 0;
            for (Object value : oldSet) {
                list.add(_convert(value, valueType, valueRaw, valueOneOf,
                        deepCopy, new PathSegment.Index(ps, i++), context));
            }
            return list;
        }

        if (toBoxed == JsonArray.class) {
            JsonArray ja = new JsonArray();
            int i = 0;
            for (Object value : oldSet) {
                ja.add(_convert(value,
                        Object.class, Object.class, null, deepCopy,
                        new PathSegment.Index(ps, i++), context));
            }
            return ja;
        }

        if (JsonArray.class.isAssignableFrom(toBoxed)) {
            PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(toBoxed);
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            Class<?> valueRaw = jajo.elementClass();
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            int i = 0;
            for (Object value : oldSet) {
                jajo.add(_convert(value,
                        valueRaw, valueRaw, valueOneOf, deepCopy,
                        new PathSegment.Index(ps, i++), context));
            }
            return jajo;
        }

        if (toBoxed.isArray()) {
            Class<?> valueType = toBoxed.getComponentType();
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            Object array = Array.newInstance(valueType, size);
            int i = 0;
            for (Object value : oldSet) {
                Array.set(array, i, _convert(value,
                        valueType, valueRaw, valueOneOf, deepCopy,
                        new PathSegment.Index(ps, i++), context));
            }
            return array;
        }

        if (Set.class.isAssignableFrom(toBoxed)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            Set<Object> set =
                    TypeRegistry.newSetContainer(toBoxed, size, false);

            int i = 0;
            for (Object value : oldSet) {
                set.add(_convert(value, valueType, valueRaw, valueOneOf,
                        deepCopy, new PathSegment.Index(ps, i++), context));
            }
            return set;
        }

        throw new BindingException(
                "cannot convert Set to '" + toBoxed.getName() + "'", ps);
    }

    private static Object _convertFromIndexedSource(IndexedSource source,
                                                    String sourceName,
                                                    Class<?> toBoxed,
                                                    Type type,
                                                    boolean deepCopy,
                                                    PathSegment ps,
                                                    RuntimeContext context) {
        if (List.class.isAssignableFrom(toBoxed)) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            int size = source.size();
            List<Object> list =
                    TypeRegistry.newListContainer(toBoxed, size, false);

            for (int i = 0; i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                list.add(_convertSourceChild(source.get(i),
                        valueType, valueRaw, valueOneOf, deepCopy, cps, context, source.external()));
            }
            return list;
        }

        if (toBoxed == JsonArray.class) {
            JsonArray ja = new JsonArray();
            for (int i = 0, size = source.size(); i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                ja.add(_convertSourceChild(source.get(i),
                        Object.class, Object.class, null, deepCopy, cps, context, source.external()));
            }
            return ja;
        }

        if (JsonArray.class.isAssignableFrom(toBoxed)) {
            PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(toBoxed);
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            Class<?> valueRaw = jajo.elementClass();
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            for (int i = 0, size = source.size(); i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                jajo.add(_convertSourceChild(source.get(i),
                        valueRaw, valueRaw, valueOneOf, deepCopy, cps, context, source.external()));
            }
            return jajo;
        }

        if (toBoxed.isArray()) {
            Class<?> valueType = toBoxed.getComponentType();
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            Object array = Array.newInstance(valueType, source.size());
            for (int i = 0, size = source.size(); i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                Array.set(array, i, _convertSourceChild(source.get(i),
                        valueType, valueRaw, valueOneOf, deepCopy, cps, context, source.external()));
            }
            return array;
        }

        if (Set.class.isAssignableFrom(toBoxed)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            int size = source.size();
            Set<Object> set =
                    TypeRegistry.newSetContainer(toBoxed, size, false);

            for (int i = 0; i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                set.add(_convertSourceChild(source.get(i),
                        valueType, valueRaw, valueOneOf, deepCopy, cps, context, source.external()));
            }
            return set;
        }

        throw new BindingException(
                "cannot convert " + sourceName + " to '" +
                        toBoxed.getName() + "'", ps);
    }


    // POJO -> Map/JsonObject/JOJO/POJO
    private static Object _convertFromPojo(Object node,
                                           PojoInfo sourceInfo,
                                           Class<?> toBoxed,
                                           Type type,
                                           boolean deepCopy,
                                           PathSegment ps,
                                           RuntimeContext context) {
        if (Map.class.isAssignableFrom(toBoxed)) {
            Map<String, Object> map = TypeRegistry.newMapContainer(
                    toBoxed, sourceInfo.readableProperties.length, false);

            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf = TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;
            for (PropertyInfo propertyInfo : sourceInfo.readableProperties) {
                Object value = propertyInfo.invokeGetter(node);
                PathSegment cps = new PathSegment.Name(ps, propertyInfo.name);
                map.put(propertyInfo.name,
                        _convert(value, valueType, valueRaw, valueOneOf, deepCopy, cps, context));
            }
            return map;
        }

        if (toBoxed == JsonObject.class) {
            JsonObject jo = new JsonObject();
            for (PropertyInfo propertyInfo : sourceInfo.readableProperties) {
                Object value = propertyInfo.invokeGetter(node);
                PathSegment cps = new PathSegment.Name(ps, propertyInfo.name);
                jo.put(propertyInfo.name,
                        _convert(value, Object.class, Object.class, null, deepCopy, cps, context));
            }
            return jo;
        }

        PojoInfo targetInfo = TypeRegistry.registerTypeInfo(toBoxed).pojoInfo;
        if (targetInfo != null && !targetInfo.isJajo) {
            return _convertPojoFromProperties(node, sourceInfo, type, toBoxed, targetInfo, deepCopy, ps, context);
        }

        throw new BindingException("cannot convert POJO to '" + toBoxed.getName() + "'", ps);
    }

    private static Object _convertPojoFromProperties(Object source,
                                                     PojoInfo sourceInfo,
                                                     Type type,
                                                     Class<?> toBoxed,
                                                     PojoInfo targetInfo,
                                                     boolean deepCopy,
                                                     PathSegment ps,
                                                     RuntimeContext context) {
        Object[] sourceValues = new Object[sourceInfo.readableProperties.length];

        int sourceIndex = 0;
        for (PropertyInfo propertyInfo : sourceInfo.readableProperties) {
            sourceValues[sourceIndex++] = propertyInfo.invokeGetter(source);
        }

        CreatorInfo ci = targetInfo.creatorInfo;
        CreatorState state = new CreatorState(ci);

        sourceIndex = 0;
        for (PropertyInfo propertyInfo : sourceInfo.readableProperties) {
            Object rawValue = sourceValues[sourceIndex++];

            int argIdx = ci.getArgIndexOrAlias(propertyInfo.name);
            if (argIdx >= 0) {
                Type argType = Types.resolveMemberType(type, toBoxed, ci.argTypes[argIdx]);
                PathSegment cps = new PathSegment.Name(ps, propertyInfo.name);
                Class<?> argRaw = Types.rawBox(argType);

                TypeInfo ti = TypeRegistry.registerTypeInfo(argRaw);
                ValueInfo argValueInfo = ci.argValueCodecs[argIdx];
                if (argValueInfo == null && ti.valueInfos != null) {
                    String valueFormat = context.defaultValueFormat(argRaw);
                    argValueInfo = ti.requireValueInfo(valueFormat);
                }

                if (ti.oneOfInfo == null && argValueInfo != null) {
                    state.acceptCtorArg(argIdx,
                            _convertValue(rawValue, argRaw, argValueInfo, deepCopy, cps, context));
                } else {
                    state.acceptCtorArg(argIdx,
                            _convert(rawValue, argType, argRaw, ti.oneOfInfo, deepCopy, cps, context));
                }
                continue;
            }

            PropertyInfo targetPropertyInfo = targetInfo.getPropertyNoAlias(propertyInfo.name);
            if (targetPropertyInfo != null) {
                if (!targetPropertyInfo.writable) continue;

                PathSegment cps = new PathSegment.Name(ps, propertyInfo.name);
                Type fieldType = targetPropertyInfo.genericDependent
                        ? Types.resolveMemberType(type, toBoxed, targetPropertyInfo.type)
                        : targetPropertyInfo.type;
                Class<?> fieldRaw = targetPropertyInfo.genericDependent
                        ? Types.rawBox(fieldType)
                        : targetPropertyInfo.boxed;

                Object value;
                if (targetPropertyInfo.oneOfInfo == null && targetPropertyInfo.valueInfo != null) {
                    value = _convertValue(rawValue, fieldRaw, targetPropertyInfo.valueInfo,
                            deepCopy, cps, context);
                } else {
                    value = _convert(rawValue, fieldType, fieldRaw, targetPropertyInfo.oneOfInfo, deepCopy, cps, context);
                }

                if (state.isCreated()) {
                    targetPropertyInfo.invokeSetter(state.pojo(), value);
                } else {
                    state.bufferProperty(targetPropertyInfo, value);
                }
                continue;
            }

            if (targetInfo.isJojo && targetInfo.readDynamic) {
                PathSegment cps = new PathSegment.Name(ps, propertyInfo.name);
                state.acceptDynamic(propertyInfo.name,
                        _convert(rawValue, Object.class, Object.class, null, deepCopy, cps, context));
            }
        }

        return state.finish();
    }


    private interface IndexedSource {
        int size();

        Object get(int i);

        default boolean external() {
            return false;
        }
    }

    private interface ObjectSource {
        Iterable<Map.Entry<String, Object>> entries();

        int size();

        default boolean external() {
            return false;
        }
    }

    private static Object _convertSourceChild(Object value, Type type, Class<?> raw,
                                              OneOfInfo oneOfInfo, boolean deepCopy,
                                              PathSegment ps, RuntimeContext context,
                                              boolean externalSource) {
        if (externalSource && raw == Object.class && oneOfInfo == null) {
            return _convertToRaw(value, ps, context);
        }
        return _convert(value, type, raw, oneOfInfo, deepCopy, ps, context);
    }


    /*
     * --------------------------------------------------------------
     * Raw conversion
     * --------------------------------------------------------------
     */

    @SuppressWarnings("unchecked")
    private static Object _convertToRaw(Object node, PathSegment ps, RuntimeContext context) {
        try {
            if (node == null) return null;

            if (node instanceof String) {
                return node.toString();
            }

            if (node instanceof Number) {
                return node;
            }

            if (node instanceof Boolean) {
                return node;
            }

            if (node instanceof Map) {
                Map<String, Object> oldMap = (Map<String, Object>) node;
                Map<String, Object> newMap = TypeRegistry.newMapContainer(LinkedHashMap.class, oldMap.size(), false);

                for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                    PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                    newMap.put(entry.getKey(), _convertToRaw(entry.getValue(), cps, context));
                }
                return newMap;
            }

            if (node instanceof List) {
                List<Object> oldList = (List<Object>) node;
                List<Object> newList = new ArrayList<>(oldList.size());

                for (int i = 0, len = oldList.size(); i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    newList.add(_convertToRaw(oldList.get(i), cps, context));
                }
                return newList;
            }

            Class<?> rawClazz = node.getClass();

            if (node instanceof JsonObject) {
                JsonObject jo = (JsonObject) node;

                if (rawClazz == JsonObject.class) {
                    Map<String, Object> newMap = TypeRegistry.newMapContainer(LinkedHashMap.class, jo.size(), false);

                    for (Map.Entry<String, Object> entry : jo.entrySet()) {
                        PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                        newMap.put(entry.getKey(), _convertToRaw(entry.getValue(), cps, context));
                    }
                    return newMap;
                }

                PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(rawClazz);
                Map<String, Object> dynamic = pi.writeDynamic ? InternalAccess.dynamicProperties(jo) : null;

                int size = pi.readableProperties.length + (dynamic == null ? 0 : dynamic.size());

                Map<String, Object> newMap = TypeRegistry.newMapContainer(LinkedHashMap.class, size, false);

                for (PropertyInfo propertyInfo : pi.readableProperties) {
                    Object value = propertyInfo.invokeGetter(node);
                    PathSegment cps = new PathSegment.Name(ps, propertyInfo.name);

                    Object raw = value != null && propertyInfo.valueInfo != null
                            ? propertyInfo.valueInfo.valueToRaw(value)
                            : _convertToRaw(value, cps, context);

                    newMap.put(propertyInfo.name, raw);
                }

                if (dynamic != null) {
                    for (Map.Entry<String, Object> entry : dynamic.entrySet()) {
                        PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                        newMap.put(entry.getKey(), _convertToRaw(entry.getValue(), cps, context));
                    }
                }

                return newMap;
            }

            if (node instanceof JsonArray) {
                JsonArray ja = (JsonArray) node;
                List<Object> newList = new ArrayList<>(ja.size());

                for (int i = 0, len = ja.size(); i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    newList.add(_convertToRaw(ja.getNode(i), cps, context));
                }
                return newList;
            }

            if (rawClazz.isArray()) {
                int len = Array.getLength(node);
                List<Object> newList = new ArrayList<>(len);

                for (int i = 0; i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    newList.add(_convertToRaw(Array.get(node, i), cps, context));
                }
                return newList;
            }

            if (node instanceof Set) {
                Set<Object> set = (Set<Object>) node;
                List<Object> newList = new ArrayList<>(set.size());

                int i = 0;
                for (Object value : set) {
                    PathSegment cps = new PathSegment.Index(ps, i++);
                    newList.add(_convertToRaw(value, cps, context));
                }
                return newList;
            }

            if (node instanceof Character) {
                return node.toString();
            }

            if (node instanceof Enum) {
                return ((Enum<?>) node).name();
            }

            TypeInfo ti = TypeRegistry.registerTypeInfo(rawClazz);
            if (ti.externalNode != null) {
                ExternalNode<Object> external = ti.externalNode;
                switch (external.jsonType(node)) {
                    case NULL:
                        return null;
                    case STRING:
                        return external.toString(node);
                    case NUMBER:
                        return external.toNumber(node);
                    case BOOLEAN:
                        return external.toBoolean(node);
                    case OBJECT: {
                        Map<String, Object> map = new LinkedHashMap<>();
                        for (Map.Entry<String, Object> entry : external.entrySetInObject(node)) {
                            map.put(entry.getKey(), _convertToRaw(entry.getValue(),
                                    new PathSegment.Name(ps, entry.getKey()), context));
                        }
                        return map;
                    }
                    case ARRAY: {
                        int size = external.sizeInArray(node);
                        List<Object> list = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            list.add(_convertToRaw(external.getInArray(node, i),
                                    new PathSegment.Index(ps, i), context));
                        }
                        return list;
                    }
                    default:
                        throw new BindingException("unsupported external node type '" + Types.name(node) + "'", ps);
                }
            }
            if (ti.valueInfos != null) {
                String valueFormat = context.defaultValueFormat(rawClazz);
                ValueInfo valueInfo = ti.requireValueInfo(valueFormat);
                return valueInfo.valueToRaw(node);
            }

            PojoInfo pi = ti.pojoInfo;
            if (pi != null) {
                Map<String, Object> newMap = TypeRegistry.newMapContainer(LinkedHashMap.class,
                        pi.readableProperties.length, false);

                for (PropertyInfo propertyInfo : pi.readableProperties) {
                    Object value = propertyInfo.invokeGetter(node);
                    PathSegment cps = new PathSegment.Name(ps, propertyInfo.name);

                    Object raw = value != null && propertyInfo.valueInfo != null
                            ? propertyInfo.valueInfo.valueToRaw(value)
                            : _convertToRaw(value, cps, context);

                    newMap.put(propertyInfo.name, raw);
                }

                return newMap;
            }

            throw new BindingException("unsupported node type '" + Types.name(node) + "'", ps);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("cannot convert node from '" + Types.name(node) +
                    "' to raw (Map/List/String/Number/Boolean/null)", ps, e);
        }
    }

}
