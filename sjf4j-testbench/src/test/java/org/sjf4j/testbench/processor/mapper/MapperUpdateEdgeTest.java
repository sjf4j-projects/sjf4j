package org.sjf4j.testbench.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.ArrayPolicy;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.EnsureMapping;
import org.sjf4j.annotation.mapping.Mapping;
import org.sjf4j.annotation.mapping.MappingOptions;
import org.sjf4j.annotation.mapping.NullValuePolicy;
import org.sjf4j.annotation.mapping.ObjectPolicy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Combination regressions for mapping updates: null policy, collection/map
 * policies, ignored properties, and ensure-path behavior.
 */
public class MapperUpdateEdgeTest {

    private final EdgeUpdateMapper mapper = CompiledInstances.create(EdgeUpdateMapper.class);

    @Test
    public void appendPolicyPreservesExistingListElements() {
        UpdateTarget target = target();
        List<Long> original = target.values;
        mapper.merge(target, new UpdateSource(List.of(1, 2), null, null, null));

        assertSame(original, target.values);
        assertEquals(List.of(9L, 1L, 2L), target.values);
    }

    @Test
    public void combinedNullIgnoreRetainsContainersAndTheirIdentity() {
        UpdateTarget target = target();
        List<Long> values = target.values;
        Map<String, String> flags = target.flags;
        ChildTarget child = target.child;

        mapper.merge(target, new UpdateSource(null, null, null, null));

        assertSame(values, target.values);
        assertSame(flags, target.flags);
        assertSame(child, target.child);
        assertEquals("seed", target.name);
    }

    @Test
    public void putIfAbsentKeepsExistingMapValues() {
        UpdateTarget target = target();
        mapper.merge(target, new UpdateSource(null,
                Map.of("keep", "replacement", "new", "added"), null, null));

        assertEquals("existing", target.flags.get("keep"));
        assertEquals("added", target.flags.get("new"));
    }

    @Test
    public void putIfAbsentReplacesNullEntryValues() {
        UpdateTarget target = target();
        target.flags.put("blank", null);
        mapper.merge(target, new UpdateSource(null, Map.of("blank", "filled"), null, null));

        assertEquals("filled", target.flags.get("blank"));
        assertEquals("existing", target.flags.get("keep"));
    }

    @Test
    public void setPolicyOverwritesExistingListIndices() {
        UpdateTarget target = target();
        target.values.add(8L);
        mapper.overwrite(target, new UpdateSource(List.of(1, 2), null, "new", null));

        assertEquals(List.of(1L, 2L), target.values.subList(0, 2));
        assertEquals("new", target.name);
    }

    @Test
    public void setNullPolicyClearsCollectionAndNestedObject() {
        UpdateTarget target = target();
        mapper.overwrite(target, new UpdateSource(null, null, null, null));

        assertNull(target.values);
        assertNull(target.flags);
        assertNull(target.child);
        assertNull(target.name);
    }

    @Test
    public void explicitIgnoreKeepsTargetValueOnUpdate() {
        UpdateTarget target = target();
        mapper.ignoreName(target, new UpdateSource(null, null, "should-not-appear", null));
        assertEquals("seed", target.name);
    }

    @Test
    public void ensurePathReusesExistingParent() {
        UpdateTarget target = target();
        ChildTarget existing = target.child;

        mapper.ensureName(target, new NameSource("updated"));

        assertSame(existing, target.child);
        assertEquals("updated", target.child.label);
    }

    @Test
    public void ensurePathCreatesMissingParent() {
        UpdateTarget target = target();
        target.child = null;

        mapper.ensureName(target, new NameSource("created"));

        assertEquals("created", target.child.label);
    }

    @Test
    public void nullIgnorePreservesNestedTargetInstance() {
        UpdateTarget target = target();
        ChildTarget existing = target.child;

        mapper.preserveChild(target, new UpdateSource(null, null, null, null));

        assertSame(existing, target.child);
        assertEquals("original", existing.label);
    }

    private static UpdateTarget target() {
        UpdateTarget target = new UpdateTarget();
        target.values = new ArrayList<>(List.of(9L));
        target.flags = new LinkedHashMap<>();
        target.flags.put("keep", "existing");
        target.name = "seed";
        target.child = new ChildTarget();
        target.child.label = "original";
        return target;
    }

    public record UpdateSource(List<Integer> values, Map<String, String> flags,
                               String name, ChildSource child) {}
    public record ChildSource(String label) {}
    public record NameSource(String name) {}

    public static final class UpdateTarget {
        public List<Long> values;
        public Map<String, String> flags;
        public String name;
        public ChildTarget child;
    }

    public static final class ChildTarget {
        public String label;
    }

    @CompiledMapper
    public interface EdgeUpdateMapper {
        @MappingOptions(arrays = ArrayPolicy.ADD,
                objects = ObjectPolicy.PUT_IF_ABSENT,
                nulls = NullValuePolicy.IGNORE)
        void merge(UpdateTarget target, UpdateSource source);

        @MappingOptions(arrays = ArrayPolicy.SET,
                objects = ObjectPolicy.PUT,
                nulls = NullValuePolicy.SET_TO_NULL)
        void overwrite(UpdateTarget target, UpdateSource source);

        @Mapping(target = "name", ignore = true)
        void ignoreName(UpdateTarget target, UpdateSource source);

        @EnsureMapping(target = "$.child.label", source = "name")
        void ensureName(UpdateTarget target, NameSource source);

        @MappingOptions(nulls = NullValuePolicy.IGNORE)
        void preserveChild(UpdateTarget target, UpdateSource source);
    }
}
