package org.sjf4j.facade.simple;

import org.sjf4j.exception.BindingException;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.JsonType;
import org.sjf4j.node.Numbers;
import org.sjf4j.path.PathSegment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;


/**
 * Minimal JSON reader for the built-in facade.
 */
public final class SimpleJsonReader implements StreamingReader {

    @FunctionalInterface
    private interface NumberParser<T> {
        T parse(String text) throws Exception;
    }

    private final Reader reader;

    /**
     * Creates reader over input characters.
     */
    public SimpleJsonReader(Reader input) {
        if (!(input instanceof BufferedReader)) {
            input = new BufferedReader(input);
        }
        this.reader = input;
    }

    /**
     * Peeks next token from current reader state.
     */
    @Override
    public Token peekToken() throws IOException {
        if (bufferedToken != null) return bufferedToken;

        _beforeToken();
        int c = _peek();
        if (c == -1) return bufferedToken = Token.EOF;
        switch (c) {
            case '{': return bufferedToken = Token.START_OBJECT;
            case '}': return bufferedToken = Token.END_OBJECT;
            case '[': return bufferedToken = Token.START_ARRAY;
            case ']': return bufferedToken = Token.END_ARRAY;
            case '"': return bufferedToken = _expectsName() ? Token.FIELD_NAME : Token.STRING;
            case 't':
            case 'f': return bufferedToken = Token.BOOLEAN;
            case 'n': return bufferedToken = Token.NULL;
            case '-':
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
                return bufferedToken = Token.NUMBER;
            default:
                return bufferedToken = Token.UNKNOWN;
        }
    }

    @Override
    public void endDocument() throws IOException {
        bufferedToken = null;
        _skipWhitespace();
        int c = _peek();
        if (depth != 0 || c != -1) throw _error("expected end of document", c);
    }

    /**
     * Consumes and enters object scope.
     */
    @Override
    public void startObject() throws IOException {
        bufferedToken = null;
        PathSegment ps = _prepareValuePath();
        activePath = ps;
        int c = _read();
        if (c != '{') throw _error("expected '{'", c);
        _pushContainer(true, ps);
        activePath = null;
    }

    /**
     * Consumes and exits object scope.
     */
    @Override
    public void endObject() throws IOException {
        bufferedToken = null;
        activePath = _containerPath();
        _beforeEnd(true);
        int c = _read();
        if (c != '}') throw _error("expected '}'", c);
        _popContainer();
        _valueDone();
        activePath = null;
    }

    /**
     * Consumes and enters array scope.
     */
    @Override
    public void startArray() throws IOException {
        bufferedToken = null;
        PathSegment ps = _prepareValuePath();
        activePath = ps;
        int c = _read();
        if (c != '[') throw _error("expected '['", c);
        _pushContainer(false, ps);
        activePath = null;
    }

    /**
     * Consumes and exits array scope.
     */
    @Override
    public void endArray() throws IOException {
        bufferedToken = null;
        activePath = _containerPath();
        _beforeEnd(false);
        int c = _read();
        if (c != ']') throw _error("expected ']'", c);
        _popContainer();
        _valueDone();
        activePath = null;
    }

    /**
     * Reads next field name.
     */
    @Override
    public String nextName() throws IOException {
        bufferedToken = null;
        _beforeName();
        PathSegment parent = _containerPath();
        activePath = parent;
        String s = _readString();
        PathSegment namePath = new PathSegment.Name(parent, s);
        activePath = namePath;
        _skipWhitespace();
        int c = _read();
        if (c != ':') throw _error("expected ':'", c);
        pendingValuePath = namePath;
        containerStateStack[depth - 1] = OBJECT_VALUE;
        bufferedToken = null;
        activePath = null;
        return s;
    }

    /**
     * Reads next scalar as string.
     */
    @Override
    public String nextString() throws IOException {
        bufferedToken = null;
        activePath = _prepareValuePath();
        try {
            String value = _readString();
            _checkValueEnd();
            return value;
        } finally {
            activePath = null;
            _valueDone();
        }
    }

