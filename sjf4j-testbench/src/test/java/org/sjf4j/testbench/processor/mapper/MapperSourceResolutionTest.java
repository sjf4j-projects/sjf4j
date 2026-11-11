package org.sjf4j.testbench.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.Mapping;
import org.sjf4j.annotation.mapping.MappingOptions;
import org.sjf4j.annotation.mapping.NullValuePolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Multi-source resolution scenarios inspired by MapStruct's multisource tests.
 * SJF4J uses explicit parameter-name qualifiers and resolves unqualified
 * property names from the first source parameter.
 */
public class MapperSourceResolutionTest {

    private final SourceMapper mapper = CompiledInstances.create(SourceMapper.class);

    @Test
    public void qualifiedNameReadsFirstSource() {
        Result result = mapper.create(first(), second(), third());
        assertEquals("first-id", result.primaryId());
    }

    @Test
    public void qualifiedNameReadsSecondSourceWhenPropertiesCollide() {
        Result result = mapper.create(first(), second(), third());
        assertEquals("second-id", result.secondaryId());
    }

    @Test
    public void unqualifiedPropertyReadsFirstSource() {
        Result result = mapper.create(first(), second(), third());
        assertEquals("first-id", result.plainId());
    }

    @Test
    public void qualifiedPropertyCanReadThirdSource() {
        Result result = mapper.create(first(), second(), third());
        assertEquals("west", result.zone());
    }

    @Test
    public void computeCombinesPropertiesWithSameNameAcrossSources() {
        Result result = mapper.create(first(), second(), third());
        assertEquals("left:right", result.combined());
    }

    @Test
    public void partiallyNullSourcesKeepValuesFromPresentSources() {
        Result result = mapper.create(null, second(), third());
        assertNull(result.primaryId());
        assertNull(result.plainId());
        assertEquals("second-id", result.secondaryId());
        assertEquals("Paris", result.city());
        assertEquals("west", result.zone());
    }

    @Test
    public void allNullSourcesReturnNull() {
        assertNull(mapper.create(null, null, null));
    }

    @Test
    public void ignoreNullsDuringMultiSourceUpdatePreservesFirstSourceFields() {
        MutableResult target = new MutableResult();
        mapper.update(target, null, second());
        assertEquals("old-id", target.id);
        assertEquals("Paris", target.city);
    }

    @Test
    public void ignoreNullsDuringMultiSourceUpdatePreservesSecondSourceFields() {
        MutableResult target = new MutableResult();
        mapper.update(target, first(), null);
        assertEquals("first-id", target.id);
        assertEquals("old-city", target.city);
    }

    @Test
    public void allNullSourcesLeaveUpdateTargetUnchanged() {
        MutableResult target = new MutableResult();
        mapper.update(target, null, null);
        assertEquals("old-id", target.id);
        assertEquals("old-city", target.city);
    }

    private static First first() {
        return new First("first-id", "left");
    }

    private static Second second() {
        return new Second("second-id", "right", "Paris");
    }

    private static Third third() {
        return new Third("west");
    }

    public record First(String id, String label) {}
    public record Second(String id, String label, String city) {}
    public record Third(String zone) {}
    public record Result(String primaryId, String secondaryId, String plainId,
                         String city, String combined, String zone) {}

    public static final class MutableResult {
        public String id = "old-id";
        public String city = "old-city";
    }

    @CompiledMapper
    public interface SourceMapper {
        @Mapping(target = "primaryId", source = "first:id")
        @Mapping(target = "secondaryId", source = "second:id")
        @Mapping(target = "plainId", source = "id")
        @Mapping(target = "city", source = "second:city")
        @Mapping(target = "combined", sources = {"first:label", "second:label"},
                compute = "(a, b) -> a + \":\" + b")
        @Mapping(target = "zone", source = "third:zone")
        Result create(First first, Second second, Third third);

        @MappingOptions(nulls = NullValuePolicy.IGNORE)
        @Mapping(target = "id", source = "first:id")
        @Mapping(target = "city", source = "second:city")
        void update(MutableResult target, First first, Second second);
    }
}
