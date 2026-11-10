package org.sjf4j.testbench.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.MappingOptions;
import org.sjf4j.annotation.mapping.ReusePolicy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MapStruct-inspired assignment/identity matrix for SJF4J's ASSIGNABLE policy.
 * These tests make reference identity observable rather than checking values only.
 */
public class MapperReuseMatrixTest {

    private final ReuseMapper mapper = CompiledInstances.create(ReuseMapper.class);

    @Test
    public void sameTypedRootListPreservesReference() {
        List<String> source = new ArrayList<>(List.of("one", "two"));
        assertSame(source, mapper.sameList(source));
    }

    @Test
    public void covariantDeclaredListPreservesReference() {
        ArrayList<String> source = new ArrayList<>(List.of("one"));
        assertSame(source, mapper.arrayListToList(source));
    }

    @Test
    public void sameTypedRootMapPreservesReference() {
        Map<String, Integer> source = new LinkedHashMap<>();
        source.put("a", 1);
        assertSame(source, mapper.sameMap(source));
    }

    @Test
    public void assignableMapWithNestedValuesPreservesReference() {
        Map<String, List<String>> source = new LinkedHashMap<>();
        source.put("a", new ArrayList<>(List.of("x")));
        assertSame(source, mapper.nestedMap(source));
    }

    @Test
    public void covariantArrayPreservesReference() {
        String[] source = {"a", "b"};
        assertSame(source, mapper.covariantArray(source));
    }

    @Test
    public void nestedAssignablePojoFieldPreservesReference() {
        BaseLeaf leaf = new SpecialLeaf("Ada");
        NestedSource source = new NestedSource(leaf, null, null);
        NestedTarget target = mapper.nested(source);
        assertSame(leaf, target.leaf());
    }

    @Test
    public void nestedAssignableListPreservesReference() {
        List<String> labels = new ArrayList<>(List.of("a"));
        NestedTarget result = mapper.nested(new NestedSource(null, labels, null));
        assertSame(labels, result.labels());
        labels.add("b");
        assertEquals(List.of("a", "b"), result.labels());
    }

    @Test
    public void nestedAssignableMapPreservesReference() {
        Map<String, Integer> ratings = new LinkedHashMap<>();
        ratings.put("old", 1);
        NestedTarget result = mapper.nested(new NestedSource(null, null, ratings));
        assertSame(ratings, result.ratings());
        ratings.put("new", 2);
        assertEquals(2, result.ratings().get("new"));
    }

    @Test
    public void nestedGenericContainerFieldPreservesReference() {
        List<List<String>> source = new ArrayList<>();
        source.add(Arrays.asList("A", null));
        assertSame(source, mapper.deep(new DeepSource(source)).values());
    }

    @Test
    public void incompatibleGenericElementsAreConvertedIntoNewContainer() {
        List<ItemSource> items = new ArrayList<>(List.of(new ItemSource("Ada")));
        List<ItemTarget> mapped = mapper.items(items);
        assertNotSame(items, mapped);
        assertEquals("Ada", mapped.get(0).name());
        assertNotSame(items.get(0), mapped.get(0));
    }

    @Test
    public void nullAssignableRootIsStillNull() {
        assertNull(mapper.sameList(null));
        assertNull(mapper.sameMap(null));
        assertNull(mapper.covariantArray(null));
    }

    public static class BaseLeaf {
        public String name;
        public BaseLeaf() {}
        public BaseLeaf(String name) { this.name = name; }
    }

    public static final class SpecialLeaf extends BaseLeaf {
        public SpecialLeaf() {}
        public SpecialLeaf(String name) { super(name); }
    }

    public record NestedSource(BaseLeaf leaf, List<String> labels, Map<String, Integer> ratings) {}
    public record NestedTarget(BaseLeaf leaf, List<String> labels, Map<String, Integer> ratings) {}
    public record DeepSource(List<List<String>> values) {}
    public record DeepTarget(List<List<String>> values) {}
    public record ItemSource(String name) {}
    public record ItemTarget(String name) {}

    @CompiledMapper
    public interface ReuseMapper {
        @MappingOptions(reuse = ReusePolicy.ASSIGNABLE)
        List<String> sameList(List<String> source);

        List<String> arrayListToList(ArrayList<String> source);

        Map<String, Integer> sameMap(Map<String, Integer> source);

        Map<String, List<String>> nestedMap(Map<String, List<String>> source);

        Object[] covariantArray(String[] source);

        NestedTarget nested(NestedSource source);

        DeepTarget deep(DeepSource source);

        List<ItemTarget> items(List<ItemSource> source);
    }
}
