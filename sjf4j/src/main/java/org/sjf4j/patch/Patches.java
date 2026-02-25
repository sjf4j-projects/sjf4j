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

/**
 * Patch utilities: JSON Merge Patch (RFC 7386) and JSON Patch diff.
 */
public class Patches {

    /**
     * Recursively merges a patch into target with custom overwrite rules.
     *
     * <p>This is a practical deep-merge utility (not RFC 7386):
     * object fields merge by key, array values merge by index, and scalar
     * values are assigned according to {@code overwrite}.</p>
     *
     * <p>Main usage:
     * use {@code overwrite=true} to let patch values replace existing values,
     * use {@code overwrite=false} to fill only missing/null target values.
     * Use {@code deepCopy=true} when patch nodes may be reused elsewhere and
     * should not be shared by reference.</p>
     *
     * <p>Example: merge defaults into request data with
     * {@code merge(target, defaults, false, true)}.
     * Example: apply an update payload over current data with
     * {@code merge(target, update, true, false)}.</p>
     *
     * <p>If you need standards-compliant JSON Merge Patch behavior
     * (array replace + {@code null} means delete), use {@link #mergeRfc7386(Object, Object)}.</p>
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
     * Merges a patch into target using RFC 7386 semantics.
     *
     * <p>Use this when you need standard JSON Merge Patch behavior instead of
     * custom deep merge from {@link #merge(Object, Object, boolean, boolean)}.</p>
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


    /**
     * Computes a JSON Patch that transforms source into target.
     */
    public static List<PatchOp> diff(Object source, Object target) {
        List<PatchOp> ops = new ArrayList<>();
        _diff(ops, PathSegment.Root.INSTANCE, source, target);
        return ops;
    }

    /**
     * Recursively builds JSON Patch ops for the diff.
     */
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
