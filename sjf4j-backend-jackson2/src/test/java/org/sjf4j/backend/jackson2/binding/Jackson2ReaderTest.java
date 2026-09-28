package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingReader.Token;
import org.sjf4j.exception.BindingException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Jackson2ReaderTest {

    @Test
    void readsRawNodesWithSjf4jCollectionSemantics() throws Exception {
        try (Jackson2Reader reader = reader(
                "{\"text\":\"Ada\",\"number\":7,\"enabled\":true,\"empty\":null,"
                        + "\"nested\":{\"first\":\"one\"},\"items\":[false,{\"second\":2}]}")) {

            assertEquals(Token.START_OBJECT, reader.peekToken());
            Map<?, ?> value = (Map<?, ?>) reader.readRawNode();

            assertEquals(LinkedHashMap.class, value.getClass());
            assertEquals(Arrays.asList("text", "number", "enabled", "empty", "nested", "items"),
                    Arrays.asList(value.keySet().toArray()));
            assertEquals("Ada", value.get("text"));
            assertEquals(7, value.get("number"));
            assertEquals(true, value.get("enabled"));
            assertNull(value.get("empty"));
            assertEquals(LinkedHashMap.class, value.get("nested").getClass());
            assertEquals(ArrayList.class, value.get("items").getClass());
            assertEquals(LinkedHashMap.class, ((List<?>) value.get("items")).get(1).getClass());
            assertEquals(Token.EOF, reader.peekToken());
            reader.endDocument();
        }
    }

    @Test
    void rawNodeReportsEofUsingStreamingTokenSemantics() throws Exception {
        try (Jackson2Reader reader = reader("")) {
            BindingException error = assertThrows(BindingException.class, reader::readRawNode);
            assertEquals("unexpected token 'EOF'", error.getMessage());
        }
    }

    private static Jackson2Reader reader(String json) throws Exception {
        return new Jackson2Reader(new JsonFactory().createParser(json));
    }
}
