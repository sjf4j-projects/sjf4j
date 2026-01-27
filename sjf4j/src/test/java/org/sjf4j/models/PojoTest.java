package org.sjf4j.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.sjf4j.annotation.node.NodeField;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

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
        @NodeField("polymorphicTypes")
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

}
