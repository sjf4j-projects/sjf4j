/*
 * Copyright 2023-2025 Creek Contributors (https://github.com/creek-service)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sjf4j.testbench.model.creek;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class TestModel {

    public enum AnEnum {
        THIS,
        THAT,
        OTHER
    }

    private final String name;
    private final BigDecimal decimal;
    private final AnEnum anEnum;
    private final List<String> list;
    private final List<PolyBase> polyTypes;

    @JsonCreator
    public TestModel(
            @JsonProperty(value = "name", required = true) final String name,
            @JsonProperty(value = "decimal", required = true) final BigDecimal decimal,
            @JsonProperty(value = "anEnum", required = true) final AnEnum anEnum,
            @JsonProperty(value = "list", required = true) final List<String> list,
            @JsonProperty(value = "polymorphicTypes", required = true)
                    final List<PolyBase> polyTypes) {
        this.name = name;
        this.decimal = decimal;
        this.anEnum = anEnum;
        this.list = List.copyOf(list);
        this.polyTypes = List.copyOf(polyTypes);
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
        return List.copyOf(list);
    }

    public List<PolyBase> getPolymorphicTypes() {
        return List.copyOf(polyTypes);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TestModel testModel = (TestModel) o;
        return Objects.equals(name, testModel.name)
                && Objects.equals(decimal, testModel.decimal)
                && Objects.equals(list, testModel.list)
                && Objects.equals(polyTypes, testModel.polyTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, decimal, list, polyTypes);
    }
}
