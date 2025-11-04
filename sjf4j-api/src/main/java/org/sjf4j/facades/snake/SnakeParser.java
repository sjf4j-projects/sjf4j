package org.sjf4j.facades.snake;

import lombok.extern.slf4j.Slf4j;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.util.NumberUtil;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;
import org.yaml.snakeyaml.parser.Parser;


@Slf4j
public class SnakeParser {

    public static Object readAny(Parser parser) {
        Event event = parser.peekEvent();
        if (event instanceof MappingStartEvent) {
            return readObject(parser);
        } else if (event instanceof SequenceStartEvent) {
            return readArray(parser);
        } else if (event instanceof ScalarEvent) {
            return readValue(parser);
        } else if (event instanceof AliasEvent) {
            throw new JsonException("YAML anchors/aliases not supported.");
        } else {
            parser.getEvent(); // consume unknown
            return null;
        }
    }

    public static JsonObject readObject(Parser parser) {
        parser.getEvent(); // consume start
        JsonObject jo = new JsonObject();
        while (!(parser.peekEvent() instanceof MappingEndEvent)) {
            String key = ((ScalarEvent) parser.getEvent()).getValue();
            Object value = readAny(parser);
            jo.put(key, value);
        }
        parser.getEvent(); // consume end
        return jo;
    }

    public static JsonArray readArray(Parser parser) {
        parser.getEvent(); // consume start
        JsonArray ja = new JsonArray();
        while (!(parser.peekEvent() instanceof SequenceEndEvent)) {
            ja.add(readAny(parser));
        }
        parser.getEvent(); // consume end
        return ja;
    }

    public static Object readValue(Parser parser) {
        ScalarEvent event = (ScalarEvent) parser.getEvent();
        String v = event.getValue();
        String tag = event.getTag();
        if (v == null) return null;
        if ("tag:yaml.org,2002:null".equals(tag) ||
                v.isEmpty() || v.equalsIgnoreCase("null") || v.equals("~")) {
            return null;
        }

        if ("tag:yaml.org,2002:str".equals(tag) || "!".equals(tag)) {
            return v;
        }

        String low = v.toLowerCase();
        if (low.equals("true") || low.equals("yes") || low.equals("on")) {
            return true;
        }
        if (low.equals("false") || low.equals("no") || low.equals("off")) {
            return false;
        }

        if (isNumeric(v)) {
            return NumberUtil.stringToNumber(v);
        } else {
            return v;
        }
    }


    /// private

    /**
     * Checks if a given string represents a numeric value.
     * <p>
     * This method supports:
     * - Integers (e.g., "42", "-7", "+8")
     * - Floating-point numbers (e.g., "3.14", "-0.5", ".5")
     * - Scientific notation (e.g., "1e3", "-2.5E-4")
     * - Special floating-point values: ".nan", ".inf", "-.inf"
     * - Underscore separators in numbers (YAML style, e.g., "1_000_000")
     * <p>
     * Rules:
     * - Leading '+' or '-' is allowed at the start or immediately after 'e'/'E'
     * - Only one decimal point is allowed, and it must appear before any 'e'/'E'
     * - Only one 'e'/'E' is allowed for scientific notation
     *
     */
    private static boolean isNumeric(String text) {
        if (text == null || text.isEmpty()) return false;
        text = text.replace("_", "").trim();

        boolean dotSeen = false, eSeen = false, digitSeen = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '+' || c == '-') {
                if (i > 0 && text.charAt(i - 1) != 'e' && text.charAt(i - 1) != 'E') return false;
            } else if (c == '.') {
                if (dotSeen || eSeen) return false;
                dotSeen = true;
            } else if (c == 'e' || c == 'E') {
                if (eSeen || !digitSeen || i == text.length() - 1) return false;
                eSeen = true;
            } else if (Character.isDigit(c)) {
                digitSeen = true;
            } else {
                return false;
            }
        }
        return digitSeen;
    }


}
