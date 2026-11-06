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

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@State(Scope.Benchmark)
@SuppressWarnings("FieldMayBeFinal") // not final to avoid folding.
public class ModelState {

    public static final TestModel TEST_MODEL =
            new TestModel(
                    "some name",
                    new BigDecimal("0145.000001"),
                    TestModel.AnEnum.THAT,
                    List.of(
                            "long", "long", "list", "of", "data", "so", "that", "we've", "got",
                            "some", "time", "spent", "parsing", "all", "this", "json", "data",
                            "long", "long", "list", "of", "data", "so", "that", "we've", "got",
                            "some", "time", "spent", "parsing", "all", "this", "json", "data"),
                    List.of(
                            new PolyTypeA(UUID.randomUUID()),
                            new PolyTypeA(UUID.randomUUID()),
                            new PolyTypeB(12.34000005d),
                            new PolyTypeB(0.0000000002d),
                            new PolyTypeA(UUID.randomUUID()),
                            new PolyTypeA(UUID.randomUUID()),
                            new PolyTypeB(13.34000005d),
                            new PolyTypeB(1.0000000002d)));

    public TestModel model = TEST_MODEL;
}
