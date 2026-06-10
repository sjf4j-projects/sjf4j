package org.sjf4j.jdk17.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.compiled.CompiledNodes;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MapperJojoPathTest {
    @Test
    public void readsJojoStaticPropertiesBeforeDynamicKeys() {
        JojoPathMapper mapper = CompiledNodes.of(JojoPathMapper.class);
        Source source = new Source();
        source.jojo = new SourceJojo();
        source.jojo.setTypedName("typed-name");
        source.jojo.rawName = "static-renamed";
        source.jojo.put("dynamicName", "dynamic-name");
        source.jojo.put("size", "dynamic-size");
        source.jojo.put("rawName", "dynamic-raw-name");

        ReadTarget target = mapper.read(source);

        assertEquals("typed-name", target.typedName);
        assertEquals("dynamic-name", target.dynamicName);
        assertEquals("dynamic-size", target.size);
        assertEquals("static-renamed", target.renamed);
        assertEquals("dynamic-raw-name", target.rawName);
    }

    @Test
    public void autoMappingFallsBackToJojoDynamicKeys() {
        JojoPathMapper mapper = CompiledNodes.of(JojoPathMapper.class);
        SourceJojo source = new SourceJojo();
        source.setTypedName("typed-auto");
        source.put("dynamicAuto", "dynamic-auto");

        AutoTarget target = mapper.auto(source);

        assertEquals("typed-auto", target.typedName);
        assertEquals("dynamic-auto", target.dynamicAuto);
    }

    @Test
    public void writesJojoStaticPropertiesBeforeDynamicKeys() {
        JojoPathMapper mapper = CompiledNodes.of(JojoPathMapper.class);

        WriteTarget target = mapper.write(new WriteSource("Ada", "alias", "other"));

        assertEquals("set:Ada", target.jojo.getTypedName());
        assertEquals("alias", target.jojo.renamedField);
        assertEquals("other", target.jojo.getString("otherName"));
    }

    public static final class Source {
        public SourceJojo jojo;
    }

    public static final class ReadTarget {
        public String typedName;
        public String dynamicName;
        public String size;
        public String renamed;
        public String rawName;
    }

    public static final class AutoTarget {
        public String typedName;
        public String dynamicAuto;
    }

    public record WriteSource(String typedName, String renamed, String otherName) {}

    public static final class WriteTarget {
        public TargetJojo jojo = new TargetJojo();
    }

    public static class SourceJojo extends JsonObject {
        private String typedName;

        @NodeProperty("renamed")
        public String getRawName() {
            return rawName;
        }

        public String getTypedName() {
            return typedName;
        }

        public void setTypedName(String typedName) {
            this.typedName = typedName;
        }

        private String rawName;
    }

    public static class TargetJojo extends JsonObject {
        private String typedName;

        @NodeProperty("renamed")
        public String renamedField;

        public String getTypedName() {
            return typedName;
        }

        public void setTypedName(String typedName) {
            this.typedName = "set:" + typedName;
        }
    }

    @CompiledMapper
    public interface JojoPathMapper {
        @Mapping(target = "typedName", source = "$.jojo.typedName")
        @Mapping(target = "dynamicName", source = "$.jojo.dynamicName")
        @Mapping(target = "size", source = "$.jojo.size")
        @Mapping(target = "renamed", source = "$.jojo.renamed")
        @Mapping(target = "rawName", source = "$.jojo.rawName")
        ReadTarget read(Source source);

        AutoTarget auto(SourceJojo source);

        @Mapping(target = "$.jojo.typedName", source = "typedName")
        @Mapping(target = "$.jojo.renamed", source = "renamed")
        @Mapping(target = "$.jojo.otherName", source = "otherName")
        WriteTarget write(WriteSource source);
    }
}
