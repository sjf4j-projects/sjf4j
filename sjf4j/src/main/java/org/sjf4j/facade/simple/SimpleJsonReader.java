package org.sjf4j.facade.simple;

import org.sjf4j.exception.BindingException;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.node.Numbers;
import org.sjf4j.path.PathSegment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;


/**
 * Minimal JSON reader for the built-in facade.
 */
public class SimpleJsonReader implements StreamingReader {

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

        _skipSeparators();
        int c = _peek();
        if (c == -1) return bufferedToken = Token.UNKNOWN;
        switch (c) {
            case '{': return bufferedToken = Token.START_OBJECT;
            case '}': return bufferedToken = Token.END_OBJECT;
            case '[': return bufferedToken = Token.START_ARRAY;
            case ']': return bufferedToken = Token.END_ARRAY;
            case '"': return bufferedToken = Token.STRING;
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

    /**
     * Consumes and enters object scope.
     */
    @Override
    public void startObject() throws IOException {
        bufferedToken = null;
        _skipWhitespace();
        PathSegment ps = _prepareValuePath();
        activePath = ps;
        int c = _read();
        if (c != '{') throw _error("Expected '{'", c);
        _pushContainer(true, ps);
        activePath = null;
    }

    /**
     * Consumes and exits object scope.
     */
    @Override
    public void endObject() throws IOException {
        bufferedToken = null;
        _skipWhitespace();
        activePath = _containerPath();
        int c = _read();
        if (c != '}') throw _error("Expected '}'", c);
        _popContainer();
        activePath = null;
    }

    /**
     * Consumes and enters array scope.
     */
    @Override
    public void startArray() throws IOException {
        bufferedToken = null;
        _skipWhitespace();
        PathSegment ps = _prepareValuePath();
        activePath = ps;
        int c = _read();
        if (c != '[') throw _error("Expected '['", c);
        _pushContainer(false, ps);
        activePath = null;
    }

    /**
     * Consumes and exits array scope.
     */
    @Override
    public void endArray() throws IOException {
        bufferedToken = null;
        _skipWhitespace();
        activePath = _containerPath();
        int c = _read();
        if (c != ']') throw _error("Expected ']'", c);
        _popContainer();
        activePath = null;
    }

    /**
     * Reads next field name.
     */
    @Override
    public String nextName() throws IOException {
        _skipWhitespace();
        PathSegment parent = _containerPath();
        activePath = parent;
        String s = _readString();
        PathSegment namePath = new PathSegment.Name(parent, Map.class, s);
        activePath = namePath;
        _skipWhitespace();
        int c = _read();
        if (c != ':') throw _error("Expected ':'", c);
        pendingValuePath = namePath;
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
            return _readString();
        } finally {
            activePath = null;
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
            return _readBoolean();
        } finally {
            activePath = null;
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
        } finally {
            activePath = null;
        }
    }

