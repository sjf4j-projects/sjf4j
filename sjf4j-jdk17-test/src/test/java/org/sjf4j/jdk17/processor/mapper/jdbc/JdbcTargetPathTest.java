package org.sjf4j.jdk17.processor.mapper.jdbc;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.jdbc.CompiledJdbcMapper;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.exception.JsonException;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sjf4j.jdk17.processor.mapper.jdbc.JdbcTestSupport.result;

class JdbcTargetPathTest {
    @Test
    void mapsNestedTargetPathsWithoutAllocatingParents() {
        Mapper mapper = CompiledNodes.instanceOf(Mapper.class);

        assertEquals("Ada", mapper.nested(result(new String[]{"full_name"},
                new Object[]{"Ada"})).getProfile().getName());
        assertEquals("Grace", mapper.nestedAlias(result(new String[]{"full_name"},
                new Object[]{"Grace"})).getProfile().getName());

        JsonException missing = assertThrows(JsonException.class,
                () -> mapper.nullNested(result(new String[]{"full_name"}, new Object[]{"Ada"})));
        assertEquals("Missing target path parent: $.profile.name", missing.getMessage());
    }

    @Test
    void mapsSingleNameTargetPathAsAColumnAlias() {
        Mapper mapper = CompiledNodes.instanceOf(Mapper.class);

        assertEquals("Ada", mapper.rootPath(result(new String[]{"full_name"}, new Object[]{"Ada"})).name);
        assertEquals("Grace", mapper.rootPointer(result(new String[]{"full_name"}, new Object[]{"Grace"})).name);
    }

    @CompiledJdbcMapper
    interface Mapper {
        @Mapping(target = "$.profile.name", source = "full_name")
        ComplexUser nested(ResultSet rs);

        @Mapping(target = "/profile/name", source = "$.full_name")
        ComplexUser nestedAlias(ResultSet rs);

        @Mapping(target = "$.profile.name", source = "full_name")
        NullComplexUser nullNested(ResultSet rs);

        @Mapping(target = "$.name", source = "full_name")
        RootName rootPath(ResultSet rs);

        @Mapping(target = "name", source = "/full_name")
        RootName rootPointer(ResultSet rs);
    }

    public static final class ComplexUser {
        private Profile profile = new Profile();

        public ComplexUser() {
        }

        public Profile getProfile() {
            return profile;
        }
    }

    public static final class NullComplexUser {
        private Profile profile;

        public NullComplexUser() {
        }

        public Profile getProfile() {
            return profile;
        }
    }

    public static final class Profile {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static final class RootName {
        public String name;

        public RootName() {
        }
    }
}
