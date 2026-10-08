package org.sjf4j.backend.snake.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.Numbers;
import org.sjf4j.util.Asserts;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.DocumentEndEvent;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;
import org.yaml.snakeyaml.events.StreamEndEvent;
import org.yaml.snakeyaml.events.StreamStartEvent;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.parser.Parser;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** StreamingReader backed directly by SnakeYAML parser events. */
public final class SnakeReader extends StreamingReader {

    private static final Resolver RESOLVER = new Resolver();
    private static final String TAG_NULL = Tag.NULL.getValue();
    private static final String TAG_BOOLEAN = Tag.BOOL.getValue();
    private static final String TAG_INTEGER = Tag.INT.getValue();
    private static final String TAG_FLOAT = Tag.FLOAT.getValue();
    private static final String TAG_STRING = Tag.STR.getValue();
    private static final String TAG_MAP = Tag.MAP.getValue();
    private static final String TAG_SEQUENCE = Tag.SEQ.getValue();

    private final Parser parser;
    private final Deque<Scope> scopes = new ArrayDeque<>();

    public SnakeReader(Parser parser) {
        super(Backend.SNAKE);
        this.parser = Asserts.notNull(parser, "parser");
    }

    @Override
    public void startDocument() throws IOException {
        require(StreamStartEvent.class);
        require(DocumentStartEvent.class);
    }

    @Override
    public void endDocument() throws IOException {
        if (!scopes.isEmpty()) {
            throw new IOException("expected end of document, but a structure is still open");
        }
        require(DocumentEndEvent.class);
        require(StreamEndEvent.class);
    }

    @Override
    public Token peekToken() throws IOException {
        Event event = peek();
        if (event == null) {
            return Token.EOF;
        }
        if (event instanceof DocumentEndEvent || event instanceof StreamEndEvent) {
            return Token.EOF;
        }
        validateNode(event);

        Scope scope = scopes.peek();
        if (scope != null && scope.object && scope.expectingName) {
            if (event instanceof MappingEndEvent) {
                return Token.OBJECT_END;
            }
            if (!(event instanceof ScalarEvent)) {
                throw new IOException("YAML mapping keys must be string scalars");
            }
            if (scalarToken((ScalarEvent) event) != Token.STRING) {
                throw new IOException("YAML mapping keys must be strings");
            }
            return Token.NAME;
        }

        if (event instanceof MappingStartEvent) return Token.OBJECT_START;
        if (event instanceof MappingEndEvent) return Token.OBJECT_END;
        if (event instanceof SequenceStartEvent) return Token.ARRAY_START;
        if (event instanceof SequenceEndEvent) return Token.ARRAY_END;
        if (event instanceof ScalarEvent) return scalarToken((ScalarEvent) event);
        return Token.UNKNOWN;
    }

    @Override
    public boolean nextIfNull() throws IOException {
        Event event = peek();
        validateNode(event);
        if (!(event instanceof ScalarEvent)) {
            return false;
        }
        ScalarEvent scalar = (ScalarEvent) event;
        if (scalarToken(scalar) != Token.NULL) {
            return false;
        }
        takeScalarValue();
        validateNull(scalar.getValue());
        return true;
    }

