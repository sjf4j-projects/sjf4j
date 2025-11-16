package org.sjf4j.facades.snake;

import org.sjf4j.JsonException;
import org.sjf4j.facades.FacadeReader;
import org.sjf4j.util.NumberUtil;
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

public class SnakeReader implements FacadeReader {

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
    public int peekTokenId() {
        Event event = parser.peekEvent();
        if (event instanceof MappingStartEvent) {
            return ID_START_OBJECT;
        } else if (event instanceof MappingEndEvent) {
            return ID_END_OBJECT;
        } else if (event instanceof SequenceStartEvent) {
            return ID_START_ARRAY;
        } else if (event instanceof SequenceEndEvent) {
            return ID_END_ARRAY;
        } else if (event instanceof ScalarEvent) {
            ScalarEvent se = (ScalarEvent) event;
            String value = se.getValue();
            String tag = se.getTag();
            if (value == null || value.isEmpty() || "tag:yaml.org,2002:null".equals(tag) ||
                    value.equalsIgnoreCase("null") || value.equals("~")) {
                return ID_NULL;
            }
            if ("tag:yaml.org,2002:str".equals(tag) || "!".equals(tag)) {
                return ID_STRING;
            }
            String low = value.toLowerCase();
            if (low.equals("true") || low.equals("yes") || low.equals("on")) {
                return ID_BOOLEAN;
            }
            if (low.equals("false") || low.equals("no") || low.equals("off")) {
                return ID_BOOLEAN;
            }
            if (NumberUtil.isNumeric(value)) {
                return ID_NUMBER;
            } else {
                return ID_STRING;
            }
        } else if (event instanceof AliasEvent) {
            throw new JsonException("YAML anchors/aliases not supported.");
        } else {
            return ID_UNKNOWN;
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
    public String nextString() {
        ScalarEvent se = (ScalarEvent) parser.getEvent();
        return se.getValue();
    }

    @Override
    public Number nextNumber() {
        ScalarEvent se = (ScalarEvent) parser.getEvent();
        return NumberUtil.stringToNumber(se.getValue());
    }

    @Override
    public Boolean nextBoolean() {
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
    public String nextName() {
        ScalarEvent se = (ScalarEvent) parser.getEvent();
        return se.getValue();
    }

    @Override
    public boolean hasNext() throws IOException {
        int tid = peekTokenId();
        return tid != ID_END_OBJECT && tid != ID_END_ARRAY;
    }

    @Override
    public void close() throws IOException {
        // Nothing
    }

}
