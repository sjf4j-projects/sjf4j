package org.sjf4j.testbench.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.Mapping;
import org.sjf4j.annotation.mapping.MappingOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enum name-matching and explicitly selected converter contracts.
 * Enum constant declaration order and toString() are not mapping keys.
 */
public class MapperEnumMappingTest {

    private final EnumMapper mapper = CompiledInstances.create(EnumMapper.class);

    @Test
    public void matchesEnumConstantsByNameRatherThanOrdinalOrToString() {
        SourceRecord source = new SourceRecord(SourceStatus.OPEN, SourceStatus.CLOSED, null);
        TargetRecord target = mapper.map(source);

        assertEquals(TargetStatus.OPEN, target.state());
        assertEquals("OPEN", target.text());
        assertSame(SourceStatus.CLOSED, target.same());
        assertNull(target.optional());
        assertNotEquals(SourceStatus.OPEN.ordinal(), TargetStatus.OPEN.ordinal());
        assertNotEquals("OPEN", SourceStatus.OPEN.toString());
    }

    @Test
    public void mapsEnumElementsInListsAndMapValues() {
        List<SourceStatus> source = new ArrayList<>(Arrays.asList(
                SourceStatus.OPEN, null, SourceStatus.CLOSED));
        List<TargetStatus> mapped = mapper.states(source);

        assertNotSame(source, mapped);
        assertEquals(Arrays.asList(TargetStatus.OPEN, null, TargetStatus.CLOSED), mapped);

        Map<String, SourceStatus> values = new LinkedHashMap<>();
        values.put("open", SourceStatus.OPEN);
        values.put("null", null);
        values.put("closed", SourceStatus.CLOSED);
        Map<String, TargetStatus> mappedValues = mapper.values(values);

        assertNotSame(values, mappedValues);
        assertEquals(TargetStatus.OPEN, mappedValues.get("open"));
        assertTrue(mappedValues.containsKey("null"));
        assertNull(mappedValues.get("null"));
        assertEquals(TargetStatus.CLOSED, mappedValues.get("closed"));
    }

    @Test
    public void mapsNonStringEnumMapKeysByName() {
        Map<SourceStatus, String> source = new LinkedHashMap<>();
        source.put(SourceStatus.OPEN, "first");
        source.put(SourceStatus.CLOSED, "last");

        Map<TargetStatus, String> mapped = mapper.keys(source);

        assertNotSame(source, mapped);
        assertEquals("first", mapped.get(TargetStatus.OPEN));
        assertEquals("last", mapped.get(TargetStatus.CLOSED));
        assertEquals(List.of(TargetStatus.OPEN, TargetStatus.CLOSED),
                new ArrayList<>(mapped.keySet()));
    }

    @Test
    public void sameTypeEnumPropertiesAreReused() {
        SourceRecord source = new SourceRecord(SourceStatus.OPEN, SourceStatus.CLOSED, null);
        assertSame(source.same(), mapper.map(source).same());
    }

    @Test
    public void customMethodOverridesDefaultEnumMappingWithDifferentNames() {
        AutoMapper auto = CompiledInstances.create(AutoMapper.class);
        assertEquals(NewStatus.ACTIVE, auto.map(new LegacyRecord(LegacyStatus.ENABLED)).status());
        assertEquals(NewStatus.INACTIVE, auto.map(new LegacyRecord(LegacyStatus.DISABLED)).status());
    }

    @Test
    public void methodsOptionSelectsSpecificEnumConverter() {
        PreferredMapper preferred = CompiledInstances.create(PreferredMapper.class);
        assertEquals(NewStatus.INACTIVE,
                preferred.map(new LegacyRecord(LegacyStatus.ENABLED)).status());
    }

    public enum SourceStatus {
        OPEN, CLOSED;

        @Override
        public String toString() {
            return "display-" + name();
        }
    }

    // Deliberately different declaration order and one extra target constant.
    public enum TargetStatus { CLOSED, OPEN, EXTRA }

    public enum LegacyStatus { ENABLED, DISABLED }
    public enum NewStatus { INACTIVE, ACTIVE }

    public record SourceRecord(SourceStatus state, SourceStatus same, SourceStatus optional) {}
    public record TargetRecord(TargetStatus state, String text,
                               SourceStatus same, TargetStatus optional) {}

    public record LegacyRecord(LegacyStatus status) {}
    public record NewRecord(NewStatus status) {}

    @CompiledMapper
    public interface EnumMapper {
        @Mapping(target = "text", source = "state")
        TargetRecord map(SourceRecord source);

        List<TargetStatus> states(List<SourceStatus> source);

        Map<String, TargetStatus> values(Map<String, SourceStatus> source);

        Map<TargetStatus, String> keys(Map<SourceStatus, String> source);
    }

    @CompiledMapper
    public interface AutoMapper {
        NewRecord map(LegacyRecord source);

        default NewStatus translate(LegacyStatus source) {
            if (source == null) return null;
            return source == LegacyStatus.ENABLED ? NewStatus.ACTIVE : NewStatus.INACTIVE;
        }
    }

    @CompiledMapper
    public interface PreferredMapper {
        @MappingOptions(methods = {"this::second"})
        NewRecord map(LegacyRecord source);

        default NewStatus first(LegacyStatus source) {
            return source == null ? null : NewStatus.ACTIVE;
        }

        default NewStatus second(LegacyStatus source) {
            return source == null ? null : NewStatus.INACTIVE;
        }
    }
}
