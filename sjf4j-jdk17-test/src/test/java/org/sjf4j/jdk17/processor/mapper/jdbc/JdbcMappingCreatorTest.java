package org.sjf4j.jdk17.processor.mapper.jdbc;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.CompiledJdbcMapper;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.mapper.MappingCreator;
import org.sjf4j.compiled.CompiledNodes;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.sjf4j.jdk17.processor.mapper.jdbc.JdbcTestSupport.result;

class JdbcMappingCreatorTest {
    @Test
    void selectsImplementationsAndFactoriesForRows() {
        Mapper mapper = CompiledNodes.of(Mapper.class);

        assertEquals("Ada", ((ViewImpl) mapper.view(result(new String[]{"name"}, new Object[]{"Ada"}))).name);
        assertEquals("Grace", ((FactoryView) mapper.factory(result(new String[]{"name"}, new Object[]{"Grace"}))).name);
        assertEquals("Katherine", ((ViewImpl) mapper.views(result(new String[]{"name"},
                new Object[]{"Katherine"})).get(0)).name);
        assertEquals("Dorothy", ((PathView) mapper.path(result(new String[]{"name"},
                new Object[]{"Dorothy"}))).profile.name);
        assertEquals("Record", ((RecordView) mapper.record(result(new String[]{"name"},
                new Object[]{"Record"}))).name());
        assertEquals("Constructor", ((SingleView) mapper.single(result(new String[]{"name"},
                new Object[]{"Constructor"}))).name);
        assertEquals("Static", ((StaticView) mapper.inheritedStatic(result(new String[]{"name"},
                new Object[]{"Static"}))).name);
        assertEquals("Default", ((DefaultView) mapper.inheritedDefault(result(new String[]{"name"},
                new Object[]{"Default"}))).name);
    }

    public interface View {
    }

    public static class ViewImpl implements View {
        public String name;

        public ViewImpl() {
        }
    }

    public static class FactoryView implements View {
        public String name;

        public FactoryView() {
        }
    }

    public static class PathView implements View {
        public CreatorProfile profile = new CreatorProfile();

        public PathView() {
        }
    }

    public static class CreatorProfile {
        public String name;
    }

    public interface RecordTarget {
    }

    public record RecordView(String name) implements RecordTarget {
    }

    public interface SingleTarget {
    }

    public static class SingleView implements SingleTarget {
        public final String name;

        public SingleView(String name) {
            this.name = name;
        }
    }

    public interface StaticTarget {
    }

    public static class StaticView implements StaticTarget {
        public String name;

        public StaticView() {
        }
    }

    public interface DefaultTarget {
    }

    public static class DefaultView implements DefaultTarget {
        public String name;

        public DefaultView() {
        }
    }

    @MappingCreator(targetType = View.class, implementation = ViewImpl.class)
    interface CreatorParent {
    }

    @MappingCreator(targetType = StaticTarget.class, creator = "this::newStatic")
    interface StaticCreatorParent {
        static StaticView newStatic() {
            return new StaticView();
        }
    }

    @MappingCreator(targetType = DefaultTarget.class, creator = "this::newDefault")
    interface DefaultCreatorParent {
    }

    @CompiledJdbcMapper
    interface Mapper extends CreatorParent, StaticCreatorParent, DefaultCreatorParent {
        View view(ResultSet rs);

        @MappingCreator(targetType = View.class, creator = "this::newView")
        View factory(ResultSet rs);

        List<View> views(ResultSet rs);

        @MappingCreator(targetType = View.class, creator = "this::newPath")
        @Mapping(target = "$.profile.name")
        View path(ResultSet rs);

        @MappingCreator(targetType = RecordTarget.class, implementation = RecordView.class)
        RecordTarget record(ResultSet rs);

        @MappingCreator(targetType = SingleTarget.class, implementation = SingleView.class)
        SingleTarget single(ResultSet rs);

        StaticTarget inheritedStatic(ResultSet rs);

        DefaultTarget inheritedDefault(ResultSet rs);

        default FactoryView newView() {
            return new FactoryView();
        }

        default DefaultView newDefault() {
            return new DefaultView();
        }

        static PathView newPath() {
            return new PathView();
        }
    }
}
