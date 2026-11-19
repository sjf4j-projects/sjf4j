package org.sjf4j.testbench.processor.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.path.JsonPath;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A primitive property cannot have child nodes, even when a path names them. */
class CompiledFilterScalarSuffixTest {
    record Item(String name, int age) {}
    record Root(List<Item> items) {}

    @CompiledNavigator
    interface Paths {
        @FindByPath("$.items[?(!@.age.missing)].name")
        List<String> missingMember(Root root);

        @FindByPath("$.items[?(!@.age[0])].name")
        List<String> missingIndex(Root root);

        @FindByPath("$.items[?(@.age.missing == null)].name")
        List<String> missingIsNotNull(Root root);
    }

    @Test
    void invalidPrimitiveSuffixesAreMissingNotNull() {
        Root root = new Root(List.of(new Item("Ada", 18), new Item("Bob", 0)));
        Paths compiled = CompiledInstances.create(Paths.class);
        String member = "$.items[?(!@.age.missing)].name";
        String index = "$.items[?(!@.age[0])].name";
        String comparison = "$.items[?(@.age.missing == null)].name";
        assertEquals(List.of("Ada", "Bob"), compiled.missingMember(root));
        assertEquals(List.of("Ada", "Bob"), compiled.missingIndex(root));
        assertEquals(List.of(), compiled.missingIsNotNull(root));
        assertEquals(JsonPath.parse(member).find(root, Object.class), compiled.missingMember(root));
        assertEquals(JsonPath.parse(index).find(root, Object.class), compiled.missingIndex(root));
        assertEquals(JsonPath.parse(comparison).find(root, Object.class),
                compiled.missingIsNotNull(root));
    }
}