    /**
     * Skips next scalar or nested value.
     */
    @Override
    public void skipNext() throws IOException {
        bufferedToken = null;
        PathSegment ps = _prepareValuePath();
        activePath = ps;
        try {
            _skipValue(ps);
        } finally {
            activePath = null;
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

    private void _pushContainer(boolean object, PathSegment path) {
        if (depth == containerStateStack.length) {
            int nextSize = containerStateStack.length << 1;
            int[] nextState = new int[nextSize];
            System.arraycopy(containerStateStack, 0, nextState, 0, containerStateStack.length);
            containerStateStack = nextState;
        }
        containerStateStack[depth] = object ? -1 : 0;
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

    private PathSegment _prepareValuePath() {
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
        containerStateStack[idx] = state + 1;
        return new PathSegment.Index(_containerPath(), List.class, state);
    }

    private <T> T _readNumberValue(String error, NumberParser<T> parser) throws IOException {
        bufferedToken = null;
        PathSegment ps = _prepareValuePath();
        activePath = ps;
        try {
            return parser.parse(_readNumberString());
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException(error, ps, e);
        } finally {
            activePath = null;
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

    private void _skipSeparators() throws IOException {
        int c;
        while ((c = _peek()) != -1) {
            if (!_isSeparator(c)) {
                return;
            }
            _read();
        }
    }

    private boolean _isSeparator(int c) {
        return _isJsonWhitespace(c)
                || c == ','
                || c == ':';
    }

    private String _readString() throws IOException {
        int c = _read();
        if (c != '"') throw _error("Expected '\"'", c);

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
                                    throw _error("Expected 'u' after '\\' for surrogate pair", b2);
                                }
                            }
                        }

                        sb.append(ch);
                        break;
                    default:
                        throw _error("Invalid escape: \\", e);
                }
            } else {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }

    private String _readNumberString() throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = _peek()) != -1) {
            if (_isNumberChar(c)) {
                sb.append((char) _read());
            } else break;
        }
        return sb.toString();
    }

    private Boolean _readBoolean() throws IOException {
        int c = _peek();
        if (c == 't') {
            if (!(_read() == 't' && _read() == 'r' && _read() == 'u' && _read() == 'e')) {
                throw _error("Expected 'true'", lastChar);
            }
            return Boolean.TRUE;
        } else if (c == 'f') {
            if (!(_read() == 'f' && _read() == 'a' && _read() == 'l' && _read() == 's' && _read() == 'e')) {
                throw _error("Expected 'false'", lastChar);
            }
            return Boolean.FALSE;
        } else {
            throw _error("Expected 'true' or 'false'", c);
        }
    }

    private void _readNull() throws IOException {
        if (!(_read() == 'n' && _read() == 'u' && _read() == 'l' && _read() == 'l')) {
            throw _error("Expected 'null'",  lastChar);
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

    private boolean _isNumberChar(int c) {
        return (c >= '0' && c <= '9')
                || c == '-'
                || c == '+'
                || c == '.'
                || c == 'e'
                || c == 'E';
    }

    private BindingException _error(String msg, int ch) {
        String c = (ch == -1) ? "EOF" : ("'" + (char) ch + "'");
        return new BindingException(msg + ", but got " + c + " at position " + pos,
                activePath != null ? activePath : _containerPath());
    }

    private void _skipString() throws IOException {
        int c = _read(); // consume opening "
        if (c != '"') throw _error("Expected '\"'", c);
        while ((c = _read()) != -1) {
            if (c == '"') return; // end string
            if (c == '\\') { // escape
                int e = _read();
                if (e == -1) throw _error("Unexpected EOF in escape", e);
                if (e == 'u') {
                    for (int i = 0; i < 4; i++) {
                        int h = _read();
                        if (h == -1) throw _error("Unexpected EOF in unicode escape", h);
                        if (!_isHexDigit(h)) throw _error("Invalid hex digit in \\u escape", h);
                    }
                }
            }
        }
        throw _error("Unexpected EOF in string", -1);
    }

    private void _skipNumber() throws IOException {
        int c = _read();
        while (true) {
            c = _read();
            if (c == -1) return;
            if (c >= '0' && c <= '9') continue;
            if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') continue;
            lastChar = c;
            return;
        }
    }

    private void _skipLiteral(String literal) throws IOException {
        for (int i = 0; i < literal.length(); i++) {
            _read();
        }
    }

    private void _skipObject(PathSegment objectPs) throws IOException {
        int c = _read();
        if (c != '{') throw _error("Expected '{'", c);
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
            if (_peek() != '"') throw _error("Expected '\"' for object key", _peek());
            String key = _readString();
            PathSegment keyPs = new PathSegment.Name(parent, Map.class, key);
            activePath = keyPs;
            _skipWhitespace();
            c = _read();
            if (c != ':') throw _error("Expected ':'", c);
            _skipValue(keyPs);
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
                throw _error("Expected ',' or '}'", c);
            }
        }
    }

    private void _skipArray(PathSegment arrayPs) throws IOException {
        int c = _read();
        if (c != '[') throw _error("Expected '['", c);
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
            int nextIndex = containerStateStack[idx];
            containerStateStack[idx] = nextIndex + 1;
            PathSegment elementPs = new PathSegment.Index(_containerPath(), List.class, nextIndex);
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
                throw _error("Expected ',' or ']'", c);
            }
        }
    }

    private void _skipValue(PathSegment ps) throws IOException {
        _skipWhitespace();
        activePath = ps;
        int c = _peek();
        if (c == -1) return;
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
