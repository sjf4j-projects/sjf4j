package org.sjf4j.jdk17.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.compiled.CompiledNodes;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MapperValueTest {

    @Test
    public void convertsNumberFamily() {
        ScalarMapper mapper = CompiledNodes.of(ScalarMapper.class);

        NumericTarget target = mapper.numbers(new NumericSource());

        assertEquals(12L, target.longValue());
        assertEquals(12, target.intValue());
        assertEquals((short) 12, target.shortValue());
        assertEquals((byte) 12, target.byteValue());
        assertEquals(12D, target.doubleValue());
        assertEquals(12F, target.floatValue());
        assertEquals(BigInteger.valueOf(12), target.bigIntegerValue());
        assertEquals(BigDecimal.valueOf(12), target.bigDecimalValue());
        assertEquals(12, target.numberValue());

        assertEquals(7L, target.objectLong());
        assertEquals(7, target.objectInt());
        assertEquals((short) 7, target.objectShort());
        assertEquals((byte) 7, target.objectByte());
        assertEquals(7D, target.objectDouble());
        assertEquals(7F, target.objectFloat());
        assertEquals(BigInteger.valueOf(7), target.objectBigInteger());
        assertEquals(BigDecimal.valueOf(7), target.objectBigDecimal());
        assertEquals(7L, target.objectNumber());
    }

    @Test
    public void convertsStringCharacterEnumBooleanFamily() {
        ScalarMapper mapper = CompiledNodes.of(ScalarMapper.class);

        TextTarget target = mapper.text(new TextSource());

        assertEquals("A", target.characterToString());
        assertEquals("ACTIVE", target.enumToString());
        assertEquals("ACTIVE", target.objectEnumToString());
        assertEquals('Z', target.stringToCharacter());
        assertEquals('Y', target.objectStringToCharacter());
        assertEquals(Letter.A, target.characterToEnum());
        assertEquals(Status.ACTIVE, target.stringToEnum());
        assertEquals(Status.ACTIVE, target.objectStringToEnum());
        assertEquals(Status.ACTIVE, target.enumToEnum());
        assertEquals(Boolean.TRUE, target.objectBoolean());
        assertEquals(5, target.primitiveInt());
    }

    @Test
    public void mapsApplicationPayloadWithStrictScalarLeaves() {
        ScalarMapper mapper = CompiledNodes.of(ScalarMapper.class);

        ApplicationDto dto = mapper.application(new ApplicationPayload(
                Map.of(
                        "id", Integer.valueOf(100),
                        "status", "ACTIVE",
                        "initial", Character.valueOf('A'),
                        "enabled", Boolean.TRUE),
                List.of(1, 2),
                Map.of("views", Long.valueOf(7))));

        assertEquals(100L, dto.id());
        assertEquals(Status.ACTIVE, dto.status());
        assertEquals("A", dto.initial());
        assertEquals(Boolean.TRUE, dto.enabled());
        assertEquals(List.of(1L, 2L), dto.scores());
        assertEquals(7, dto.metrics().get("views"));
    }

    @Test
    public void recursivelyConvertsCollectionAndMapScalarLeaves() {
        ScalarMapper mapper = CompiledNodes.of(ScalarMapper.class);

        assertEquals(List.of(1L, 2L), mapper.longs(List.of(1, 2)));
        assertEquals(List.of(List.of(1L), List.of(2L, 3L)),
                mapper.nestedLongs(List.of(List.of(1), List.of(2, 3))));

        Map<String, Object> rawInts = new LinkedHashMap<>();
        rawInts.put("a", Long.valueOf(1));
        rawInts.put("b", Integer.valueOf(2));
        assertEquals(Map.of("a", 1, "b", 2), mapper.ints(rawInts));

        Map<String, List<Object>> grouped = new LinkedHashMap<>();
        grouped.put("x", List.of(Long.valueOf(4), Integer.valueOf(5)));
        assertEquals(Map.of("x", List.of(4, 5)), mapper.groupedInts(grouped));

        List<Map<String, Object>> statusMaps = List.of(Map.of("state", "ACTIVE"));
        assertEquals(List.of(Map.of("state", Status.ACTIVE)), mapper.statusMaps(statusMaps));
    }

    @Test
    public void updatesCollectionAndMapScalarLeavesInPlace() {
        ScalarMapper mapper = CompiledNodes.of(ScalarMapper.class);

        List<Long> longs = new ArrayList<>(List.of(99L));
        mapper.updateLongs(longs, List.of(1, 2));
        assertEquals(List.of(1L, 2L), longs);

        Map<String, Integer> ints = new LinkedHashMap<>();
        ints.put("old", 99);
        mapper.updateInts(ints, Map.of("new", Long.valueOf(3)));
        assertEquals(99, ints.get("old"));
        assertEquals(3, ints.get("new"));
    }

    public static class NumericSource {
        public Number number = Integer.valueOf(12);
        public Object objectNumber = Long.valueOf(7);
    }

    public record NumericTarget(Long longValue, Integer intValue, Short shortValue, Byte byteValue,
                                Double doubleValue, Float floatValue, BigInteger bigIntegerValue,
                                BigDecimal bigDecimalValue, Number numberValue,
                                Long objectLong, Integer objectInt, Short objectShort, Byte objectByte,
                                Double objectDouble, Float objectFloat, BigInteger objectBigInteger,
                                BigDecimal objectBigDecimal, Number objectNumber) {}

    public static class TextSource {
        public Character characterToString = Character.valueOf('A');
        public SourceStatus enumToString = SourceStatus.ACTIVE;
        public Object objectEnumToString = SourceStatus.ACTIVE;
        public String stringToCharacter = "Zed";
        public Object objectStringToCharacter = "Yes";
        public Character characterToEnum = Character.valueOf('A');
        public String stringToEnum = "ACTIVE";
        public Object objectStringToEnum = "ACTIVE";
        public SourceStatus enumToEnum = SourceStatus.ACTIVE;
        public Object objectBoolean = Boolean.TRUE;
        public Object primitiveInt = Integer.valueOf(5);
    }

    public record TextTarget(String characterToString, String enumToString, String objectEnumToString,
                             Character stringToCharacter, Character objectStringToCharacter,
                             Letter characterToEnum, Status stringToEnum, Status objectStringToEnum,
                             Status enumToEnum, Boolean objectBoolean, int primitiveInt) {}

    public enum SourceStatus { ACTIVE }
    public enum Status { ACTIVE }
    public enum Letter { A }

    public record ApplicationPayload(Map<String, Object> payload, List<Integer> scores, Map<String, Object> metrics) {}

    public record ApplicationDto(Long id, Status status, String initial, Boolean enabled,
                                 List<Long> scores, Map<String, Integer> metrics) {}

    @CompiledMapper
    public interface ScalarMapper {
        @Mapping(target = "longValue", source = "number")
        @Mapping(target = "intValue", source = "number")
        @Mapping(target = "shortValue", source = "number")
        @Mapping(target = "byteValue", source = "number")
        @Mapping(target = "doubleValue", source = "number")
        @Mapping(target = "floatValue", source = "number")
        @Mapping(target = "bigIntegerValue", source = "number")
        @Mapping(target = "bigDecimalValue", source = "number")
        @Mapping(target = "numberValue", source = "number")
        @Mapping(target = "objectLong", source = "objectNumber")
        @Mapping(target = "objectInt", source = "objectNumber")
        @Mapping(target = "objectShort", source = "objectNumber")
        @Mapping(target = "objectByte", source = "objectNumber")
        @Mapping(target = "objectDouble", source = "objectNumber")
        @Mapping(target = "objectFloat", source = "objectNumber")
        @Mapping(target = "objectBigInteger", source = "objectNumber")
        @Mapping(target = "objectBigDecimal", source = "objectNumber")
        NumericTarget numbers(NumericSource source);

        TextTarget text(TextSource source);

        List<Long> longs(List<Integer> source);

        List<List<Long>> nestedLongs(List<List<Integer>> source);

        Map<String, Integer> ints(Map<String, Object> source);

        Map<String, List<Integer>> groupedInts(Map<String, List<Object>> source);

        List<Map<String, Status>> statusMaps(List<Map<String, Object>> source);

        void updateLongs(List<Long> target, List<Integer> source);

        void updateInts(Map<String, Integer> target, Map<String, Object> source);

        @Mapping(target = "id", source = "$.payload.id")
        @Mapping(target = "status", source = "$.payload.status")
        @Mapping(target = "initial", source = "$.payload.initial")
        @Mapping(target = "enabled", source = "$.payload.enabled")
        ApplicationDto application(ApplicationPayload source);
    }
}
