package org.sjf4j.processor.binding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BindingKeyTest {

    @Test
    void separatesCodecFormatAndPatternWithoutDelimiterCollisions() {
        BindingPlan.Direction write = BindingPlan.Direction.WRITE_TO;
        String type = "fixture.Value";
        BindingKey key = new BindingKey(write, type, "a:b", "c", false);
        BindingKey equal = new BindingKey(write, type, "a:b", "c", false);
        assertEquals(key, equal);
        assertEquals(key.hashCode(), equal.hashCode());
        assertNotEquals(key, new BindingKey(write, type, "a", "b:c", false));
        assertNotEquals(new BindingKey(write, type, null, null, false),
                new BindingKey(write, type, "null", null, false));
        assertNotEquals(new BindingKey(write, type, null, null, false),
                new BindingKey(write, type, null, "null", false));
    }

    @Test
    void separatesDirectionTypeAndInlineSubtypePlan() {
        String type = "fixture.Value";
        BindingPlan.Direction write = BindingPlan.Direction.WRITE_TO;
        BindingKey direct = new BindingKey(write, type, null, null, false);
        assertNotEquals(direct, new BindingKey(BindingPlan.Direction.READ_FROM, type, null, null, false));
        assertNotEquals(direct, new BindingKey(write, "fixture.Other", null, null, false));
        assertNotEquals(direct, new BindingKey(write, type, null, null, true));
    }
}
