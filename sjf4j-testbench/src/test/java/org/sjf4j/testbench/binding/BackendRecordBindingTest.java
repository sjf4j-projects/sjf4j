package org.sjf4j.testbench.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.Binder;
import org.sjf4j.exception.BindingException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackendRecordBindingTest {

    @Test
    void fastjson2MatchesRecordCreatorArgumentsAndAliases() {
        assertRecordCreator(new Fastjson2Binder());
    }

    @Test
    void jackson2MatchesRecordCreatorArgumentsAndAliases() {
        assertRecordCreator(new Jackson2Binder());
    }

    @Test
    void jackson3MatchesRecordCreatorArgumentsAndAliases() {
        assertRecordCreator(new Jackson3Binder());
    }

    @Test
    void gsonMatchesRecordCreatorArgumentsAndAliases() {
        assertRecordCreator(new GsonBinder());
    }

    @Test
    void jsonpMatchesRecordCreatorArgumentsAndAliases() {
        assertRecordCreator(new JsonpBinder());
    }

    private static void assertRecordCreator(Binder<?, ?> binder) {
        CreatorRecord value = (CreatorRecord) binder.readNode(
                "{\"unknown\":{\"nested\":[1,2]},\"age\":7,\"legacy_name\":\"Ada\"}",
                CreatorRecord.class);

        assertEquals(new CreatorRecord("Ada", 7), value);
        assertThrows(BindingException.class, () -> binder.readNode(
                "{\"name\":\"first\",\"legacy_name\":\"duplicate\",\"age\":7}",
                CreatorRecord.class));
    }

    record CreatorRecord(@NodeProperty(value = "name", aliases = "legacy_name") String name, int age) {}
}
