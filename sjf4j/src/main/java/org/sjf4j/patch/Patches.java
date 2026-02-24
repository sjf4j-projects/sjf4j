package org.sjf4j.patch;

import org.sjf4j.JsonType;
import org.sjf4j.Sjf4j;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.NodeKind;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Patches {

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
        JsonType targetJt = JsonType.of(target);
        JsonType patchJt = JsonType.of(mergePatch);
        if (targetJt.isObject() && patchJt.isObject()) {
            Nodes.visitObject(mergePatch, (key, subPatch) -> {
                Object subTarget = Nodes.getInObject(target, key);
                JsonType subTargetJt = JsonType.of(subTarget);
                JsonType subPatchJt = JsonType.of(subPatch);
                if (subPatchJt.isObject()) {
                    if (subTargetJt.isObject()) {
                        merge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        if (deepCopy) {
                            Nodes.putInObject(target, key, Sjf4j.deepNode(subPatch));
                        } else {
                            Nodes.putInObject(target, key, subPatch);
                        }
                    }
                } else if (subPatchJt.isArray()) {
                    if (subTargetJt.isArray()) {
                        merge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        if (deepCopy) {
                            Nodes.putInObject(target, key, Sjf4j.deepNode(subPatch));
                        } else {
                            Nodes.putInObject(target, key, subPatch);
                        }
                    }
                } else if (overwrite || subTarget == null) {
                    Nodes.putInObject(target, key, subPatch);
                }
            });
        } else if (targetJt.isArray() && patchJt.isArray()) {
            Nodes.visitArray(mergePatch, (i, subPatch) -> {
                Object subTarget = Nodes.getInArray(target, i);
                JsonType subTargetJt = JsonType.of(subTarget);
                JsonType subPatchJt = JsonType.of(subPatch);
                if (subPatchJt.isObject()) {
                    if (subTargetJt.isObject()) {
                        merge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        if (deepCopy) {
                            Nodes.setInArray(target, i, Sjf4j.deepNode(subPatch));
                        } else {
                            Nodes.setInArray(target, i, subPatch);
                        }
                    }
                } else if (subPatchJt.isArray()) {
                    if (subTargetJt.isArray()) {
                        merge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        if (deepCopy) {
                            Nodes.setInArray(target, i, Sjf4j.deepNode(subPatch));
                        } else {
                            Nodes.setInArray(target, i, subPatch);
                        }
                    }
                } else if (overwrite || subTarget == null) {
                    Nodes.setInArray(target, i, subPatch);
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
        JsonType targetJt = JsonType.of(target);
        JsonType patchJt = JsonType.of(mergePatch);
        if (targetJt.isObject() && patchJt.isObject()) {
            Nodes.visitObject(mergePatch, (key, subPatch) -> {
                Object subTarget = Nodes.getInObject(target, key);
                JsonType subTargetJt = JsonType.of(subTarget);
                JsonType subPatchJt = JsonType.of(subPatch);
                if (subPatch == null) {
                    if (subTarget != null) {
                        Nodes.removeInObject(target, key);
                    }
                } else if (subPatchJt.isObject()) {
                    if (subTargetJt.isObject()) {
                        mergeRfc7386(subTarget, subPatch);
                    } else {
                        Nodes.putInObject(target, key, subPatch);
                    }
                } else {
                    Nodes.putInObject(target, key, subPatch);
                }
            });
        }
    }


    public static List<PatchOp> diff(Object source, Object target) {
        List<PatchOp> ops = new ArrayList<>();
        _diff(ops, PathSegment.Root.INSTANCE, source, target);
        return ops;
    }

    private static void _diff(List<PatchOp> ops, PathSegment ps, Object source, Object target) {
        if (source == null && target == null) return;
        if (null == source) {
            ops.add(new PatchOp(PatchOp.STD_ADD, JsonPointer.fromLast(ps), target, null));
        } else if (null == target) {
            ops.add(new PatchOp(PatchOp.STD_REMOVE, JsonPointer.fromLast(ps), null, null));
        } else {
            JsonType sourceJt = JsonType.of(source);
            JsonType targetJt = JsonType.of(target);
            if (sourceJt.isObject() && targetJt.isObject()) {
                Nodes.visitObject(source, (k, v) -> {
                    PathSegment cps = new PathSegment.Name(ps, null, k);
                    if (Nodes.containsInObject(target, k)) {
                        Object newTarget = Nodes.getInObject(target, k);
                        _diff(ops, cps, v, newTarget);
                    } else {
                        ops.add(new PatchOp(PatchOp.STD_REMOVE, JsonPointer.fromLast(cps), null, null));
                    }
                });
                Nodes.visitObject(target, (k, v) -> {
                   if (!Nodes.containsInObject(source, k)) {
                       PathSegment cps = new PathSegment.Name(ps, null, k);
                       ops.add(new PatchOp(PatchOp.STD_ADD, JsonPointer.fromLast(cps), v, null));
                   }
                });
            } else if (sourceJt.isArray() && targetJt.isArray()) {
                int sourceSize = Nodes.sizeInArray(source);
                int targetSize = Nodes.sizeInArray(target);
                int size = Math.min(sourceSize, targetSize);
                for (int i = 0; i < size; i++) {
                    PathSegment cps = new PathSegment.Index(ps, null, i);
                    _diff(ops, cps, Nodes.getInArray(source, i), Nodes.getInArray(target, i));
                }
                if (targetSize > sourceSize) {  // add with '/xx/-'
                    PathSegment cps = new PathSegment.Append(ps, null);
                    for (int i = sourceSize; i < targetSize; i++) {
                        ops.add(new PatchOp(PatchOp.STD_ADD, JsonPointer.fromLast(cps),
                                Nodes.getInArray(target, i), null));
                    }
                }
                if (targetSize < sourceSize) {  // Remove from back to front
                    for (int i = sourceSize - 1; i >= targetSize; i--) {
                        PathSegment cps = new PathSegment.Index(ps, null, i);
                        ops.add(new PatchOp(PatchOp.STD_REMOVE, JsonPointer.fromLast(cps), null, null));
                    }
                }
            } else if (!Objects.equals(source, target)) {
                ops.add(new PatchOp(PatchOp.STD_REPLACE, JsonPointer.fromLast(ps), target, null));
            }
        }
    }


}