    /**
     * Reads next scalar as number.
     */
    @Override
    public Number nextNumber() throws IOException {
        return _readNumberValue("Invalid number literal", Numbers::parseNumber);
    }
    /**
     * Reads next scalar as long.
     */
    @Override
    public Long nextLong() throws IOException {
        return _readNumberValue("Invalid long literal", Long::parseLong);
    }
    /**
     * Reads next scalar as int.
     */
    @Override
    public Integer nextInt() throws IOException {
        return _readNumberValue("Invalid int literal", Integer::parseInt);
    }
    /**
     * Reads next scalar as short.
     */
    @Override
    public Short nextShort() throws IOException {
        return _readNumberValue("Invalid short literal", Short::parseShort);
    }
    /**
     * Reads next scalar as byte.
     */
    @Override
    public Byte nextByte() throws IOException {
        return _readNumberValue("Invalid byte literal", Byte::parseByte);
    }
    /**
     * Reads next scalar as double.
     */
    @Override
    public Double nextDouble() throws IOException {
        return _readNumberValue("Invalid double literal", Double::parseDouble);
    }
    /**
     * Reads next scalar as float.
     */
    @Override
    public Float nextFloat() throws IOException {
        return _readNumberValue("Invalid float literal", Float::parseFloat);
    }
    /**
     * Reads next scalar as BigInteger.
     */
    @Override
    public BigInteger nextBigInteger() throws IOException {
        return _readNumberValue("Invalid BigInteger literal", BigInteger::new);
    }
    /**
     * Reads next scalar as BigDecimal.
     */
    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        return _readNumberValue("Invalid BigDecimal literal", BigDecimal::new);
    }

    /**
     * Reads next scalar as boolean.
     */
    @Override
    public Boolean nextBoolean() throws IOException {
        bufferedToken = null;
        activePath = _prepareValuePath();
        try {
            Boolean value = _readBoolean();
            _checkValueEnd();
            return value;
        } finally {
            activePath = null;
            _valueDone();
        }
    }

    /**
     * Consumes next null token.
     */
    @Override
    public void nextNull() throws IOException {
        bufferedToken = null;
        activePath = _prepareValuePath();
        try {
            _readNull();
            _checkValueEnd();
        } finally {
            activePath = null;
            _valueDone();
        }
    }

    @Override
    public boolean nextIfNull() throws IOException {
        if (peekToken() != Token.NULL) return false;
        nextNull();
        return true;
    }

    @Override
    public boolean nextIfObjectEnd() throws IOException {
        if (peekToken() != Token.END_OBJECT) return false;
        endObject();
        return true;
    }

    @Override
    public boolean nextIfArrayEnd() throws IOException {
        if (peekToken() != Token.END_ARRAY) return false;
        endArray();
        return true;
    }


    /**
     * Skips next scalar or nested value.
     */
    @Override
    public void skipNext() throws IOException {
        Token token = peekToken();
        if (token.jsonType() == JsonType.UNKNOWN) throw _error("expected value", _peek());
        bufferedToken = null;
        PathSegment ps = _prepareValuePath();
        activePath = ps;
        try {
            _skipValue(ps);
        } finally {
            activePath = null;
            _valueDone();
        }
    }

    /**
     * Closes underlying reader.
     */
    @Override
    public void close() throws IOException {
        reader.close();
    }

    /// Private

    private int pos = 0;
    private int lastChar = -2; // -2 means unread
    private Token bufferedToken = null;
    private int[] containerStateStack = new int[8];
    private int depth = 0;
    private PathSegment currentContainerPath = null;
    private PathSegment activePath = null;
    private PathSegment pendingValuePath = null;

    // Object: first name, value after name, name/end after value. Array values
    // use their index while waiting for a value and -(index + 4) after one.
    private static final int OBJECT_FIRST_NAME = -1;
    private static final int OBJECT_VALUE = -2;
    private static final int OBJECT_NEXT_NAME = -3;

    private void _pushContainer(boolean object, PathSegment path) {
        if (depth == containerStateStack.length) {
            int nextSize = containerStateStack.length << 1;
            int[] nextState = new int[nextSize];
            System.arraycopy(containerStateStack, 0, nextState, 0, containerStateStack.length);
            containerStateStack = nextState;
        }
        containerStateStack[depth] = object ? OBJECT_FIRST_NAME : 0;
        depth++;
        currentContainerPath = path;
    }

    private void _popContainer() {
        if (depth == 0) return;
        depth--;
        containerStateStack[depth] = 0;
        currentContainerPath = currentContainerPath == null ? null : currentContainerPath.parent();
    }

    private PathSegment _containerPath() {
        return currentContainerPath == null ? PathSegment.Root.INSTANCE : currentContainerPath;
    }

    private PathSegment _prepareValuePath() throws IOException {
        _beforeValue();
        if (depth == 0) return PathSegment.Root.INSTANCE;
        if (pendingValuePath != null) {
            PathSegment ps = pendingValuePath;
            pendingValuePath = null;
            return ps;
        }
        int idx = depth - 1;
        int state = containerStateStack[idx];
        if (state < 0) {
            return _containerPath();
        }
        containerStateStack[idx] = -state - 4;
        return new PathSegment.Index(_containerPath(), state);
    }

    private boolean _expectsName() {
        return depth > 0 && containerStateStack[depth - 1] == OBJECT_FIRST_NAME;
    }

    private void _valueDone() {
        if (depth > 0 && containerStateStack[depth - 1] == OBJECT_VALUE) {
            containerStateStack[depth - 1] = OBJECT_NEXT_NAME;
        }
    }

    private <T> T _readNumberValue(String error, NumberParser<T> parser) throws IOException {
        bufferedToken = null;
        PathSegment ps = _prepareValuePath();
        activePath = ps;
        try {
            T value = parser.parse(_readNumberString());
            _checkValueEnd();
            return value;
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException(error, ps, e);
        } finally {
            activePath = null;
            _valueDone();
        }
    }

    private int _read() throws IOException {
        if (lastChar != -2) {
            int tmp = lastChar;
            lastChar = -2;
            return tmp;
        }
        pos++;
        return reader.read();
    }

    private int _peek() throws IOException {
        if (lastChar == -2) {
            pos++;
            lastChar = reader.read();
        }
        return lastChar;
    }

    private void _skipWhitespace() throws IOException {
        int c;
        while ((c = _peek()) != -1) {
            if (!_isJsonWhitespace(c)) return;
            _read();
        }
    }

    private void _beforeToken() throws IOException {
        _skipWhitespace();
        if (depth == 0) return;
        int idx = depth - 1;
        int state = containerStateStack[idx];
        int c = _peek();
        if (state == OBJECT_NEXT_NAME || state <= -4) {
            if ((state == OBJECT_NEXT_NAME && c == '}') || (state <= -4 && c == ']')) return;
            if (c != ',') throw _error("expected ',' or container end", c);
            _read();
            containerStateStack[idx] = state == OBJECT_NEXT_NAME ? OBJECT_FIRST_NAME : -state - 3;
            _skipWhitespace();
            c = _peek();
            if (c == '}' || c == ']') throw _error("trailing ',' in container", c);
            state = containerStateStack[idx];
        }
        if (state == OBJECT_FIRST_NAME) {
            if (c != '"' && c != '}') throw _error("expected field name or '}'", c);
        } else if (state == OBJECT_VALUE) {
            if (!_isValueStart(c)) throw _error("expected value", c);
        } else if (state >= 0 && c != ']' && !_isValueStart(c)) {
            throw _error("expected value or ']'", c);
        }
    }

    private void _beforeValue() throws IOException {
        _beforeToken();
        if (depth == 0) return;
        int state = containerStateStack[depth - 1];
        int c = _peek();
        if (state == OBJECT_FIRST_NAME) throw _error("expected field name", c);
        if (state >= 0 && c == ']') throw _error("expected value", c);
    }

    private void _beforeName() throws IOException {
        _beforeToken();
        int c = _peek();
        if (depth == 0 || containerStateStack[depth - 1] != OBJECT_FIRST_NAME || c != '"') {
            throw _error("expected field name", c);
        }
    }

    private void _beforeEnd(boolean object) throws IOException {
        _beforeToken();
        int c = _peek();
        int state = depth == 0 ? 0 : containerStateStack[depth - 1];
        if (depth == 0 || (object ? (state != OBJECT_FIRST_NAME && state != OBJECT_NEXT_NAME)
                : state < 0 && state > -4) || c != (object ? '}' : ']')) {
            throw _error(object ? "expected '}'" : "expected ']'", c);
        }
    }

    private boolean _isValueStart(int c) {
        return c == '"' || c == '{' || c == '[' || c == 't' || c == 'f' || c == 'n'
                || c == '-' || (c >= '0' && c <= '9');
    }

    private String _readString() throws IOException {
        int c = _read();
        if (c != '"') throw _error("expected '\"'", c);

        StringBuilder sb = new StringBuilder();
        while ((c = _read()) != -1) {
            if (c == '"') return sb.toString();
            if (c == '\\') {
                int e = _read();
                if (e == -1) throw _error("Unexpected EOF after escape '\\'", e);

                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        char ch = _readUnicodeEscape("Unexpected EOF in unicode escape",
                                "Invalid hex digit in \\u escape");

                        // surrogate pair handling
                        if (Character.isHighSurrogate(ch)) {
                            // expect another \\uXXXX
                            int b1 = _peek();
                            if (b1 == '\\') {
                                _read(); // consume '\'
                                int b2 = _read();
                                if (b2 == 'u') {
                                    char low = _readUnicodeEscape("Unexpected EOF in second \\u",
                                            "Invalid hex digit in second \\u");
                                    if (!Character.isLowSurrogate(low)) {
                                        throw _error("Invalid low surrogate", low);
                                    }
                                    sb.append(Character.toChars(Character.toCodePoint(ch, low)));
                                    break;
                                } else {
                                    throw _error("expected 'u' after '\\' for surrogate pair", b2);
                                }
                            }
                        }

                        if (Character.isHighSurrogate(ch)) {
                            throw _error("expected '\\u' for surrogate pair", _peek());
                        }
                        if (Character.isLowSurrogate(ch)) throw _error("Unexpected low surrogate", ch);
                        sb.append(ch);
                        break;
                    default:
                        throw _error("Invalid escape: \\", e);
                }
            } else {
                if (c < 0x20) throw _error("Unescaped control character in string", c);
                sb.append((char) c);
            }
        }
        throw _error("Unexpected EOF in string", -1);
    }

    private String _readNumberString() throws IOException {
        StringBuilder sb = new StringBuilder();
        int c = _peek();
        if (c == '-') {
            sb.append((char) _read());
            c = _peek();
        }
        if (c == '0') {
            sb.append((char) _read());
            c = _peek();
            if (c >= '0' && c <= '9') throw _error("Leading zero in number", c);
        } else if (c >= '1' && c <= '9') {
            do {
                sb.append((char) _read());
                c = _peek();
            } while (c >= '0' && c <= '9');
        } else {
            throw _error("Invalid number", c);
        }
        if (c == '.') {
            sb.append((char) _read());
            c = _peek();
            if (c < '0' || c > '9') throw _error("Invalid fraction", c);
            do {
                sb.append((char) _read());
                c = _peek();
            } while (c >= '0' && c <= '9');
        }
        if (c == 'e' || c == 'E') {
            sb.append((char) _read());
            c = _peek();
            if (c == '+' || c == '-') {
                sb.append((char) _read());
                c = _peek();
            }
            if (c < '0' || c > '9') throw _error("Invalid exponent", c);
            do {
                sb.append((char) _read());
                c = _peek();
            } while (c >= '0' && c <= '9');
        }
        return sb.toString();
    }

    private Boolean _readBoolean() throws IOException {
        int c = _peek();
        if (c == 't') {
            if (!(_read() == 't' && _read() == 'r' && _read() == 'u' && _read() == 'e')) {
                throw _error("expected 'true'", lastChar);
            }
            return Boolean.TRUE;
        } else if (c == 'f') {
            if (!(_read() == 'f' && _read() == 'a' && _read() == 'l' && _read() == 's' && _read() == 'e')) {
                throw _error("expected 'false'", lastChar);
            }
            return Boolean.FALSE;
        } else {
            throw _error("expected 'true' or 'false'", c);
        }
    }

    private void _readNull() throws IOException {
        if (!(_read() == 'n' && _read() == 'u' && _read() == 'l' && _read() == 'l')) {
            throw _error("expected 'null'",  lastChar);
        }
    }

    private boolean _isHexDigit(int c) {
        return (c >= '0' && c <= '9')
                || (c >= 'A' && c <= 'F')
                || (c >= 'a' && c <= 'f');
    }

    private int _hexDigitValue(int c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return -1;
    }

    private char _readUnicodeEscape(String eofMessage, String invalidMessage) throws IOException {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int h = _read();
            if (h == -1) throw _error(eofMessage, h);
            int digit = _hexDigitValue(h);
            if (digit < 0) throw _error(invalidMessage, h);
            value = (value << 4) | digit;
        }
        return (char) value;
    }

    private boolean _isJsonWhitespace(int c) {
        return c == ' ' || c == '\n' || c == '\r' || c == '\t';
    }

    private BindingException _error(String msg, int ch) {
        String c = (ch == -1) ? "EOF" : ("'" + (char) ch + "'");
        return new BindingException(msg + ", but got " + c + " at position " + pos,
                activePath != null ? activePath : _containerPath());
    }

    private void _skipString() throws IOException {
        int c = _read(); // consume opening "
        if (c != '"') throw _error("expected '\"'", c);
        while ((c = _read()) != -1) {
            if (c == '"') {
                _checkSkippedValueEnd();
                return;
            }
            if (c < 0x20) throw _error("Unescaped control character in string", c);
            if (c == '\\') { // escape
                int e = _read();
                if (e == -1) throw _error("Unexpected EOF in escape", e);
                if (e == 'u') {
                    char ch = _readUnicodeEscape("Unexpected EOF in unicode escape",
                            "Invalid hex digit in \\u escape");
                    if (Character.isHighSurrogate(ch)) {
                        int b1 = _peek();
                        if (b1 != '\\') throw _error("expected '\\u' for surrogate pair", b1);
                        _read();
                        int b2 = _read();
                        if (b2 != 'u') {
                            throw _error("expected 'u' after '\\' for surrogate pair", b2);
                        }
                        char low = _readUnicodeEscape("Unexpected EOF in second \\u",
                                "Invalid hex digit in second \\u");
                        if (!Character.isLowSurrogate(low)) throw _error("Invalid low surrogate", low);
                    } else if (Character.isLowSurrogate(ch)) {
                        throw _error("Unexpected low surrogate", ch);
                    }
                } else if (e != '"' && e != '\\' && e != '/' && e != 'b' && e != 'f'
                        && e != 'n' && e != 'r' && e != 't') {
                    throw _error("Invalid escape", e);
                }
            }
        }
        throw _error("Unexpected EOF in string", -1);
    }

    private void _skipNumber() throws IOException {
        int c = _peek();
        if (c == '-') {
            _read();
            c = _peek();
        }
        if (c == '0') {
            _read();
            c = _peek();
            if (c >= '0' && c <= '9') throw _error("Leading zero in number", c);
        } else if (c >= '1' && c <= '9') {
            do {
                _read();
                c = _peek();
            } while (c >= '0' && c <= '9');
        } else {
            throw _error("Invalid number", c);
        }
        if (c == '.') {
            _read();
            c = _peek();
            if (c < '0' || c > '9') throw _error("Invalid fraction", c);
            do {
                _read();
                c = _peek();
            } while (c >= '0' && c <= '9');
        }
        if (c == 'e' || c == 'E') {
            _read();
            c = _peek();
            if (c == '+' || c == '-') {
                _read();
                c = _peek();
            }
            if (c < '0' || c > '9') throw _error("Invalid exponent", c);
            do {
                _read();
                c = _peek();
            } while (c >= '0' && c <= '9');
        }
        _checkSkippedValueEnd();
    }

    private void _skipLiteral(String literal) throws IOException {
        for (int i = 0; i < literal.length(); i++) {
            int c = _read();
            if (c != literal.charAt(i)) throw _error("Invalid literal", c);
        }
        _checkSkippedValueEnd();
    }

    private void _checkSkippedValueEnd() throws IOException {
        _checkValueEnd();
    }

    private void _checkValueEnd() throws IOException {
        int c = _peek();
        if (_isJsonWhitespace(c) || c == -1) return;
        if (depth > 0) {
            int state = containerStateStack[depth - 1];
            if (c == ',' || (state == OBJECT_VALUE && c == '}') || (state <= -4 && c == ']')) return;
        }
        throw _error("Invalid character after value", c);
    }

    private void _skipObject(PathSegment objectPs) throws IOException {
        int c = _read();
        if (c != '{') throw _error("expected '{'", c);
        _pushContainer(true, objectPs);
        activePath = null;
        _skipWhitespace();
        if (_peek() == '}') { // empty object
            activePath = objectPs;
            _read();
            _popContainer();
            activePath = null;
            return;
        }
        while (true) {
            _skipWhitespace();
            PathSegment parent = _containerPath();
            activePath = parent;
            if (_peek() != '"') throw _error("expected '\"' for object key", _peek());
            String key = _readString();
            PathSegment keyPs = new PathSegment.Name(parent, key);
            activePath = keyPs;
            _skipWhitespace();
            c = _read();
            if (c != ':') throw _error("expected ':'", c);
            containerStateStack[depth - 1] = OBJECT_VALUE;
            _skipValue(keyPs);
            _valueDone();
            _skipWhitespace();
            activePath = parent;
            c = _read();
            if (c == ',') {
                continue;
            } else if (c == '}') {
                _popContainer();
                activePath = null;
                return;
            } else {
                throw _error("expected ',' or '}'", c);
            }
        }
    }

    private void _skipArray(PathSegment arrayPs) throws IOException {
        int c = _read();
        if (c != '[') throw _error("expected '['", c);
        _pushContainer(false, arrayPs);
        activePath = null;
        _skipWhitespace();
        if (_peek() == ']') { // empty array
            activePath = arrayPs;
            _read();
            _popContainer();
            activePath = null;
            return;
        }
        while (true) {
            int idx = depth - 1;
            int state = containerStateStack[idx];
            int nextIndex = state >= 0 ? state : -state - 3;
            containerStateStack[idx] = -nextIndex - 4;
            PathSegment elementPs = new PathSegment.Index(_containerPath(), nextIndex);
            _skipValue(elementPs);
            _skipWhitespace();
            activePath = _containerPath();
            c = _read();
            if (c == ',') {
                continue;
            } else if (c == ']') {
                _popContainer();
                activePath = null;
                return;
            } else {
                throw _error("expected ',' or ']'", c);
            }
        }
    }

    private void _skipValue(PathSegment ps) throws IOException {
        _skipWhitespace();
        activePath = ps;
        int c = _peek();
        if (c == -1) throw _error("expected value", c);
        switch (c) {
            case '"':
                _skipString();
                return;
            case '{':
                _skipObject(ps);
                return;
            case '[':
                _skipArray(ps);
                return;
            case 't':
                _skipLiteral("true");
                return;
            case 'f':
                _skipLiteral("false");
                return;
            case 'n':
                _skipLiteral("null");
                return;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    _skipNumber();
                    return;
                }
                throw _error("Unexpected token", c);
        }
    }
}
