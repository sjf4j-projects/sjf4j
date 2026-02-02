package org.sjf4j.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.NodeProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PojoTest {

    public static final class TestModel {

        public enum AnEnum {
            THIS,
            THAT,
            OTHER
        }

        private final String name;
        private final BigDecimal decimal;
        private final AnEnum anEnum;
        private final List<String> list;
        @NodeProperty("polymorphicTypes")
        private final Set<Integer> polyTypes;

        public TestModel(
                @JsonProperty(value = "name", required = true) final String name,
                @JsonProperty(value = "decimal", required = true) final BigDecimal decimal,
                @JsonProperty(value = "anEnum", required = true) final AnEnum anEnum,
                @JsonProperty(value = "list", required = true) final List<String> list,
                @JsonProperty(value = "polymorphicTypes", required = true)
                final Set<Integer> polyTypes) {
            this.name = name;
            this.decimal = decimal;
            this.anEnum = anEnum;
            this.list = list;
            this.polyTypes = polyTypes;
        }

        public String getName() {
            return name;
        }

        public BigDecimal getDecimal() {
            return decimal;
        }

        public AnEnum getAnEnum() {
            return anEnum;
        }

        public List<String> getList() {
            return list;
        }

        public Set<Integer> getPolymorphicTypes() {
            return polyTypes;
        }

    }

    @Test
    public void testCreatorPojoWithJsonProperty() {
        String json = "{\"name\":\"Alice\",\"decimal\":12.5,\"anEnum\":\"THIS\"," +
                "\"list\":[\"a\",\"b\"],\"polymorphicTypes\":[1,2]}";
        TestModel model = Sjf4j.fromJson(json, TestModel.class);
        assertEquals("Alice", model.getName());
        assertEquals(0, model.getDecimal().compareTo(new BigDecimal("12.5")));
        assertEquals(TestModel.AnEnum.THIS, model.getAnEnum());
        assertEquals(2, model.getList().size());
        assertEquals(2, model.getPolymorphicTypes().size());
    }

}
