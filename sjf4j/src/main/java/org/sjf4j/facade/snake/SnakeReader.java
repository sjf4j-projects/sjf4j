package org.sjf4j.facade.snake;

import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.StreamingReader;
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
import org.yaml.snakeyaml.parser.Parser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

public class SnakeReader implements StreamingReader {

    private final Parser parser;
//    private Object cachedValue;

    public SnakeReader(Parser parser) {
        this.parser = parser;
    }

    @Override
    public void startDocument() {
        if (!(parser.getEvent() instanceof StreamStartEvent)) throw new IllegalStateException("Malformed YAML");
        if (!(parser.getEvent() instanceof DocumentStartEvent)) throw new IllegalStateException("Malformed YAML");
    }

    @Override
    public void endDocument() {
        if (!(parser.getEvent() instanceof DocumentEndEvent)) throw new IllegalStateException("Malformed YAML");
        if (!(parser.getEvent() instanceof StreamEndEvent)) throw new IllegalStateException("Malformed YAML");
    }


    @Override
    public Token peekToken() {
        Event event = parser.peekEvent();
        if (event instanceof MappingStartEvent) {
            return Token.START_OBJECT;
        } else if (event instanceof MappingEndEvent) {
            return Token.END_OBJECT;
        } else if (event instanceof SequenceStartEvent) {
            return Token.START_ARRAY;
        } else if (event instanceof SequenceEndEvent) {
            return Token.END_ARRAY;
        } else if (event instanceof ScalarEvent) {
            ScalarEvent se = (ScalarEvent) event;
            String value = se.getValue();
            String tag = se.getTag();
            if (value == null || value.isEmpty() || "tag:yaml.org,2002:null".equals(tag) ||
                    value.equalsIgnoreCase("null") || value.equals("~")) {
                return Token.NULL;
            }
            if ("tag:yaml.org,2002:str".equals(tag) || "!".equals(tag)) {
                return Token.STRING;
            }
            String low = value.toLowerCase();
            if (low.equals("true") || low.equals("yes") || low.equals("on")) {
                return Token.BOOLEAN;
            }
            if (low.equals("false") || low.equals("no") || low.equals("off")) {
                return Token.BOOLEAN;
            }
            if (Numbers.isNumeric(value)) {
                return Token.NUMBER;
            } else {
                return Token.STRING;
            }
        } else if (event instanceof AliasEvent) {
            throw new JsonException("YAML anchors/aliases not supported.");
        } else {
            return Token.UNKNOWN;
        }
    }

    @Override
    public void startObject() {
        parser.getEvent(); // consume start
    }

    @Override
    public void endObject() {
        parser.getEvent(); // consume end
    }

    @Override
    public void startArray() {
        parser.getEvent(); // consume start
    }

    @Override
    public void endArray() {
        parser.getEvent(); // consume end
    }

    @Override
    public String nextName() {
        ScalarEvent se = (ScalarEvent) parser.getEvent();
        return se.getValue();
    }

    @Override
    public String nextString() {
        ScalarEvent se = (ScalarEvent) parser.getEvent();
        return se.getValue();
    }

    @Override
    public Number nextNumber() {
        ScalarEvent se = (ScalarEvent) parser.getEvent();
        return Numbers.asNumber(se.getValue());
    }
    @Override
    public long nextLong() {
        return Numbers.toLong(nextNumber());
    }
    @Override
    public int nextInt() {
        return Numbers.toInt(nextNumber());
    }
    @Override
    public short nextShort() {
        return Numbers.toShort(nextNumber());
    }
    @Override
    public byte nextByte() {
        return Numbers.toByte(nextNumber());
    }
    @Override
    public double nextDouble() {
        return Numbers.toDouble(nextNumber());
    }
    @Override
    public float nextFloat() {
        return Numbers.toFloat(nextNumber());
    }
    @Override
    public BigInteger nextBigInteger() {
        return Numbers.toBigInteger(nextNumber());
    }
    @Override
    public BigDecimal nextBigDecimal() {
        return Numbers.toBigDecimal(nextNumber());
    }

