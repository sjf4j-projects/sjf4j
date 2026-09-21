package org.sjf4j.backend.snake.binding;

import org.sjf4j.binding.StreamingReader;
import org.sjf4j.node.Numbers;
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
import org.yaml.snakeyaml.parser.Parser;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.BindException;
import java.util.Locale;
import java.util.Objects;

/** Streaming reader backed directly by SnakeYAML parser events. */
public final class SnakeReader implements StreamingReader {

    private static final Resolver RESOLVER = new Resolver();

    private static final String TAG_NULL = "tag:yaml.org,2002:null";
    private static final String TAG_BOOLEAN = "tag:yaml.org,2002:bool";
    private static final String TAG_INTEGER = "tag:yaml.org,2002:int";
    private static final String TAG_FLOAT = "tag:yaml.org,2002:float";

    private final Parser parser;

    public SnakeReader(Parser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    @Override
    public void startDocument() throws IOException {
        requireEvent(StreamStartEvent.class);
        requireEvent(DocumentStartEvent.class);
    }

    @Override
    public void endDocument() throws IOException {
        requireEvent(DocumentEndEvent.class);
        requireEvent(StreamEndEvent.class);
    }

    @Override
    public Token peekToken() throws IOException {
        Event event = peekEvent();
        if (event == null) return Token.EOF;
        if (event instanceof MappingStartEvent) return Token.START_OBJECT;
        if (event instanceof MappingEndEvent) return Token.END_OBJECT;
        if (event instanceof SequenceStartEvent) return Token.START_ARRAY;
        if (event instanceof SequenceEndEvent) return Token.END_ARRAY;
        if (event instanceof AliasEvent) {
            throw new IOException("YAML aliases are not supported");
        }
        if (!(event instanceof ScalarEvent)) return Token.UNKNOWN;

        String tag = resolveTag((ScalarEvent) event);
        if (TAG_NULL.equals(tag)) return Token.NULL;
        if (TAG_BOOLEAN.equals(tag)) return Token.BOOLEAN;
        if (TAG_INTEGER.equals(tag) || TAG_FLOAT.equals(tag)) return Token.NUMBER;
        return Token.STRING;
    }

    @Override
    public void startObject() throws IOException {
        requireEvent(MappingStartEvent.class);
    }

    @Override
    public void endObject() throws IOException {
        requireEvent(MappingEndEvent.class);
    }

    @Override
    public void startArray() throws IOException {
        requireEvent(SequenceStartEvent.class);
    }

    @Override
    public void endArray() throws IOException {
        requireEvent(SequenceEndEvent.class);
    }

    @Override
    public String nextName() throws IOException {
        return requireEvent(ScalarEvent.class).getValue();
    }

    @Override
    public String nextString() throws IOException {
        return nextScalar(Token.STRING).getValue();
    }

    @Override
    public Number nextNumber() throws IOException {
        String value = nextScalar(Token.NUMBER).getValue();
        String normalized = normalizeDecimal(value);
        if (normalized == null) {
            throw new IOException("Unsupported YAML number: " + value);
        }
        return Numbers.parseNumber(normalized);
    }

    @Override
    public long nextLongValue() throws IOException {
        return Numbers.toLong(nextNumber());
    }

    @Override
    public int nextIntValue() throws IOException {
        return Numbers.toInt(nextNumber());
    }

    @Override
    public short nextShortValue() throws IOException {
        return Numbers.toShort(nextNumber());
    }

    @Override
    public byte nextByteValue() throws IOException {
        return Numbers.toByte(nextNumber());
    }

    @Override
    public double nextDoubleValue() throws IOException {
        return Numbers.toDouble(nextNumber());
    }

    @Override
    public float nextFloatValue() throws IOException {
        return Numbers.toFloat(nextNumber());
    }

    @Override
    public boolean nextBooleanValue() throws IOException {
        String value = nextScalar(Token.BOOLEAN).getValue();
        return "true".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value)
                || "on".equalsIgnoreCase(value);
    }

    @Override
    public char nextCharValue() throws IOException {
        String value = nextString();
        if (value.isEmpty()) {
            throw new BindException("cannot read empty string as char");
        }
        return value.charAt(0);
    }

    @Override
    public BigInteger nextBigInteger() throws IOException {
        return Numbers.toBigInteger(nextNumber());
    }

    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        return Numbers.toBigDecimal(nextNumber());
    }

    @Override
    public void nextNull() throws IOException {
        nextScalar(Token.NULL);
    }

    @Override
    public boolean nextIfNull() throws IOException {
        if (peekToken() != Token.NULL) return false;
        nextEvent();
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() throws IOException {
        if (!(peekEvent() instanceof MappingEndEvent)) return false;
        nextEvent();
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() throws IOException {
        if (!(peekEvent() instanceof SequenceEndEvent)) return false;
        nextEvent();
        return true;
    }

    @Override
    public void skipNext() throws IOException {
        Token token = peekToken();

        switch (token) {
            case STRING:
            case NUMBER:
            case BOOLEAN:
            case NULL:
                nextEvent();
                return;
            case START_OBJECT:
            case START_ARRAY:
                break;
            default:
                throw new IOException("Expected value, but was " + token);
        }

        int depth = 0;
        do {
            Event event = nextEvent();

            if (event instanceof AliasEvent) {
                throw new IOException("YAML aliases are not supported");
            }

            if (event instanceof MappingStartEvent || event instanceof SequenceStartEvent) {
                depth++;
            } else if (event instanceof MappingEndEvent || event instanceof SequenceEndEvent) {
                depth--;
            }
        } while (depth != 0);
    }

    @Override
    public void close() {
    }

    private ScalarEvent nextScalar(Token expected) throws IOException {
        Token actual = peekToken();
        if (actual != expected) {
            throw new IOException(
                    "Expected " + expected.name().toLowerCase(Locale.ROOT) + ", but was " + actual);
        }
        return (ScalarEvent) nextEvent();
    }

    private <E extends Event> E requireEvent(Class<E> type) throws IOException {
        Event event = nextEvent();
        if (!type.isInstance(event)) {
            throw new IOException(
                    "Expected " + type.getSimpleName() + ", but was " + event);
        }
        return type.cast(event);
    }

    private Event peekEvent() throws IOException {
        try {
            return parser.peekEvent();
        } catch (RuntimeException e) {
            throw new IOException("Invalid YAML", e);
        }
    }

    private Event nextEvent() throws IOException {
        try {
            Event event = parser.getEvent();
            if (event == null) {
                throw new IOException("Unexpected end of YAML stream");
            }
            return event;
        } catch (RuntimeException e) {
            throw new IOException("Invalid YAML", e);
        }
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

    private static String normalizeDecimal(String value) {
        if (value == null || value.isEmpty()) return null;

        StringBuilder normalized = null;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (c == '_') {
                if (i == 0
                        || i + 1 == value.length()
                        || !isDigit(value.charAt(i - 1))
                        || !isDigit(value.charAt(i + 1))) {
                    return null;
                }

                if (normalized == null) {
                    normalized = new StringBuilder(value.length() - 1)
                            .append(value, 0, i);
                }
                continue;
            }

            if (normalized != null) {
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
}
