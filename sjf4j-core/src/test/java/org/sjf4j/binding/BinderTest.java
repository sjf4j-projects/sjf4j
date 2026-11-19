package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.NodeConfig;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BinderTest {
    @Test
    void readsUtf8BytesAndWritesUtf8Bytes() {
        SimpleJsonBinder binder = new SimpleJsonBinder(NodeConfig.DEFAULT);

        assertEquals("héllo", binder.readNode("\"héllo\"".getBytes(StandardCharsets.UTF_8), String.class));
        assertEquals("\"héllo\"", new String(binder.writeNodeAsBytes("héllo"), StandardCharsets.UTF_8));
    }
}
