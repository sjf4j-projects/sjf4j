package org.sjf4j.backend.jsonp.binding;

import jakarta.json.stream.JsonParser;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.Numbers;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * StreamingReader backed directly by a JSON-P {@link JsonParser}.
 *
 * <p>Requires Jakarta JSON Processing API 2.1 or later. A supplied parser
 * retains its current event when {@code currentEvent()} is supported. When
 * that method is unsupported, the parser must not have been advanced before
 * construction, since its previous position cannot be recovered.</p>
 */
public final class JsonpReader extends StreamingReader {

    private final JsonParser parser;
    private JsonParser.Event current;

    public JsonpReader(JsonParser parser) {
        super(Backend.JSONP);
        this.parser = Asserts.notNull(parser, "parser");
        try {
            current = parser.currentEvent();
        } catch (UnsupportedOperationException ignored) {
            advance();
            return;
        }
        if (current == null) {
            advance();
        }
    }

    @Override
    public Token peekToken() {
        return token(current);
    }

    @Override
    public boolean nextIfNull() {
        return nextIf(JsonParser.Event.VALUE_NULL);
    }

    @Override
    public boolean nextIfObjectStart() {
        return nextIf(JsonParser.Event.START_OBJECT);
    }

    @Override
    public boolean nextIfObjectEnd() {
        return nextIf(JsonParser.Event.END_OBJECT);
    }

    @Override
    public boolean nextIfArrayStart() {
        return nextIf(JsonParser.Event.START_ARRAY);
    }

    @Override
    public boolean nextIfArrayEnd() {
        return nextIf(JsonParser.Event.END_ARRAY);
    }

    @Override
    public void startObject() throws IOException {
        require(JsonParser.Event.START_OBJECT, "object start");
        advance();
    }

    @Override
    public void endObject() throws IOException {
        require(JsonParser.Event.END_OBJECT, "object end");
        advance();
    }

    @Override
    public void startArray() throws IOException {
        require(JsonParser.Event.START_ARRAY, "array start");
        advance();
    }

    @Override
    public void endArray() throws IOException {
        require(JsonParser.Event.END_ARRAY, "array end");
        advance();
    }

    @Override
    public String nextName() throws IOException {
        if (current == JsonParser.Event.END_OBJECT) {
            advance();
            return null;
        }
        require(JsonParser.Event.KEY_NAME, "name");
        String name = parser.getString();
        advance();
        return name;
    }

    @Override
    public String readString() throws IOException {
        if (nextIfNull()) {
            return null;
        }
        require(JsonParser.Event.VALUE_STRING, "string or null");
        String value = parser.getString();
        advance();
        return value;
    }

    @Override
    public Number readNumber() throws IOException {
        if (nextIfNull()) {
            return null;
        }
        require(JsonParser.Event.VALUE_NUMBER, "number or null");
        Number value = Numbers.parseNumber(parser.getString());
        advance();
        return value;
    }

    @Override
    public long readLongValue() throws IOException {
        BigDecimal value = number();
        advance();
        return Numbers.toLong(value);
    }

    @Override
    public int readIntValue() throws IOException {
        int value = parser.getInt();
        advance();
        return value;
    }

    @Override
    public short readShortValue() throws IOException {
        int value = parser.getInt();
        advance();
        return Numbers.toShort(value);
    }

    @Override
    public byte readByteValue() throws IOException {
        int value = parser.getInt();
        advance();
        return Numbers.toByte(value);
    }

    @Override
    public double readDoubleValue() throws IOException {
        BigDecimal value = number();
        advance();
        return Numbers.toDouble(value);
    }

    @Override
    public float readFloatValue() throws IOException {
        BigDecimal value = number();
        advance();
        return Numbers.toFloat(value);
    }

