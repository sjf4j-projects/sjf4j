package org.sjf4j;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class Sjf4jTargetValidationTest {
    @Test
    void typedTargetsRejectNullBeforeBindingOrNullInputValues() {
        Sjf4j sjf4j = Sjf4j.global();
        Class<String> nullClass = null;
        TypeReference<String> nullType = null;
        byte[] jsonBytes = "null".getBytes(StandardCharsets.UTF_8);

        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromJson(new StringReader("null"), nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromJson(new StringReader("null"), nullType));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromJson("null", nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromJson("null", nullType));
        assertThrowsExactly(NullPointerException.class,
                () -> sjf4j.fromJson(new ByteArrayInputStream(jsonBytes), nullClass));
        assertThrowsExactly(NullPointerException.class,
                () -> sjf4j.fromJson(new ByteArrayInputStream(jsonBytes), nullType));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromJson(jsonBytes, nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromJson(jsonBytes, nullType));

        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromYaml(new StringReader("null"), nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromYaml(new StringReader("null"), nullType));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromYaml("null", nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromYaml("null", nullType));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromNode(null, nullClass, false));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromNode(null, nullType, false));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromProperties(new Properties(), nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromProperties(new Properties(), nullType));
    }
}
