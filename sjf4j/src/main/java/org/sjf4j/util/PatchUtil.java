package org.sjf4j.util;

import org.sjf4j.node.NodeWalker;
import org.sjf4j.node.NodeType;
import org.sjf4j.patch.PatchOp;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PatchUtil {

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
                            NodeWalker.putInObject(target, key, NodeUtil.deepCopy(subPatch));
                        } else {
                            NodeWalker.putInObject(target, key, subPatch);
                        }
                    }
                } else if (ntSubPatch.isArray()) {
                    if (ntSubTarget.isArray()) {
                        merge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        if (deepCopy) {
                            NodeWalker.putInObject(target, key, NodeUtil.deepCopy(subPatch));
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
                            NodeWalker.setInArray(target, i, NodeUtil.deepCopy(subPatch));
                        } else {
                            NodeWalker.setInArray(target, i, subPatch);
                        }
                    }
                } else if (ntSubPatch.isArray()) {
                    if (ntSubTarget.isArray()) {
                        merge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        if (deepCopy) {
                            NodeWalker.setInArray(target, i, NodeUtil.deepCopy(subPatch));
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
                    newPath.push(new PathToken.Name(k));
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
                       newPath.push(new PathToken.Name(k));
                       ops.add(new PatchOp(PatchOp.STD_ADD, newPath, v, null));
                   }
                });
            } else if (sourceType.isArray() && targetType.isArray()) {
                int sourceSize = NodeWalker.sizeInArray(source);
                int targetSize = NodeWalker.sizeInArray(target);
                int size = Math.min(sourceSize, targetSize);
                for (int i = 0; i < size; i++) {
                    JsonPointer newPath = path.copy();
                    newPath.push(new PathToken.Index(i));
                    _diff(ops, newPath, NodeWalker.getInArray(source, i), NodeWalker.getInArray(target, i));
                }
                if (targetSize > sourceSize) {  // add with '/xx/-'
                    JsonPointer newPath = path.copy();
                    newPath.push(PathToken.Append.INSTANCE);
                    for (int i = sourceSize; i < targetSize; i++) {
                        ops.add(new PatchOp(PatchOp.STD_ADD, newPath, NodeWalker.getInArray(target, i), null));
                    }
                }
                if (targetSize < sourceSize) {  // Remove from back to front
                    for (int i = sourceSize - 1; i >= targetSize; i--) {
                        JsonPointer newPath = path.copy();
                        newPath.push(new PathToken.Index(i));
                        ops.add(new PatchOp(PatchOp.STD_REMOVE, newPath, null, null));
                    }
                }
            } else if (!Objects.equals(source, target)) {
                ops.add(new PatchOp(PatchOp.STD_REPLACE, path, target, null));
            }
        }
    }


}
