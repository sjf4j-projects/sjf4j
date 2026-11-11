package org.sjf4j.testbench.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.MappingOptions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MapStruct selection/resulttype/qualifier-inspired tests, using SJF4J's
 * local/imported and method-level preferred-converter contracts.
 */
public class MapperConverterSelectionMatrixTest {

    @Test
    public void exactSourceConverterWinsOverAssignableBaseSource() {
        MostSpecificMapper mapper = CompiledInstances.create(MostSpecificMapper.class);
        LeafTarget result = mapper.convert(new SpecialEnvelope(new SpecialLeaf("A"))).value();
        assertEquals("specific:A", result.name);
    }

    @Test
    public void baseSourceConverterIsUsedForBaseDeclaredSource() {
        MostSpecificMapper mapper = CompiledInstances.create(MostSpecificMapper.class);
        LeafTarget result = mapper.convertBase(new BaseEnvelope(new BaseLeaf("B"))).value();
        assertEquals("base:B", result.name);
    }

    @Test
    public void selectedConverterRecursesIntoListElements() {
        MostSpecificMapper mapper = CompiledInstances.create(MostSpecificMapper.class);
        CollectionTarget result = mapper.convertCollection(new CollectionSource(
                List.of(new SpecialLeaf("C")),
                Map.of("x", new SpecialLeaf("D"))));
        assertEquals("specific:C", result.items().get(0).name);
    }

    @Test
    public void selectedConverterRecursesIntoMapValues() {
        MostSpecificMapper mapper = CompiledInstances.create(MostSpecificMapper.class);
        CollectionTarget result = mapper.convertCollection(new CollectionSource(
                List.of(new SpecialLeaf("C")),
                Map.of("x", new SpecialLeaf("D"))));
        assertEquals("specific:D", result.byName().get("x").name);
    }

    @Test
    public void localConverterWinsOverImportedConverter() {
        LocalVsImportedMapper mapper = CompiledInstances.create(LocalVsImportedMapper.class);
        assertEquals("local:E", mapper.convert(new SpecialEnvelope(new SpecialLeaf("E"))).value().name);
    }

    @Test
    public void mostSpecificImportedSourceWins() {
        ImportedOnlyMapper mapper = CompiledInstances.create(ImportedOnlyMapper.class);
        assertEquals("import-specific:F", mapper.convert(new SpecialEnvelope(new SpecialLeaf("F"))).value().name);
    }

    @Test
    public void preferredConvertersUseFirstCompatibleReference() {
        PreferredMapper mapper = CompiledInstances.create(PreferredMapper.class);
        assertEquals("second:G", mapper.convert(new SpecialEnvelope(new SpecialLeaf("G"))).value().name);
    }

    @Test
    public void preferredConvertersSkipIncompatibleReferences() {
        SkipIncompatibleMapper mapper = CompiledInstances.create(SkipIncompatibleMapper.class);
        assertEquals("correct:H", mapper.convert(new SpecialEnvelope(new SpecialLeaf("H"))).value().name);
    }

    @Test
    public void directAssignablePropertyWinsOverPreferredConverter() {
        DirectPriorityMapper mapper = CompiledInstances.create(DirectPriorityMapper.class);
        String name = new String("original");
        NameTarget result = mapper.convert(new NameSource(name));
        assertSame(name, result.name());
    }

    @Test
    public void converterWithExactTargetTypeWinsOverSubtypeReturn() {
        TargetPreferenceMapper mapper = CompiledInstances.create(TargetPreferenceMapper.class);
        assertEquals("exact:I", mapper.convert(new SpecialEnvelope(new SpecialLeaf("I"))).value().name);
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
    public static class LeafTarget {
        public String name;
        public LeafTarget() {}
        public LeafTarget(String name) { this.name = name; }
    }
    public static final class SpecialTarget extends LeafTarget {
        public SpecialTarget() {}
        public SpecialTarget(String name) { super(name); }
    }

    public record BaseEnvelope(BaseLeaf value) {}
    public record SpecialEnvelope(SpecialLeaf value) {}
    public record TargetEnvelope(LeafTarget value) {}
    public record CollectionSource(List<SpecialLeaf> items, Map<String, SpecialLeaf> byName) {}
    public record CollectionTarget(List<LeafTarget> items, Map<String, LeafTarget> byName) {}
    public record NameSource(String name) {}
    public record NameTarget(String name) {}

    @CompiledMapper
    public interface MostSpecificMapper {
        TargetEnvelope convert(SpecialEnvelope source);
        TargetEnvelope convertBase(BaseEnvelope source);
        CollectionTarget convertCollection(CollectionSource source);

        default LeafTarget fromBase(BaseLeaf value) {
            return new LeafTarget("base:" + value.name);
        }
        default LeafTarget fromSpecific(SpecialLeaf value) {
            return new LeafTarget("specific:" + value.name);
        }
    }

    @CompiledMapper
    public interface ImportGeneral {
        default LeafTarget fromBase(BaseLeaf value) {
            return new LeafTarget("import-base:" + value.name);
        }
    }

    @CompiledMapper
    public interface ImportSpecific {
        default LeafTarget fromSpecific(SpecialLeaf value) {
            return new LeafTarget("import-specific:" + value.name);
        }
    }

    @CompiledMapper(importing = {ImportGeneral.class})
    public interface LocalVsImportedMapper {
        TargetEnvelope convert(SpecialEnvelope source);
        default LeafTarget fromLocal(SpecialLeaf value) {
            return new LeafTarget("local:" + value.name);
        }
    }

    @CompiledMapper(importing = {ImportGeneral.class, ImportSpecific.class})
    public interface ImportedOnlyMapper {
        TargetEnvelope convert(SpecialEnvelope source);
    }

    @CompiledMapper
    public interface PreferredMapper {
        @MappingOptions(methods = {"this::second", "this::first"})
        TargetEnvelope convert(SpecialEnvelope source);
        default LeafTarget first(SpecialLeaf value) {
            return new LeafTarget("first:" + value.name);
        }
        default LeafTarget second(SpecialLeaf value) {
            return new LeafTarget("second:" + value.name);
        }
    }

    @CompiledMapper
    public interface SkipIncompatibleMapper {
        @MappingOptions(methods = {"this::wrong", "this::correct"})
        TargetEnvelope convert(SpecialEnvelope source);
        default LeafTarget wrong(Integer value) {
            return new LeafTarget("wrong:" + value);
        }
        default LeafTarget correct(SpecialLeaf value) {
            return new LeafTarget("correct:" + value.name);
        }
    }

    @CompiledMapper
    public interface DirectPriorityMapper {
        @MappingOptions(methods = {"this::decorate"})
        NameTarget convert(NameSource source);
        default String decorate(String value) {
            return "changed:" + value;
        }
    }

    @CompiledMapper
    public interface TargetPreferenceMapper {
        TargetEnvelope convert(SpecialEnvelope source);
        default LeafTarget exact(SpecialLeaf value) {
            return new LeafTarget("exact:" + value.name);
        }
        default SpecialTarget subtype(SpecialLeaf value) {
            return new SpecialTarget("subtype:" + value.name);
        }
    }
}