    @Override
    public boolean nextBoolean() {
        ScalarEvent se = (ScalarEvent) parser.getEvent();
        String value = se.getValue();
        String low = value.toLowerCase();
        if (low.equals("true") || low.equals("yes") || low.equals("on")) {
            return true;
        }
        if (low.equals("false") || low.equals("no") || low.equals("off")) {
            return false;
        }
        throw new JsonException("Expect a Boolean but got '" + value + "'");
    }

    @Override
    public void nextNull() {
        ScalarEvent se = (ScalarEvent) parser.getEvent();
        String value = se.getValue();
        String tag = se.getTag();
        if (value == null || value.isEmpty() || "tag:yaml.org,2002:null".equals(tag) ||
                value.equalsIgnoreCase("null") || value.equals("~")) {
            return;
        }
        throw new JsonException("Expect a Null but got '" + value + "'");
    }

    @Override
    public void close() throws IOException {
        // Nothing
    }

    @Override
    public void nextSkip() throws IOException {
        Event event = parser.peekEvent();
        if (event instanceof ScalarEvent) {
            parser.getEvent();
        } else if (event instanceof MappingStartEvent || event instanceof SequenceStartEvent) {
            int depth = 0;
            do {
                event = parser.getEvent();
                if (event instanceof MappingStartEvent || event instanceof SequenceStartEvent) {
                    depth++;
                } else if (event instanceof MappingEndEvent || event instanceof SequenceEndEvent) {
                    depth--;
                }
            } while (depth > 0);
        }
    }


//    @Override
//    public Token nextToken() throws IOException {
//        Token tk = peekToken();
//        parser.getEvent();
//        return tk;
//    }
//
//    @Override
//    public String peekName() {
//        ScalarEvent se = (ScalarEvent) parser.peekEvent();
//        return se.getValue();
//    }
//
//    @Override
//    public String nextString() {
//        ScalarEvent se = (ScalarEvent) parser.getEvent();
//        return se.getValue();
//    }
//
//    @Override
//    public Number nextNumber() {
//        ScalarEvent se = (ScalarEvent) parser.getEvent();
//        return Numbers.asNumber(se.getValue());
//    }
//    @Override
//    public long nextLong() {
//        ScalarEvent se = (ScalarEvent) parser.getEvent();
//        return Long.parseLong(se.getValue());
//    }
//    @Override
//    public int nextInt() {
//        ScalarEvent se = (ScalarEvent) parser.getEvent();
//        return Integer.parseInt(se.getValue());
//    }
//    @Override
//    public short nextShort() {
//        ScalarEvent se = (ScalarEvent) parser.getEvent();
//        return Short.parseShort(se.getValue());
//    }
//    @Override
//    public byte nextByte() {
//        ScalarEvent se = (ScalarEvent) parser.getEvent();
//        return Byte.parseByte(se.getValue());
//    }
//    @Override
//    public double peekDouble() {
//        ScalarEvent se = (ScalarEvent) parser.peekEvent();
//        return Double.parseDouble(se.getValue());
//    }
//    @Override
//    public float peekFloat() {
//        ScalarEvent se = (ScalarEvent) parser.peekEvent();
//        return Float.parseFloat(se.getValue());
//    }
//    @Override
//    public BigInteger peekBigInteger() {
//        ScalarEvent se = (ScalarEvent) parser.peekEvent();
//        return new BigInteger(se.getValue());
//    }
//    @Override
//    public BigDecimal peekBigDecimal() {
//        ScalarEvent se = (ScalarEvent) parser.peekEvent();
//        return new BigDecimal(se.getValue());
//    }
//
//    @Override
//    public boolean peekBoolean() {
//        ScalarEvent se = (ScalarEvent) parser.peekEvent();
//        String value = se.getValue();
//        String low = value.toLowerCase();
//        if (low.equals("true") || low.equals("yes") || low.equals("on")) {
//            return true;
//        }
//        if (low.equals("false") || low.equals("no") || low.equals("off")) {
//            return false;
//        }
//        throw new JsonException("Expect a Boolean but got '" + value + "'");
//    }

}
