package org.sjf4j.util;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeWalker;
import org.sjf4j.NodeType;
import org.sjf4j.NodeRegistry;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ContainerUtil {

    // Basic

    public static boolean equals(Object source, Object target) {
        if (target == source) return true;
        if (source == null || target == null) return false;
        NodeType ntSource = NodeType.of(source);
        NodeType ntTarget = NodeType.of(target);
        if (ntSource.isNumber() && ntTarget.isNumber()) {
            return NumberUtil.equals((Number) source, (Number) target);
        } else if (ntSource.isValue() && ntTarget.isValue()) {
            return source.equals(target);
        } else if (ntSource == NodeType.OBJECT_POJO) {
            return source.equals(target);
        } else if (ntTarget == NodeType.OBJECT_POJO) {
            return target.equals(source);
        } else if (ntSource.isObject() && ntTarget.isObject()) {
            if ((ntSource == NodeType.OBJECT_JOJO || ntTarget == NodeType.OBJECT_JOJO) &&
                    source.getClass() != target.getClass()) {
                return false;
            }
            if (NodeWalker.sizeInObject(source) != NodeWalker.sizeInObject(target)) return false;
            for (Map.Entry<String, Object> entry : NodeWalker.entrySetInObject(source)) {
                Object subSrouce = entry.getValue();
                Object subTarget = NodeWalker.getInObject(target, entry.getKey());
                if (!equals(subSrouce, subTarget)) return false;
            }
            return true;
        } else if (ntSource.isArray() && ntTarget.isArray()) {
            if (NodeWalker.sizeInArray(source) != NodeWalker.sizeInArray(target)) return false;
            int size = NodeWalker.sizeInArray(source);
            for (int i = 0; i < size; i++) {
                if (!equals(NodeWalker.getInArray(source, i), NodeWalker.getInArray(target, i))) return false;
            }
            return true;
        } else if (ntSource.isUnknown() && ntTarget.isUnknown()) {
            return source.equals(target);
        }
        return false;
    }

    /**
     * Returns a shallow copy of the given container.
     */
    @SuppressWarnings({"unchecked", "SuspiciousSystemArraycopy"})
    public static <T> T copy(T container) {
        NodeType nt = NodeType.of(container);
        switch (nt) {
            case OBJECT_JSON_OBJECT: {
                return (T) new JsonObject(container);
            }
            case OBJECT_MAP: {
                Map<String, Object> map = JsonConfig.global().mapSupplier.create();
                map.putAll((Map<String, ?>) container);
                return (T) map;
            }
            case OBJECT_JOJO: {
                NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(container.getClass());
                JsonObject jojo = (JsonObject) pi.newInstance();
                jojo.putAll(container);
                return (T) jojo;
            }
            case OBJECT_POJO: {
                NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(container.getClass());
                Object pojo = pi.newInstance();
                NodeWalker.visitObject(container, (k, v) -> NodeWalker.putInObject(pojo, k, v));
                return (T) pojo;
            }
            case ARRAY_JSON_ARRAY: {
                return (T) new JsonArray(container);
            }
            case ARRAY_LIST: {
                List<Object> list = JsonConfig.global().listSupplier.create();
                list.addAll((List<?>) container);
                return (T) list;
            }
            case ARRAY_ARRAY: {
                int len = Array.getLength(container);
                Object arr = Array.newInstance(container.getClass().getComponentType(), len);
                System.arraycopy(container, 0, arr, 0, len);
                return (T) arr;
            }
            default:
                return container;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T container) {
        if (container instanceof Map) {
            return (T) JsonConfig.global().getObjectFacade().readNode(container, Map.class);
        } else if (container instanceof List) {
            return (T) JsonConfig.global().getObjectFacade().readNode(container, List.class);
        }
        return (T) JsonConfig.global().getObjectFacade().readNode(container, container.getClass());
    }

    public static void merge(Object source, Object target, boolean preferTarget, boolean needCopy) {
        if (source == null || target == null) return;
        NodeType ntSource = NodeType.of(source);
        NodeType ntTarget = NodeType.of(target);
        if (ntSource.isObject() && ntTarget.isObject()) {
            NodeWalker.visitObject(target, (key, subTarget) -> {
                Object subSource = NodeWalker.getInObject(source, key);
                NodeType ntSubSource = NodeType.of(subSource);
                NodeType ntSubTarget = NodeType.of(subTarget);
                if (ntSubTarget.isObject()) {
                    if (ntSubSource.isObject()) {
                        merge(subSource, subTarget, preferTarget, needCopy);
                    } else if (preferTarget || subSource == null) {
                        if (needCopy) {
                            NodeWalker.putInObject(source, key, deepCopy(subTarget));
                        } else {
                            NodeWalker.putInObject(source, key, subTarget);
                        }
                    }
                } else if (ntSubTarget.isArray()) {
                    if (ntSubSource.isArray()) {
                        merge(subSource, subTarget, preferTarget, needCopy);
                    } else if (preferTarget || subSource == null) {
                        if (needCopy) {
                            NodeWalker.putInObject(source, key, deepCopy(subTarget));
                        } else {
                            NodeWalker.putInObject(source, key, subTarget);
                        }
                    }
                } else if (preferTarget || subSource == null) {
                    NodeWalker.putInObject(source, key, subTarget);
                }
            });
        } else if (ntSource.isArray() && ntTarget.isArray()) {
            NodeWalker.visitArray(target, (i, subTarget) -> {
                Object subSource = NodeWalker.getInArray(source, i);
                NodeType ntSubSource = NodeType.of(subSource);
                NodeType ntSubTarget = NodeType.of(subTarget);
                if (ntSubTarget.isObject()) {
                    if (ntSubSource.isObject()) {
                        merge(subSource, subTarget, preferTarget, needCopy);
                    } else if (preferTarget || subSource == null) {
                        if (needCopy) {
                            NodeWalker.setInArray(source, i, deepCopy(subTarget));
                        } else {
                            NodeWalker.setInArray(source, i, subTarget);
                        }
                    }
                } else if (ntSubTarget.isArray()) {
                    if (ntSubSource.isArray()) {
                        merge(subSource, subTarget, preferTarget, needCopy);
                    } else if (preferTarget || subSource == null) {
                        if (needCopy) {
                            NodeWalker.setInArray(source, i, deepCopy(subTarget));
                        } else {
                            NodeWalker.setInArray(source, i, subTarget);
                        }
                    }
                } else if (preferTarget || subSource == null) {
                    NodeWalker.setInArray(source, i, subTarget);
                }
            });
        }
    }

    /// Inspect

    // J{email=.com, User{*id=1, #name=Ja{a=b, c=d}}, Arr[haha, xi, 1])
    // M{..}
    // User{..}
    // J[..]
    // L[..]
    // A[..]
    // !DateTime@12345
    public static String inspect(Object container) {
        StringBuilder sb = new StringBuilder();
        _inspect(container, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void _inspect(Object container, StringBuilder sb) {
        NodeType nt = NodeType.of(container);
        switch (nt) {
            case OBJECT_JSON_OBJECT: {
                JsonObject jo = (JsonObject) container;
                sb.append("J{");
                int idx = 0;
                for (Map.Entry<String, Object> entry : jo.entrySet()) {
                    if (idx++ > 0) sb.append(", ");
                    sb.append(entry.getKey()).append("=");
                    _inspect(entry.getValue(), sb);
                }
                sb.append("}");
                return;
            }
            case OBJECT_MAP: {
                Map<String, Object> map = (Map<String, Object>) container;
                sb.append("M{");
                int idx = 0;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (idx++ > 0) sb.append(", ");
                    sb.append(entry.getKey()).append("=");
                    _inspect(entry.getValue(), sb);
                }
                sb.append("}");
                return;
            }
            case OBJECT_JOJO: {
                JsonObject jo = (JsonObject) container;
                sb.append(container.getClass().getSimpleName()).append("@").append("{");
                NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(container.getClass());
                AtomicInteger idx = new AtomicInteger(0);
                jo.forEach((k, v) -> {
                    if (idx.getAndIncrement() > 0) sb.append(", ");
                    if (pi != null && pi.getFields().containsKey(k)) {
                        sb.append("*");
                    }
                    sb.append(k).append("=");
                    _inspect(v, sb);
                });
                sb.append("}");
                return;
            }
            case OBJECT_POJO: {
                NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(container.getClass());
                sb.append(container.getClass().getSimpleName()).append("@").append("{");
                int idx = 0;
                for (Map.Entry<String, NodeRegistry.FieldInfo> fi : pi.getFields().entrySet()) {
                    if (idx++ > 0) sb.append(", ");
                    sb.append("*").append(fi.getKey()).append("=");
                    Object v = fi.getValue().invokeGetter(container);
                    _inspect(v, sb);
                }
                sb.append("}");
                return;
            }
            case ARRAY_JSON_ARRAY: {
                JsonArray ja = (JsonArray) container;
                sb.append("J[");
                int idx = 0;
                for (Object v : ja) {
                    if (idx++ > 0) sb.append(", ");
                    _inspect(v, sb);
                }
                sb.append("]");
                return;
            }
            case ARRAY_LIST: {
                List<Object> list = (List<Object>) container;
                sb.append("L[");
                int idx = 0;
                for (Object v : list) {
                    if (idx++ > 0) sb.append(", ");
                    _inspect(v, sb);
                }
                sb.append("]");
                return;
            }
            case ARRAY_ARRAY: {
                int len = Array.getLength(container);
                sb.append("A[");
                int idx = 0;
                for (int i = 0; i < len; i++) {
                    if (i > 0) sb.append(", ");
                    _inspect(Array.get(container, i), sb);
                }
                sb.append("]");
                return;
            }
            case UNKNOWN: {
                sb.append("!").append(container);
                return;
            }
            default: {
                sb.append(container);
                return;
            }
        }
    }


}
