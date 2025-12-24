package org.sjf4j.util;

import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeWalker;
import org.sjf4j.node.NodeType;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.patch.PatchOp;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathToken;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            if ((ntSource == NodeType.OBJECT_JOJO || ntTarget == NodeType.OBJECT_JOJO)
                    && source.getClass() != target.getClass()) {
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
            if ((ntSource == NodeType.ARRAY_JAJO || ntTarget == NodeType.ARRAY_JAJO)
                    && source.getClass() != target.getClass()) {
                return false;
            }
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
            case OBJECT_MAP: {
                Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
                map.putAll((Map<String, ?>) container);
                return (T) map;
            }
            case OBJECT_JSON_OBJECT: {
                return (T) new JsonObject(container);
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
            case ARRAY_LIST: {
                List<Object> list = Sjf4jConfig.global().listSupplier.create();
                list.addAll((List<?>) container);
                return (T) list;
            }
            case ARRAY_JSON_ARRAY: {
                return (T) new JsonArray(container);
            }
            case ARRAY_JAJO: {
                NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(container.getClass());
                JsonArray jajo = (JsonArray) pi.newInstance();
                jajo.addAll((JsonArray) container);
                return (T) jajo;
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
        return (T) Sjf4jConfig.global().getNodeFacade().readNode(container, container.getClass());
    }

    /**
     * Recursively merges {@code mergePatch} into {@code target}.
     *
     * <p>The merge is performed in place and proceeds depth-first:
     * objects are merged by key and arrays by index. When both corresponding
     * nodes are composite (object or array), the merge continues recursively;
     * otherwise the patch value may replace the target value depending on
     * the {@code overwrite} rule.</p>
     *
     * <p>If {@code overwrite} is {@code false}, existing non-{@code null} target
     * values are preserved. If the target value is {@code null}, the patch value
     * is always applied.</p>
     *
     * <p>If {@code deepCopy} is {@code true}, composite values taken from
     * {@code mergePatch} are deep-copied before assignment; otherwise they are
     * inserted directly.</p>
     *
     * <p><strong>Note:</strong> This method does not follow RFC 7386.
     * Arrays are merged element-wise and {@code null} does not imply deletion.</p>
     *
     * @param target     the target object or array to be modified in place
     * @param mergePatch the patch object or array
     * @param overwrite  whether existing non-{@code null} values may be replaced
     * @param deepCopy   whether values from the patch should be deep-copied
     */
    public static void merge(Object target, Object mergePatch, boolean overwrite, boolean deepCopy) {
        if (target == null || mergePatch == null) return;
        NodeType ntTarget = NodeType.of(target);
        NodeType ntPatch = NodeType.of(mergePatch);
        if (ntTarget.isObject() && ntPatch.isObject()) {
            NodeWalker.visitObject(mergePatch, (key, subPatch) -> {
                Object subTarget = NodeWalker.getInObject(target, key);
                NodeType ntSubTarget = NodeType.of(subTarget);
                NodeType ntSubPatch = NodeType.of(subPatch);
                if (ntSubPatch.isObject()) {
                    if (ntSubTarget.isObject()) {
                        merge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        if (deepCopy) {
                            NodeWalker.putInObject(target, key, deepCopy(subPatch));
                        } else {
                            NodeWalker.putInObject(target, key, subPatch);
                        }
                    }
                } else if (ntSubPatch.isArray()) {
                    if (ntSubTarget.isArray()) {
                        merge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        if (deepCopy) {
                            NodeWalker.putInObject(target, key, deepCopy(subPatch));
                        } else {
                            NodeWalker.putInObject(target, key, subPatch);
                        }
                    }
                } else if (overwrite || subTarget == null) {
                    NodeWalker.putInObject(target, key, subPatch);
                }
            });
        } else if (ntTarget.isArray() && ntPatch.isArray()) {
            NodeWalker.visitArray(mergePatch, (i, subPatch) -> {
                Object subTarget = NodeWalker.getInArray(target, i);
                NodeType ntSubTarget = NodeType.of(subTarget);
                NodeType ntSubPatch = NodeType.of(subPatch);
                if (ntSubPatch.isObject()) {
                    if (ntSubTarget.isObject()) {
                        merge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        if (deepCopy) {
                            NodeWalker.setInArray(target, i, deepCopy(subPatch));
                        } else {
                            NodeWalker.setInArray(target, i, subPatch);
                        }
                    }
                } else if (ntSubPatch.isArray()) {
                    if (ntSubTarget.isArray()) {
                        merge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        if (deepCopy) {
                            NodeWalker.setInArray(target, i, deepCopy(subPatch));
                        } else {
                            NodeWalker.setInArray(target, i, subPatch);
                        }
                    }
                } else if (overwrite || subTarget == null) {
                    NodeWalker.setInArray(target, i, subPatch);
                }
            });
        }
    }

    /**
     * This method strictly follows the semantics defined in
     * <a href="https://datatracker.ietf.org/doc/html/rfc7386">RFC 7386 (JSON Merge Patch)</a>.
     *
     * <p>Differences from {@link #merge(Object, Object, boolean, boolean)}:
     * <ul>
     *   <li>Arrays are always replaced as a whole</li>
     *   <li>A {@code null} value in the patch deletes the corresponding target member</li>
     *   <li>{@code deepCopy} is implicitly disabled</li>
     * </ul>
     *
     * @param target        the target object to be merged
     * @param mergePatch    the JSON Merge Patch (RFC 7386)
     */
    public static void mergeRfc7386(Object target, Object mergePatch) {
        if (target == null || mergePatch == null) return;
        NodeType ntTarget = NodeType.of(target);
        NodeType ntPatch = NodeType.of(mergePatch);
        if (ntTarget.isObject() && ntPatch.isObject()) {
            NodeWalker.visitObject(mergePatch, (key, subPatch) -> {
                Object subTarget = NodeWalker.getInObject(target, key);
                NodeType ntSubTarget = NodeType.of(subTarget);
                NodeType ntSubPatch = NodeType.of(subPatch);
                if (subPatch == null) {
                    if (subTarget != null) {
                        NodeWalker.removeInObject(target, key);
                    }
                } else if (ntSubPatch.isObject()) {
                    if (ntSubTarget.isObject()) {
                        mergeRfc7386(subTarget, subPatch);
                    } else {
                        NodeWalker.putInObject(target, key, subPatch);
                    }
                } else {
                    NodeWalker.putInObject(target, key, subPatch);
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
            case OBJECT_JOJO: {
                JsonObject jo = (JsonObject) container;
                sb.append("@").append(container.getClass().getSimpleName()).append("{");
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
                sb.append("@").append(container.getClass().getSimpleName()).append("{");
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
            case ARRAY_JAJO: {
                JsonArray ja = (JsonArray) container;
                sb.append("@").append(container.getClass().getSimpleName()).append("[");
                int idx = 0;
                for (Object v : ja) {
                    if (idx++ > 0) sb.append(", ");
                    _inspect(v, sb);
                }
                sb.append("]");
                return;
            }
            case ARRAY_ARRAY: {
                int len = Array.getLength(container);
                sb.append("A[");
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


    public static List<PatchOp> diff(Object source, Object target) {
        List<PatchOp> ops = new ArrayList<>();
        _diff(ops, new JsonPointer(), source, target);
        return ops;
    }

    private static void _diff(List<PatchOp> ops, JsonPointer path, Object source, Object target) {
        if (source == null && target == null) return;
        if (null == source) {
            ops.add(new PatchOp(PatchOp.STD_ADD, path, target, null));
        } else if (null == target) {
            ops.add(new PatchOp(PatchOp.STD_REMOVE, path, null, null));
        } else {
            NodeType sourceType = NodeType.of(source);
            NodeType targetType = NodeType.of(target);
            if (sourceType.isObject() && targetType.isObject()) {
                NodeWalker.visitObject(source, (k, v) -> {
                    JsonPointer newPath = path.copy();
                    newPath.append(new PathToken.Name(k));
                    Object newTarget = NodeWalker.getInObject(target, k);
                    if (newTarget != null) {
                        _diff(ops, newPath, v, newTarget);
                    } else {
                        ops.add(new PatchOp(PatchOp.STD_REMOVE, newPath, null, null));
                    }
                });
                NodeWalker.visitObject(target, (k, v) -> {
                   if (!NodeWalker.containsInObject(source, k)) {
                       JsonPointer newPath = path.copy();
                       newPath.append(new PathToken.Name(k));
                       ops.add(new PatchOp(PatchOp.STD_ADD, newPath, v, null));
                   }
                });
            } else if (sourceType.isArray() && targetType.isArray()) {
                int sourceSize = NodeWalker.sizeInArray(source);
                int targetSize = NodeWalker.sizeInArray(target);
                int size = Math.min(sourceSize, targetSize);
                for (int i = 0; i < size; i++) {
                    JsonPointer newPath = path.copy();
                    newPath.append(new PathToken.Index(i));
                    _diff(ops, newPath, NodeWalker.getInArray(source, i), NodeWalker.getInArray(target, i));
                }
                if (targetSize > sourceSize) {  // add with '/xx/-'
                    JsonPointer newPath = path.copy();
                    newPath.append(PathToken.Append.INSTANCE);
                    for (int i = sourceSize; i < targetSize; i++) {
                        ops.add(new PatchOp(PatchOp.STD_ADD, newPath, NodeWalker.getInArray(target, i), null));
                    }
                }
                if (targetSize < sourceSize) {  // Remove from back to front
                    for (int i = sourceSize - 1; i >= targetSize; i--) {
                        JsonPointer newPath = path.copy();
                        newPath.append(new PathToken.Index(i));
                        ops.add(new PatchOp(PatchOp.STD_REMOVE, newPath, null, null));
                    }
                }
            } else if (!Objects.equals(source, target)) {
                ops.add(new PatchOp(PatchOp.STD_REPLACE, path, target, null));
            }
        }
    }


}
