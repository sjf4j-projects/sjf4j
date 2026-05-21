package org.sjf4j.patch;

import org.sjf4j.JsonType;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.Nodes;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * Patch utilities: RFC 7386 JSON Merge Patch, indexed deep merge, and JSON Patch diff.
 */
public final class Patches {

    /**
     * Applies indexed deep-merge semantics to {@code target} in place.
     *
     * <p>This is a practical merge utility, not RFC 7386. Object members merge by key,
     * array elements merge by index, and recursion happens only when both sides are the
     * same container shape. Otherwise the patch value is assigned according to
     * {@code overwrite}.</p>
     *
     * <p>Object {@code null} is a normal assigned value. In array-to-array merge, non-final
     * array {@code null} means skip that index; a final {@code null} means truncate to the
     * preceding length. Examples: {@code [null, 2, 3]} updates indexes 1 and 2 only,
     * {@code [1, 2, null]} becomes {@code [1, 2]}, and {@code [null]} clears the array.
     * When such a trailing-{@code null} array patch is assigned into a non-array target slot,
     * the stored value is the patch array with the sentinel removed.</p>
     *
     * <p>Use {@code overwrite=true} to replace existing non-null values, or
     * {@code overwrite=false} to fill only missing or {@code null} target values.
     * Use {@code deepCopy=true} when composite patch values should not be shared by
     * reference.</p>
     *
     * <p>Fixed-size Java arrays cannot be truncated and fail with {@code JsonException}.
     * If you need RFC 7386 semantics instead (array replace and object {@code null}
     * means remove), use {@link #mergePatch(Object, Object)}.</p>
     */
    public static void indexedMerge(Object target, Object patch, boolean overwrite, boolean deepCopy) {
        if (target == null || patch == null) return;
        JsonType targetJt = JsonType.of(target);
        JsonType patchJt = JsonType.of(patch);
        if (targetJt.isObject() && patchJt.isObject()) {
            Nodes.forEachObject(patch, (key, subPatch) -> {
                Object subTarget = Nodes.getInObject(target, key);
                JsonType subTargetJt = JsonType.of(subTarget);
                JsonType subPatchJt = JsonType.of(subPatch);
                if (subPatchJt.isObject()) {
                    if (subTargetJt.isObject()) {
                        indexedMerge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        subPatch = deepCopy ? Sjf4j.global().deepNode(subPatch) : subPatch;
                        Nodes.putInObject(target, key, subPatch);
                    }
                } else if (subPatchJt.isArray()) {
                    if (subTargetJt.isArray()) {
                        indexedMerge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        subPatch = _normalizeArrayPatch(subPatch, deepCopy);
                        Nodes.putInObject(target, key, subPatch);
                    }
                } else if (overwrite || subTarget == null) {
                    Nodes.putInObject(target, key, subPatch);
                }
            });
        } else if (targetJt.isArray() && patchJt.isArray()) {
            int patchSize = Nodes.sizeInArray(patch);
            boolean truncate = patchSize > 0 && Nodes.getInArray(patch, patchSize - 1) == null;
            int size = truncate ? patchSize - 1 : patchSize;
            for (int i = 0; i < size; i++) {
                Object subPatch = Nodes.getInArray(patch, i);
                if (subPatch == null) continue;
                Object subTarget = Nodes.getInArray(target, i);
                JsonType subTargetJt = JsonType.of(subTarget);
                JsonType subPatchJt = JsonType.of(subPatch);
                if (subPatchJt.isObject()) {
                    if (subTargetJt.isObject()) {
                        indexedMerge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        subPatch = deepCopy ? Sjf4j.global().deepNode(subPatch) : subPatch;
                        Nodes.putInArray(target, i, subPatch);
                    }
                } else if (subPatchJt.isArray()) {
                    if (subTargetJt.isArray()) {
                        indexedMerge(subTarget, subPatch, overwrite, deepCopy);
                    } else if (overwrite || subTarget == null) {
                        subPatch = _normalizeArrayPatch(subPatch, deepCopy);
                        Nodes.putInArray(target, i, subPatch);
                    }
                } else if (overwrite || subTarget == null) {
                    Nodes.putInArray(target, i, subPatch);
                }
            }
            if (truncate) {
                for (int i = Nodes.sizeInArray(target); i > size; i--) {
                    Nodes.removeInArray(target, i - 1);
                }
            }
        }
    }

