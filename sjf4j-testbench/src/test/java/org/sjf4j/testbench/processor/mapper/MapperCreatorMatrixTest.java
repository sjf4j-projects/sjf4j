package org.sjf4j.testbench.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.MappingCreator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MapStruct factory/constructor/inheritance scenarios expressed with SJF4J's
 * @MappingCreator declaration and default constructor selection rules.
 */
public class MapperCreatorMatrixTest {

    @Test
    public void interfaceImplementationCreatorCreatesConcreteTarget() {
        ViewMapper mapper = CompiledInstances.create(ViewMapper.class);
        View value = mapper.create(new Source("Ada", 36));
        assertInstanceOf(DefaultView.class, value);
        assertEquals("Ada", value.getName());
    }

    @Test
    public void creatorProducesFreshInstancesForIndependentCalls() {
        ViewMapper mapper = CompiledInstances.create(ViewMapper.class);
        View first = mapper.create(new Source("Ada", 36));
        View second = mapper.create(new Source("Ada", 36));
        assertNotSame(first, second);
        assertEquals(first.getName(), second.getName());
    }

    @Test
    public void methodLevelCreatorOverridesInterfaceLevel() {
        OverrideMapper mapper = CompiledInstances.create(OverrideMapper.class);
        View value = mapper.create(new Source("Ada", 36));
        assertInstanceOf(OverrideView.class, value);
        assertEquals("Ada", value.getName());
    }

    @Test
    public void interfaceLevelCreatorIsInheritedByChildMapper() {
        ChildMapper mapper = CompiledInstances.create(ChildMapper.class);
        View value = mapper.create(new Source("Ada", 36));
        assertInstanceOf(DefaultView.class, value);
        assertEquals("Ada", value.getName());
    }

    @Test
    public void mostSpecificCreatorRuleWinsForSubtypeTarget() {
        HierarchyMapper mapper = CompiledInstances.create(HierarchyMapper.class);
        SpecialView value = mapper.special(new Source("Eve", 29));
        assertInstanceOf(SpecialViewImpl.class, value);
        assertEquals("Eve", value.getName());
    }

    @Test
    public void generalCreatorRuleStillHandlesBaseTarget() {
        HierarchyMapper mapper = CompiledInstances.create(HierarchyMapper.class);
        View value = mapper.general(new Source("Bob", 42));
        assertInstanceOf(DefaultView.class, value);
        assertEquals("Bob", value.getName());
    }

    @Test
    public void defaultFactoryInitializesTargetBeforeMappingFields() {
        FactoryMapper mapper = CompiledInstances.create(FactoryMapper.class);
        DefaultView value = mapper.create(new Source("Lin", 24));
        assertEquals("Lin", value.name);
        assertEquals("factory", value.origin);
    }

    @Test
    public void factoryRunsExactlyOncePerMappingInvocation() {
        FactoryMapper mapper = CompiledInstances.create(FactoryMapper.class);
        DefaultView first = mapper.create(new Source("one", 1));
        DefaultView second = mapper.create(new Source("two", 2));
        assertEquals("factory", first.origin);
        assertEquals("factory", second.origin);
        assertNotSame(first, second);
    }

    @Test
    public void uniqueConstructorReceivesAllSourceProperties() {
        ConstructorMapper mapper = CompiledInstances.create(ConstructorMapper.class);
        UniqueCtor value = mapper.create(new Source("Ada", 36));
        assertEquals("Ada", value.name());
        assertEquals(36, value.age());
    }

    @Test
    public void nullSourceDoesNotCallFactory() {
        FactoryMapper mapper = CompiledInstances.create(FactoryMapper.class);
        assertNull(mapper.create(null));
    }

    public record Source(String name, Integer age) {}

    public interface View {
        String getName();
    }

    public interface SpecialView extends View {}

    public static class DefaultView implements View {
        public String name;
        public String origin = "default";
        public DefaultView() {}
        public String getName() { return name; }
    }

    public static final class OverrideView extends DefaultView {
        public OverrideView() {}
    }

    public static final class SpecialViewImpl extends DefaultView implements SpecialView {
        public SpecialViewImpl() {}
    }

    public static final class UniqueCtor {
        private final String name;
        private final Integer age;
        public UniqueCtor(String name, Integer age) {
            this.name = name;
            this.age = age;
        }
        public String name() { return name; }
        public Integer age() { return age; }
    }

    @CompiledMapper
    @MappingCreator(targetType = View.class, implementation = DefaultView.class)
    public interface ViewMapper {
        View create(Source source);
    }

    @CompiledMapper
    @MappingCreator(targetType = View.class, implementation = DefaultView.class)
    public interface OverrideMapper {
        @MappingCreator(targetType = View.class, implementation = OverrideView.class)
        View create(Source source);
    }

    @CompiledMapper
    @MappingCreator(targetType = View.class, implementation = DefaultView.class)
    public interface ParentCreator {}

    @CompiledMapper
    public interface ChildMapper extends ParentCreator {
        View create(Source source);
    }

    @CompiledMapper
    @MappingCreator(targetType = View.class, implementation = DefaultView.class)
    @MappingCreator(targetType = SpecialView.class, implementation = SpecialViewImpl.class)
    public interface HierarchyMapper {
        View general(Source source);
        SpecialView special(Source source);
    }

    @CompiledMapper
    public interface FactoryMapper {
        @MappingCreator(targetType = DefaultView.class, creator = "this::newView")
        DefaultView create(Source source);

        default DefaultView newView() {
            DefaultView target = new DefaultView();
            target.origin = "factory";
            return target;
        }
    }

    @CompiledMapper
    public interface ConstructorMapper {
        UniqueCtor create(Source source);
    }
}
