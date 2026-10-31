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
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.sjf4j.annotation.node.NodeProperty;

import java.util.Objects;

@JsonTypeName("poly-b")
@SuppressWarnings("unused")
public final class PolyTypeB implements PolyBase {
    @com.fasterxml.jackson.annotation.JsonIgnore
    @NodeProperty("@type")
    public final String type = "poly-b";

    private final double num;

    @JsonCreator
    public PolyTypeB(@JsonProperty(value = "num", required = true) final double num) {
        this.num = num;
    }

    public double getNum() {
        return num;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PolyTypeB polyTypeB = (PolyTypeB) o;
        return Double.compare(polyTypeB.num, num) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(num);
    }
}