    /**
     * When an array patch is assigned into a non-array target slot (e.g. replacing a
     * scalar or object with an array), trailing-null truncation sentinels are stripped
     * because they are only meaningful for array-to-array merge.
     */
    private static Object _normalizeArrayPatch(Object patch, boolean deepCopy) {
        int size = Nodes.sizeInArray(patch);
        if (size == 0 || Nodes.getInArray(patch, size - 1) != null) {
            return deepCopy ? Sjf4j.global().deepNode(patch) : patch;
        }
        Object value = deepCopy ? Sjf4j.global().deepNode(patch) : Nodes.copy(patch);
        for (int i = Nodes.sizeInArray(value); i > size - 1; i--) {
            Nodes.removeInArray(value, i - 1);
        }
        return value;
    }

    /**
     * Applies RFC 7386 JSON Merge Patch semantics and returns the possibly replaced root.
     *
     * <p>If {@code patch} is not an object, it replaces the entire target and is returned as-is.
     * If {@code patch} is an object, object members are merged recursively. A {@code null}
     * patch member means remove that member from the target object. Non-object patch values,
     * including arrays, replace the target value at that member.</p>
     *
     * <p>When the target is not an object and the patch is an object, RFC 7386 treats the target
     * as an empty object and returns a merged object result.</p>
     *
     * <p>Removal requires a removable object container such as {@link JsonObject}, {@link java.util.Map},
     * or a backend-native mutable object node. POJO fields are structural and cannot be removed;
     * an explicit {@code null} patch member for an existing POJO property will fail with
     * {@code JsonException} rather than silently setting the property to {@code null}.</p>
     *
     * <p>Use this when you need standards-compliant merge behavior instead of indexed deep merge
     * from {@link #indexedMerge(Object, Object, boolean, boolean)}.</p>
     */
    public static Object mergePatch(Object target, Object patch) {
        JsonType patchJt = JsonType.of(patch);
        if (!patchJt.isObject()) {
            return patch;
        }

        JsonType targetJt = JsonType.of(target);
        Object current = targetJt.isObject() ? target : new JsonObject();
        Nodes.forEachObject(patch, (key, subPatch) -> {
            if (subPatch == null) {
                if (Nodes.containsInObject(current, key)) {
                    Nodes.removeInObject(current, key);
                }
                return;
            }

            JsonType subPatchJt = JsonType.of(subPatch);
            if (subPatchJt.isObject()) {
                Object subTarget = Nodes.getInObject(current, key);
                Object subMerged = mergePatch(subTarget, subPatch);
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
                Nodes.forEachObject(source, (k, v) -> {
                    PathSegment cps = new PathSegment.Name(ps, k);
                    if (Nodes.containsInObject(target, k)) {
                        Object newTarget = Nodes.getInObject(target, k);
                        _diff(operations, cps, v, newTarget);
                    } else {
                        operations.add(new PatchOperation(PatchOperation.STD_REMOVE, JsonPointer.fromLast(cps), null, null));
                    }
                });
                Nodes.forEachObject(target, (k, v) -> {
                   if (!Nodes.containsInObject(source, k)) {
                       PathSegment cps = new PathSegment.Name(ps, k);
                       operations.add(new PatchOperation(PatchOperation.STD_ADD, JsonPointer.fromLast(cps), v, null));
                   }
                });
            } else if (sourceJt.isArray() && targetJt.isArray()) {
                int sourceSize = Nodes.sizeInArray(source);
                int targetSize = Nodes.sizeInArray(target);
                int size = Math.min(sourceSize, targetSize);
                for (int i = 0; i < size; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    _diff(operations, cps, Nodes.getInArray(source, i), Nodes.getInArray(target, i));
                }
                if (targetSize > sourceSize) {  // add with '/xx/-'
                    PathSegment cps = new PathSegment.Append(ps);
                    for (int i = sourceSize; i < targetSize; i++) {
                        operations.add(new PatchOperation(PatchOperation.STD_ADD, JsonPointer.fromLast(cps),
                                Nodes.getInArray(target, i), null));
                    }
                }
                if (targetSize < sourceSize) {  // Remove from back to front
                    for (int i = sourceSize - 1; i >= targetSize; i--) {
                        PathSegment cps = new PathSegment.Index(ps, i);
                        operations.add(new PatchOperation(PatchOperation.STD_REMOVE, JsonPointer.fromLast(cps), null, null));
                    }
                }
            } else if (!Nodes.equals(source, target)) {
                operations.add(new PatchOperation(PatchOperation.STD_REPLACE, JsonPointer.fromLast(ps), target, null));
            }
        }
    }


}