    @Override
    public boolean readBooleanValue() throws IOException {
        if (current != JsonParser.Event.VALUE_TRUE && current != JsonParser.Event.VALUE_FALSE) {
            throw expected("boolean");
        }
        boolean value = current == JsonParser.Event.VALUE_TRUE;
        advance();
        return value;
    }

    @Override
    public BigInteger readBigInteger() throws IOException {
        if (nextIfNull()) {
            return null;
        }
        BigInteger value = Numbers.toBigInteger(number());
        advance();
        return value;
    }

    @Override
    public BigDecimal readBigDecimal() throws IOException {
        if (nextIfNull()) {
            return null;
        }
        BigDecimal value = number();
        advance();
        return value;
    }

    @Override
    public void skipNode() throws IOException {
        if (current == null) {
            throw expected("value");
        }
        switch (current) {
            case VALUE_STRING:
            case VALUE_NUMBER:
            case VALUE_TRUE:
            case VALUE_FALSE:
            case VALUE_NULL:
                advance();
                return;
            case START_OBJECT:
            case START_ARRAY:
                skipComposite();
                return;
            default:
                throw expected("value");
        }
    }

    @Override
    public Object readRawNode() throws IOException {
        if (current == null) {
            throw new BindingException("unexpected token 'EOF'");
        }
        switch (current) {
            case START_OBJECT:
                return readRawObject();
            case START_ARRAY:
                return readRawArray();
            case VALUE_STRING:
                return readString();
            case VALUE_NUMBER:
                return readNumber();
            case VALUE_TRUE:
            case VALUE_FALSE:
                return readBooleanValue();
            case VALUE_NULL:
                advance();
                return null;
            default:
                throw new BindingException("unexpected token '" + peekToken() + "'");
        }
    }

    @Override
    public void close() {
        parser.close();
    }

    private Map<String, Object> readRawObject() throws IOException {
        Map<String, Object> value = new LinkedHashMap<>();
        startObject();
        String name;
        while ((name = nextName()) != null) {
            value.put(name, readRawNode());
        }
        return value;
    }

    private List<Object> readRawArray() throws IOException {
        List<Object> value = new ArrayList<>();
        startArray();
        while (!nextIfArrayEnd()) {
            value.add(readRawNode());
        }
        return value;
    }

    private BigDecimal number() throws IOException {
        require(JsonParser.Event.VALUE_NUMBER, "number");
        return parser.getBigDecimal();
    }

    private void skipComposite() throws IOException {
        int depth = 0;
        do {
            if (current == null) {
                throw expected("composite end");
            }
            if (current == JsonParser.Event.START_OBJECT || current == JsonParser.Event.START_ARRAY) {
                depth++;
            } else if (current == JsonParser.Event.END_OBJECT || current == JsonParser.Event.END_ARRAY) {
                depth--;
            }
            advance();
        } while (depth != 0);
    }

    private boolean nextIf(JsonParser.Event event) {
        if (current != event) {
            return false;
        }
        advance();
        return true;
    }

    private void require(JsonParser.Event event, String name) throws IOException {
        if (current != event) {
            throw expected(name);
        }
    }

    private IOException expected(String name) {
        return new IOException("expected " + name + ", but was " + peekToken());
    }

    private void advance() {
        current = parser.hasNext() ? parser.next() : null;
    }

    private static Token token(JsonParser.Event event) {
        if (event == null) {
            return Token.EOF;
        }
        switch (event) {
            case START_OBJECT: return Token.OBJECT_START;
            case END_OBJECT: return Token.OBJECT_END;
            case KEY_NAME: return Token.NAME;
            case START_ARRAY: return Token.ARRAY_START;
            case END_ARRAY: return Token.ARRAY_END;
            case VALUE_STRING: return Token.STRING;
            case VALUE_NUMBER: return Token.NUMBER;
            case VALUE_TRUE:
            case VALUE_FALSE: return Token.BOOLEAN;
            case VALUE_NULL: return Token.NULL;
            default: return Token.UNKNOWN;
        }
    }
}