    @Override
    public boolean nextIfObjectStart() throws IOException {
        if (peekToken() != Token.OBJECT_START) {
            return false;
        }
        startObject();
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() throws IOException {
        Scope scope = scopes.peek();
        if (scope == null || !scope.object || !scope.expectingName
                || !(peek() instanceof MappingEndEvent)) {
            return false;
        }
        take();
        scopes.pop();
        return true;
    }

    @Override
    public boolean nextIfArrayStart() throws IOException {
        if (peekToken() != Token.ARRAY_START) {
            return false;
        }
        startArray();
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() throws IOException {
        Scope scope = scopes.peek();
        if (scope == null || scope.object || !(peek() instanceof SequenceEndEvent)) {
            return false;
        }
        take();
        scopes.pop();
        return true;
    }

    @Override
    public void startObject() throws IOException {
        if (peekToken() != Token.OBJECT_START) {
            throw expected("object start");
        }
        completeParentValue();
        take();
        scopes.push(new Scope(true));
    }

    @Override
    public void endObject() throws IOException {
        if (!nextIfObjectEnd()) {
            throw expected("object end");
        }
    }

    @Override
    public void startArray() throws IOException {
        if (peekToken() != Token.ARRAY_START) {
            throw expected("array start");
        }
        completeParentValue();
        take();
        scopes.push(new Scope(false));
    }

    @Override
    public void endArray() throws IOException {
        if (!nextIfArrayEnd()) {
            throw expected("array end");
        }
    }

    @Override
    public String nextName() throws IOException {
        if (nextIfObjectEnd()) {
            return null;
        }
        Scope scope = scopes.peek();
        if (scope == null || !scope.object || !scope.expectingName || peekToken() != Token.NAME) {
            throw expected("member name");
        }
        ScalarEvent scalar = (ScalarEvent) take();
        scope.expectingName = false;
        return scalar.getValue();
    }

    @Override
    public String readString() throws IOException {
        ScalarEvent scalar = peekScalarValue();
        Token token = scalarToken(scalar);
        if (token == Token.NULL) {
            takeScalarValue();
            validateNull(scalar.getValue());
            return null;
        }
        if (token != Token.STRING) {
            throw expected("string or null", token);
        }
        takeScalarValue();
        return scalar.getValue();
    }

    @Override
    public Number readNumber() throws IOException {
        ScalarEvent scalar = peekScalarValue();
        Token token = scalarToken(scalar);
        if (token == Token.NULL) {
            takeScalarValue();
            validateNull(scalar.getValue());
            return null;
        }
        if (token != Token.NUMBER) {
            throw expected("number or null", token);
        }
        takeScalarValue();
        return parseNumber(scalar.getValue());
    }

    @Override
    public long readLongValue() throws IOException {
        return Numbers.toLong(requireNumber());
    }

    @Override
    public int readIntValue() throws IOException {
        return Numbers.toInt(requireNumber());
    }

    @Override
    public short readShortValue() throws IOException {
        return Numbers.toShort(requireNumber());
    }

    @Override
    public byte readByteValue() throws IOException {
        return Numbers.toByte(requireNumber());
    }

    @Override
    public double readDoubleValue() throws IOException {
        return Numbers.toDouble(requireNumber());
    }

    @Override
    public float readFloatValue() throws IOException {
        return Numbers.toFloat(requireNumber());
    }

    @Override
    public boolean readBooleanValue() throws IOException {
        ScalarEvent scalar = peekScalarValue();
        Token token = scalarToken(scalar);
        if (token != Token.BOOLEAN) {
            throw expected("boolean", token);
        }
        takeScalarValue();
        return parseBoolean(scalar.getValue());
    }

    @Override
    public Long readLong() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toLong(value);
    }

    @Override
    public Integer readInt() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toInt(value);
    }

