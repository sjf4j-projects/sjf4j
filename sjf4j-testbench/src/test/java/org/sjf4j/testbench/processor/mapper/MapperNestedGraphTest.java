package org.sjf4j.testbench.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.CompiledMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-level object graph regressions adapted as scenarios from MapStruct's
 * nested-bean tests, including records and recursive collection/map values.
 */
public class MapperNestedGraphTest {

    private final GraphMapper mapper = CompiledInstances.create(GraphMapper.class);

    @Test
    public void mapsRecordsInsideNestedBeansAndNestedCollections() {
        OrderTarget target = mapper.map(fixture());

        assertEquals("Ada", target.customer.name());
        assertEquals("London", target.customer.address().city());
        assertEquals("SW1", target.customer.address().zip());
        assertEquals("A", target.lines.get(0).sku());
        assertEquals(2L, target.lines.get(0).quantity());
        assertEquals(LineState.READY, target.lines.get(0).state());
        assertEquals("B", target.lines.get(1).sku());
    }

    @Test
    public void mapsIndexedAndGroupedNestedValues() {
        OrderTarget target = mapper.map(fixture());

        assertEquals(3L, target.index.get("B").quantity());
        assertEquals(LineState.NEW, target.index.get("B").state());
        assertEquals(List.of("A", "B"),
                target.groups.get("one").stream().map(LineTarget::sku).toList());
        assertEquals(2L, target.groups.get("one").get(0).quantity());
    }

    @Test
    public void missingNestedBranchesStayNull() {
        OrderTarget target = mapper.map(new OrderSource(null, null, null, null));

        assertNotNull(target);
        assertNull(target.customer);
        assertNull(target.lines);
        assertNull(target.index);
        assertNull(target.groups);
    }

    @Test
    public void emptyNestedContainersRemainEmpty() {
        OrderTarget target = mapper.map(new OrderSource(
                new CustomerSource("Ada", null),
                List.of(), Map.of(), Map.of()));

        assertNotNull(target.customer);
        assertNull(target.customer.address());
        assertTrue(target.lines.isEmpty());
        assertTrue(target.index.isEmpty());
        assertTrue(target.groups.isEmpty());
    }

    @Test
    public void generatedMappingDoesNotShareMutableContainerInstances() {
        OrderSource source = fixture();
        OrderTarget target = mapper.map(source);

        assertNotSame(source.lines(), target.lines);
        assertNotSame(source.index(), target.index);
        assertNotSame(source.groups(), target.groups);
        assertNotSame(source.groups().get("one"), target.groups.get("one"));
        source.lines().clear();
        source.index().clear();
        assertEquals(2, target.lines.size());
        assertEquals(2, target.index.size());
    }

    private static OrderSource fixture() {
        LineSource a = new LineSource("A", 2, "READY");
        LineSource b = new LineSource("B", 3, "NEW");
        List<LineSource> lines = new ArrayList<>(List.of(a, b));
        Map<String, LineSource> index = new LinkedHashMap<>();
        index.put("A", a);
        index.put("B", b);
        Map<String, List<LineSource>> groups = new LinkedHashMap<>();
        groups.put("one", new ArrayList<>(List.of(a, b)));
        return new OrderSource(new CustomerSource("Ada",
                new AddressSource("London", "SW1")), lines, index, groups);
    }

    public record OrderSource(CustomerSource customer,
                              List<LineSource> lines,
                              Map<String, LineSource> index,
                              Map<String, List<LineSource>> groups) {}
    public record CustomerSource(String name, AddressSource address) {}
    public record AddressSource(String city, String zip) {}
    public record LineSource(String sku, int quantity, String state) {}

    public record CustomerTarget(String name, AddressTarget address) {}
    public record AddressTarget(String city, String zip) {}
    public record LineTarget(String sku, long quantity, LineState state) {}
    public enum LineState { NEW, READY }

    public static final class OrderTarget {
        public CustomerTarget customer;
        public List<LineTarget> lines;
        public Map<String, LineTarget> index;
        public Map<String, List<LineTarget>> groups;
    }

    @CompiledMapper
    public interface GraphMapper {
        OrderTarget map(OrderSource source);
    }
}
