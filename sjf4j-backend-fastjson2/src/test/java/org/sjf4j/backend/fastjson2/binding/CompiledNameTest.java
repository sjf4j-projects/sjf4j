package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONWriter;
import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompiledNameTest {


    @Test
    void writesCompiledNamesWithUtf8AndUtf16() {
        Fastjson2Binder binder = new Fastjson2Binder();
        UnicodeNameValue value = new UnicodeNameValue();

        String expected = "{\"中文😀\\\"\\\\\\n\\u0001\":1}";

        // UTF-16, in-memory String
        assertEquals(expected, binder.writeNodeAsString(value));

        // UTF-16, caller-owned Writer
        StringWriter text = new StringWriter();
        binder.writeNode(text, value);
        assertEquals(expected, text.toString());

        // UTF-8, caller-owned OutputStream
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        binder.writeNode(bytes, value);
        assertEquals(
                expected,
                new String(bytes.toByteArray(), StandardCharsets.UTF_8)
        );

        // Reuse cached compiled names
        ByteArrayOutputStream second = new ByteArrayOutputStream();
        binder.writeNode(second, value);
        assertEquals(
                expected,
                new String(second.toByteArray(), StandardCharsets.UTF_8)
        );
    }

    @Test
    void compiledNamesRespectWriterFeaturesOnUtf8() throws Exception {
        JSONWriter.Context context =
                JSONFactory.createWriteContext(
                        JSONWriter.Feature.UseSingleQuotes
                );

        Fastjson2Binder binder = new Fastjson2Binder(
                JSONFactory.createReadContext(), context
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        binder.writeNode(output, new Fastjson2BinderTest.NamedValue());

        assertEquals(
                "{'value':1}",
                new String(output.toByteArray(), StandardCharsets.UTF_8)
        );
    }

    static class UnicodeNameValue {
        @NodeProperty("中文😀\"\\\n\u0001")
        public int value = 1;
    }

}