    @Override
    public Short readShort() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toShort(value);
    }

    @Override
    public Byte readByte() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toByte(value);
    }

    @Override
    public Double readDouble() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toDouble(value);
    }

    @Override
    public Float readFloat() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toFloat(value);
    }

    @Override
    public Boolean readBoolean() throws IOException {
        ScalarEvent scalar = peekScalarValue();
        Token token = scalarToken(scalar);
        if (token == Token.NULL) {
            takeScalarValue();
            validateNull(scalar.getValue());
            return null;
        }
        if (token != Token.BOOLEAN) {
            throw expected("boolean or null", token);
        }
        takeScalarValue();
        return parseBoolean(scalar.getValue());
    }

    @Override
    public char readCharValue() throws IOException {
        return toChar(readString());
    }

    @Override
    public Character readChar() throws IOException {
        String value = readString();
        return value == null ? null : toChar(value);
    }

    private static boolean parseBoolean(String text) throws IOException {
        String value = text.toLowerCase(Locale.ROOT);
        if ("true".equals(value) || "yes".equals(value) || "on".equals(value)) {
            return true;
        }
        if ("false".equals(value) || "no".equals(value) || "off".equals(value)) {
            return false;
        }
        throw new IOException("invalid YAML boolean: " + value);
    }

    @Override
    public BigInteger readBigInteger() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toBigInteger(value);
    }

    @Override
    public BigDecimal readBigDecimal() throws IOException {
        Number value = readNumber();
        return value == null ? null : Numbers.toBigDecimal(value);
    }

    @Override
    public void skipNode() throws IOException {
        Event event = peek();
        validateNode(event);
        if (event instanceof ScalarEvent) {
            completeParentValue();
            skipScalar((ScalarEvent) event, null);
            take();
            return;
        }
        if (!(event instanceof MappingStartEvent) && !(event instanceof SequenceStartEvent)) {
            throw expected("value");
        }

        completeParentValue();
        Deque<SkipFrame> frames = new ArrayDeque<>();
        while (true) {
            event = peek();
            validateNode(event);
            SkipFrame frame = frames.peek();
            if (event instanceof MappingEndEvent || event instanceof SequenceEndEvent) {
                if (frame == null || (frame.object != (event instanceof MappingEndEvent))) {
                    throw new IOException("unexpected YAML collection end");
                }
                if (frame.object && !frame.expectingName) {
                    throw new IOException("expected YAML mapping value");
                }
                take();
                frames.pop();
                if (frames.isEmpty()) {
                    return;
                }
                continue;
            }
            if (event instanceof ScalarEvent) {
                skipScalar((ScalarEvent) event, frame);
                take();
                continue;
            }
            if (event instanceof MappingStartEvent || event instanceof SequenceStartEvent) {
                if (frame != null && frame.object) {
                    if (frame.expectingName) {
                        throw new IOException("YAML mapping keys must be string scalars");
                    }
                    frame.expectingName = true;
                }
                take();
                frames.push(new SkipFrame(event instanceof MappingStartEvent));
                continue;
            }
            throw expected("value");
        }
    }

    @Override
    public Object readRawNode() throws IOException {
        Token token = peekToken();
        if (token != Token.OBJECT_START && token != Token.ARRAY_START) {
            return readRawScalar(token);
        }

        RawFrame root = startRawContainer(token);
        Deque<RawFrame> frames = new ArrayDeque<>();
        frames.push(root);
        while (!frames.isEmpty()) {
            RawFrame frame = frames.peek();
            if (frame.object != null) {
                if (nextIfObjectEnd()) {
                    frames.pop();
                    continue;
                }
                String name = nextName();
                token = peekToken();
                if (token == Token.OBJECT_START || token == Token.ARRAY_START) {
                    RawFrame child = startRawContainer(token);
                    frame.object.put(name, child.value());
                    frames.push(child);
                } else {
                    frame.object.put(name, readRawScalar(token));
                }
            } else {
                if (nextIfArrayEnd()) {
                    frames.pop();
                    continue;
                }
                token = peekToken();
                if (token == Token.OBJECT_START || token == Token.ARRAY_START) {
                    RawFrame child = startRawContainer(token);
                    frame.array.add(child.value());
                    frames.push(child);
                } else {
                    frame.array.add(readRawScalar(token));
                }
            }
        }
        return root.value();
    }

    @Override
    public void close() {
        // The caller owns the Reader supplied to SnakeYAML.
    }

    private Number requireNumber() throws IOException {
        Number value = readNumber();
        if (value == null) {
            throw new IOException("expected number, but was null");
        }
        return value;
    }

    private ScalarEvent takeScalarValue() throws IOException {
        if (!(peek() instanceof ScalarEvent)) {
            throw expected("scalar value");
        }
        completeParentValue();
        return (ScalarEvent) take();
    }

    private ScalarEvent peekScalarValue() throws IOException {
        Event event = peek();
        validateNode(event);
        if (!(event instanceof ScalarEvent)) {
            throw expected("scalar value");
        }
        return (ScalarEvent) event;
    }

    private void completeParentValue() throws IOException {
        Scope parent = scopes.peek();
        if (parent != null && parent.object) {
            if (parent.expectingName) {
                throw new IOException("expected YAML mapping key");
            }
            parent.expectingName = true;
        }
    }

    private <E extends Event> E require(Class<E> type) throws IOException {
        Event event = take();
        if (!type.isInstance(event)) {
            throw new IOException("expected " + type.getName() + ", but was " + event);
        }
        return type.cast(event);
    }

    private Event peek() throws IOException {
        try {
            return parser.peekEvent();
        } catch (RuntimeException | StackOverflowError e) {
            throw new IOException("invalid YAML", e);
        }
    }

    private Event take() throws IOException {
        try {
            Event event = parser.getEvent();
            if (event == null) {
                throw new IOException("unexpected end of YAML stream");
            }
            return event;
        } catch (RuntimeException | StackOverflowError e) {
            throw new IOException("invalid YAML", e);
        }
    }

    private IOException expected(String expected) throws IOException {
        return new IOException("expected " + expected + ", but was " + peekToken());
    }

    private static IOException expected(String expected, Token actual) {
        return new IOException("expected " + expected + ", but was " + actual);
    }

    private static Token scalarToken(ScalarEvent scalar) throws IOException {
        rejectAnchor(scalar.getAnchor());
        String tag = resolveTag(scalar);
        if (TAG_STRING.equals(tag)) return Token.STRING;
        if (TAG_NULL.equals(tag)) return Token.NULL;
        if (TAG_BOOLEAN.equals(tag)) return Token.BOOLEAN;
        if (TAG_INTEGER.equals(tag) || TAG_FLOAT.equals(tag)) return Token.NUMBER;
        throw new IOException("YAML tag is not JSON-compatible: " + tag);
    }

    private static String resolveTag(ScalarEvent scalar) {
        String tag = scalar.getTag();
        if (tag != null && !"!".equals(tag)) {
            return tag;
        }
        return RESOLVER.resolve(
                NodeId.scalar,
                scalar.getValue(),
                scalar.getImplicit().canOmitTagInPlainScalar()
        ).getValue();
    }

    private static void validateNode(Event event) throws IOException {
        if (event instanceof AliasEvent) {
            throw new IOException("YAML aliases are not supported");
        }
        if (event instanceof ScalarEvent) {
            rejectAnchor(((ScalarEvent) event).getAnchor());
        } else if (event instanceof MappingStartEvent) {
            MappingStartEvent mapping = (MappingStartEvent) event;
            rejectAnchor(mapping.getAnchor());
            validateCollectionTag(mapping.getTag(), TAG_MAP);
        } else if (event instanceof SequenceStartEvent) {
            SequenceStartEvent sequence = (SequenceStartEvent) event;
            rejectAnchor(sequence.getAnchor());
            validateCollectionTag(sequence.getTag(), TAG_SEQUENCE);
        }
    }

    private static void validateCollectionTag(String tag, String expected) throws IOException {
        if (tag != null && !"!".equals(tag) && !expected.equals(tag)) {
            throw new IOException("YAML tag is not JSON-compatible: " + tag);
        }
    }

    private static void rejectAnchor(String anchor) throws IOException {
        if (anchor != null) {
            throw new IOException("YAML anchors are not supported");
        }
    }

    private static void skipScalar(ScalarEvent scalar, SkipFrame frame) throws IOException {
        Token token = scalarToken(scalar);
        if (frame != null && frame.object && frame.expectingName) {
            if (token != Token.STRING) {
                throw new IOException("YAML mapping keys must be strings");
            }
            frame.expectingName = false;
            return;
        }
        if (frame != null && frame.object) {
            frame.expectingName = true;
        }
        switch (token) {
            case NUMBER:
                parseNumber(scalar.getValue());
                return;
            case BOOLEAN:
                parseBoolean(scalar.getValue());
                return;
            case NULL:
                validateNull(scalar.getValue());
                return;
            case STRING:
                return;
            default:
                throw new IOException("expected value, but was " + token);
        }
    }

    private RawFrame startRawContainer(Token token) throws IOException {
        if (token == Token.OBJECT_START) {
            startObject();
            return new RawFrame(new LinkedHashMap<String, Object>());
        }
        if (token == Token.ARRAY_START) {
            startArray();
            return new RawFrame(new ArrayList<Object>());
        }
        throw expected("container", token);
    }

    private Object readRawScalar(Token token) throws IOException {
        if (token != Token.STRING && token != Token.NUMBER
                && token != Token.BOOLEAN && token != Token.NULL) {
            throw expected("value", token);
        }
        ScalarEvent scalar = takeScalarValue();
        switch (token) {
            case STRING:
                return scalar.getValue();
            case NUMBER:
                return parseNumber(scalar.getValue());
            case BOOLEAN:
                return parseBoolean(scalar.getValue());
            case NULL:
                validateNull(scalar.getValue());
                return null;
            default:
                throw expected("value", token);
        }
    }

    private static char toChar(String value) {
        if (value == null) {
            throw new BindingException("cannot read null as char");
        }
        if (value.length() != 1) {
            throw new BindingException("cannot read char: expected single-character string, but length was " + value.length());
        }
        return value.charAt(0);
    }

    private static Number parseNumber(String value) throws IOException {
        String normalized = normalizeDecimal(value);
        if (normalized == null) {
            throw new IOException("unsupported YAML number: " + value);
        }
        try {
            return Numbers.parseNumber(normalized);
        } catch (NumberFormatException e) {
            throw new IOException("invalid YAML number: " + value, e);
        }
    }

    private static void validateNull(String value) throws IOException {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!"".equals(value) && !"~".equals(value) && !"null".equals(normalized)) {
            throw new IOException("invalid YAML null: " + value);
        }
    }

    private static String normalizeDecimal(String value) {
        if (value == null || value.isEmpty()) return null;
        StringBuilder normalized = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '_') {
                if (i == 0 || i + 1 == value.length()
                        || !isDigit(value.charAt(i - 1)) || !isDigit(value.charAt(i + 1))) {
                    return null;
                }
                if (normalized == null) {
                    normalized = new StringBuilder(value.length() - 1).append(value, 0, i);
                }
            } else if (normalized != null) {
                normalized.append(c);
            }
        }
        String text = normalized == null ? value : normalized.toString();
        if (text.charAt(0) == '+') {
            text = text.substring(1);
        }
        return Numbers.isNumeric(text) ? text : null;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static final class Scope {
        final boolean object;
        boolean expectingName;

        Scope(boolean object) {
            this.object = object;
            this.expectingName = object;
        }
    }

    private static final class SkipFrame {
        final boolean object;
        boolean expectingName;

        SkipFrame(boolean object) {
            this.object = object;
            this.expectingName = object;
        }
    }

    private static final class RawFrame {
        final Map<String, Object> object;
        final List<Object> array;

        RawFrame(Map<String, Object> object) {
            this.object = object;
            this.array = null;
        }

        RawFrame(List<Object> array) {
            this.object = null;
            this.array = array;
        }

        Object value() {
            return object != null ? object : array;
        }
    }
}
