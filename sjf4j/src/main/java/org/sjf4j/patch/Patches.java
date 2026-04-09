package org.sjf4j.patch;

import org.sjf4j.JsonType;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.node.Nodes;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * Patch utilities: JSON Merge Patch (RFC 7386) and JSON Patch diff.
 */
public final class Patches {

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
    public static void merge(Object target, Object patch, boolean overwrite, boolean deepCopy) {
        if (target == null || patch == null) return;
        JsonType targetJt = JsonType.of(target);
        JsonType patchJt = JsonType.of(patch);
        if (targetJt.isObject() && patchJt.isObject()) {
            Nodes.visitObject(patch, (key, subPatch) -> {
                Object subTarget = Nodes.getInObject(target, key);
                JsonType subTargetJt = JsonType.of(subTarget);
                JsonType subPatchJt = JsonType.of(subPatch);
                if (subPatchJt.isObject()) {
                    if (subTargetJt.isObject()) {
                        merge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        if (deepCopy) {
                            Nodes.putInObject(target, key, Sjf4j.global().deepNode(subPatch));
                        } else {
                            Nodes.putInObject(target, key, subPatch);
                        }
                    }
                } else if (subPatchJt.isArray()) {
                    if (subTargetJt.isArray()) {
                        merge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        if (deepCopy) {
                            Nodes.putInObject(target, key, Sjf4j.global().deepNode(subPatch));
                        } else {
                            Nodes.putInObject(target, key, subPatch);
                        }
                    }
                } else if (overwrite || subTarget == null) {
                    Nodes.putInObject(target, key, subPatch);
                }
            });
        } else if (targetJt.isArray() && patchJt.isArray()) {
            Nodes.visitArray(patch, (i, subPatch) -> {
                Object subTarget = Nodes.getInArray(target, i);
                JsonType subTargetJt = JsonType.of(subTarget);
                JsonType subPatchJt = JsonType.of(subPatch);
                if (subPatchJt.isObject()) {
                    if (subTargetJt.isObject()) {
                        merge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        if (deepCopy) {
                            Nodes.setInArray(target, i, Sjf4j.global().deepNode(subPatch));
                        } else {
                            Nodes.setInArray(target, i, subPatch);
                        }
                    }
                } else if (subPatchJt.isArray()) {
                    if (subTargetJt.isArray()) {
                        merge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        if (deepCopy) {
                            Nodes.setInArray(target, i, Sjf4j.global().deepNode(subPatch));
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
     * <p>Object members are merged recursively. A {@code null} patch member means
     * remove that member from target. Non-object patch values replace the target
     * value at that member.
     * Use this when you need standards-compliant merge behavior instead of
     * custom deep merge from {@link #merge(Object, Object, boolean, boolean)}.</p>
     */
    public static Object mergeRfc7386(Object target, Object patch) {
        JsonType patchJt = JsonType.of(patch);
        if (!patchJt.isObject()) {
            return patch;
        }

        JsonType targetJt = JsonType.of(target);
        Object current = targetJt.isObject() ? target : new JsonObject();
        Nodes.visitObject(patch, (key, subPatch) -> {
            if (subPatch == null) {
                if (Nodes.containsInObject(current, key)) {
                    Nodes.removeInObject(current, key);
                }
                return;
            }

            JsonType subPatchJt = JsonType.of(subPatch);
            if (subPatchJt.isObject()) {
                Object subTarget = Nodes.getInObject(current, key);
                Object subMerged = mergeRfc7386(subTarget, subPatch);
                Nodes.putInObject(current, key, subMerged);
            } else {
                Nodes.putInObject(current, key, subPatch);
            }
        });
        return current;
    }


    /**
     * Computes a JSON Patch operation list that transforms {@code source} into
     * {@code target}.
     */
    public static List<PatchOperation> diff(Object source, Object target) {
        List<PatchOperation> operations = new ArrayList<>();
        _diff(operations, PathSegment.Root.INSTANCE, source, target);
        return operations;
    }

    /**
     * Recursively builds JSON Patch ops for the diff.
     * <p>
     * Array growth emits {@code add} with append path ({@code /-}); array shrink
     * emits {@code remove} from tail to head to keep indexes stable.
     */
    private static void _diff(List<PatchOperation> operations, PathSegment ps, Object source, Object target) {
        if (source == null && target == null) return;
        if (null == source) {
            operations.add(new PatchOperation(PatchOperation.STD_ADD, JsonPointer.fromLast(ps), target, null));
        } else if (null == target) {
            operations.add(new PatchOperation(PatchOperation.STD_REMOVE, JsonPointer.fromLast(ps), null, null));
        } else {
            JsonType sourceJt = JsonType.of(source);
            JsonType targetJt = JsonType.of(target);
            if (sourceJt.isObject() && targetJt.isObject()) {
                Nodes.visitObject(source, (k, v) -> {
                    PathSegment cps = new PathSegment.Name(ps, null, k);
                    if (Nodes.containsInObject(target, k)) {
                        Object newTarget = Nodes.getInObject(target, k);
                        _diff(operations, cps, v, newTarget);
                    } else {
                        operations.add(new PatchOperation(PatchOperation.STD_REMOVE, JsonPointer.fromLast(cps), null, null));
                    }
                });
                Nodes.visitObject(target, (k, v) -> {
                   if (!Nodes.containsInObject(source, k)) {
                       PathSegment cps = new PathSegment.Name(ps, null, k);
                       operations.add(new PatchOperation(PatchOperation.STD_ADD, JsonPointer.fromLast(cps), v, null));
                   }
                });
            } else if (sourceJt.isArray() && targetJt.isArray()) {
                int sourceSize = Nodes.sizeInArray(source);
                int targetSize = Nodes.sizeInArray(target);
                int size = Math.min(sourceSize, targetSize);
                for (int i = 0; i < size; i++) {
                    PathSegment cps = new PathSegment.Index(ps, null, i);
                    _diff(operations, cps, Nodes.getInArray(source, i), Nodes.getInArray(target, i));
                }
                if (targetSize > sourceSize) {  // add with '/xx/-'
                    PathSegment cps = new PathSegment.Append(ps, null);
                    for (int i = sourceSize; i < targetSize; i++) {
                        operations.add(new PatchOperation(PatchOperation.STD_ADD, JsonPointer.fromLast(cps),
                                Nodes.getInArray(target, i), null));
                    }
                }
                if (targetSize < sourceSize) {  // Remove from back to front
                    for (int i = sourceSize - 1; i >= targetSize; i--) {
                        PathSegment cps = new PathSegment.Index(ps, null, i);
                        operations.add(new PatchOperation(PatchOperation.STD_REMOVE, JsonPointer.fromLast(cps), null, null));
                    }
                }
            } else if (!Nodes.equals(source, target)) {
                operations.add(new PatchOperation(PatchOperation.STD_REPLACE, JsonPointer.fromLast(ps), target, null));
            }
        }
    }


}
