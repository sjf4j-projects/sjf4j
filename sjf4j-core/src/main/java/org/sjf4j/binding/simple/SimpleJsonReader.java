package org.sjf4j.binding.simple;

import org.sjf4j.JsonType;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.Numbers;
import org.sjf4j.path.PathSegment;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;


/**
 * Minimal JSON reader for the built-in facade.
 */
public final class SimpleJsonReader implements StreamingReader {

    private final Reader reader;

    /**
     * Creates reader over input characters.
     */
    public SimpleJsonReader(Reader input) {
        this.reader = input;
    }

    /**
     * Peeks next token from current reader state.
     */
    @Override
    public Token peekToken() throws IOException {
        if (bufferedToken != null) return bufferedToken;

        boolean fieldName = _prepareToken();
        int c = _peek();
        if (c == -1) return bufferedToken = Token.EOF;
        switch (c) {
            case '{': return bufferedToken = Token.START_OBJECT;
            case '}': return bufferedToken = Token.END_OBJECT;
            case '[': return bufferedToken = Token.START_ARRAY;
            case ']': return bufferedToken = Token.END_ARRAY;
            case '"': return bufferedToken = fieldName ? Token.NAME : Token.STRING;
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
        _prepareValuePath();
        int c = _read();
        if (c != '{') throw _error("expected '{'", c);
        _pushContainer(true);
        _clearActivePath();
    }

    /**
     * Consumes and exits object scope.
     */
    @Override
    public void endObject() throws IOException {
        bufferedToken = null;
        _activateContainerPath();
        _beforeEnd(true);
        int c = _read();
        if (c != '}') throw _error("expected '}'", c);
        _popContainer();
        _valueDone();
        _clearActivePath();
    }

    /**
     * Consumes and enters array scope.
     */
    @Override
    public void startArray() throws IOException {
        bufferedToken = null;
        _prepareValuePath();
        int c = _read();
        if (c != '[') throw _error("expected '['", c);
        _pushContainer(false);
        _clearActivePath();
    }

    /**
     * Consumes and exits array scope.
     */
    @Override
    public void endArray() throws IOException {
        bufferedToken = null;
        _activateContainerPath();
        _beforeEnd(false);
        int c = _read();
        if (c != ']') throw _error("expected ']'", c);
        _popContainer();
        _valueDone();
        _clearActivePath();
    }

    /**
     * Reads next field name.
     */
    @Override
    public String nextName() throws IOException {
        bufferedToken = null;
        _beforeName();
        _activateContainerPath();
        String s = _readString();
        _activateNamePath(s);
        _skipWhitespace();
        int c = _read();
        if (c != ':') throw _error("expected ':'", c);
        pendingName = s;
        containerStateStack[depth - 1] = OBJECT_VALUE;
        bufferedToken = null;
        _clearActivePath();
        return s;
    }

    /**
     * Reads next scalar as string.
     */
    @Override
    public String nextString() throws IOException {
        bufferedToken = null;
        _prepareValuePath();
        try {
            String value = _readString();
            _checkValueEnd();
            return value;
        } finally {
            _clearActivePath();
            _valueDone();
        }
    }

    /**
     * Reads next scalar as number.
     */

    @Override
    public Number nextNumber() throws IOException {
        return _readNumberValue();
    }

    @Override
    public long nextLongValue() throws IOException {
        return _readLongValue(Long.MIN_VALUE, Long.MAX_VALUE, "invalid long literal");
    }

    @Override
    public int nextIntValue() throws IOException {
        return (int) _readLongValue(Integer.MIN_VALUE, Integer.MAX_VALUE, "invalid int literal");
    }

    @Override
    public short nextShortValue() throws IOException {
        return (short) _readLongValue(Short.MIN_VALUE, Short.MAX_VALUE, "invalid short literal");
    }

    @Override
    public byte nextByteValue() throws IOException {
        return (byte) _readLongValue(Byte.MIN_VALUE, Byte.MAX_VALUE, "invalid byte literal");
    }

    @Override
    public double nextDoubleValue() throws IOException {
        return _readDoubleValue(false);
    }

    @Override
    public float nextFloatValue() throws IOException {
        return (float) _readDoubleValue(true);
    }

    /**
     * Reads next scalar as BigInteger.
     */
    @Override
    public BigInteger nextBigInteger() throws IOException {
        return _readBigIntegerValue();
    }

    /**
     * Reads next scalar as BigDecimal.
     */
    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        return _readBigDecimalValue();
    }

    @Override
    public boolean nextBooleanValue() throws IOException {
        bufferedToken = null;
        _prepareValuePath();
        try {
            boolean value = _readBoolean();
            _checkValueEnd();
            return value;
        } finally {
            _clearActivePath();
            _valueDone();
        }
    }

    /**
     * Consumes next null token.
     */
    @Override
    public void nextNull() throws IOException {
        bufferedToken = null;
        _prepareValuePath();
        try {
            _readNull();
            _checkValueEnd();
        } finally {
            _clearActivePath();
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
        _prepareValuePath();
        try {
            _skipValue();
        } finally {
            _clearActivePath();
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

    private static final int BUFFER_SIZE = 8192;
    private static final int NUMBER_BUFFER_INITIAL_SIZE = 32;
    /** Do not retain an input-sized array after parsing an exceptional numeric value. */
    private static final int NUMBER_BUFFER_RETAIN_CAP = 1024;
    private final char[] inputBuffer = new char[BUFFER_SIZE];
    private int inputPos;
    private int inputLimit;
    private boolean inputEof;
    private int pos = 0;
    private Token bufferedToken = null;
    private int[] containerStateStack = new int[8];
    // The path to each open container.  Entries are primitive/string values so
    // ordinary successful reads do not allocate PathSegment chains.
    private int[] pathKindStack = new int[8]; // 0 root, 1 name, 2 index
    private int[] pathIndexStack = new int[8];
    private String[] pathNameStack = new String[8];
    private int depth = 0;
    private String pendingName;
    private int activePathDepth;
    private int activePathKind;
    private int activePathIndex;
    private String activePathName;

    // Object: first name, value after name, name/end after value. Array values
    // use their index while waiting for a value and -(index + 4) after one.
    private static final int OBJECT_FIRST_NAME = -1;
    private static final int OBJECT_VALUE = -2;
    private static final int OBJECT_NEXT_NAME = -3;

    private void _pushContainer(boolean object) {
        if (depth == containerStateStack.length) {
            int nextSize = containerStateStack.length << 1;
            int[] nextState = new int[nextSize];
            int[] nextKind = new int[nextSize];
            int[] nextIndex = new int[nextSize];
            String[] nextName = new String[nextSize];
            System.arraycopy(containerStateStack, 0, nextState, 0, containerStateStack.length);
            System.arraycopy(pathKindStack, 0, nextKind, 0, pathKindStack.length);
            System.arraycopy(pathIndexStack, 0, nextIndex, 0, pathIndexStack.length);
            System.arraycopy(pathNameStack, 0, nextName, 0, pathNameStack.length);
            containerStateStack = nextState;
            pathKindStack = nextKind;
            pathIndexStack = nextIndex;
            pathNameStack = nextName;
        }
        containerStateStack[depth] = object ? OBJECT_FIRST_NAME : 0;
        pathKindStack[depth] = activePathKind;
        pathIndexStack[depth] = activePathIndex;
        pathNameStack[depth] = activePathName;
        depth++;
    }

    private void _popContainer() {
        if (depth == 0) return;
        depth--;
        containerStateStack[depth] = 0;
        pathKindStack[depth] = 0;
        pathIndexStack[depth] = 0;
        pathNameStack[depth] = null;
    }

    private void _activateContainerPath() {
        activePathDepth = depth;
        activePathKind = 0;
        activePathName = null;
    }

    private void _activateNamePath(String name) {
        activePathDepth = depth;
        activePathKind = 1;
        activePathName = name;
    }

    private void _activateIndexPath(int index) {
        activePathDepth = depth;
        activePathKind = 2;
        activePathIndex = index;
        activePathName = null;
    }

    private void _clearActivePath() {
        activePathDepth = depth;
        activePathKind = 0;
        activePathName = null;
    }

    private void _prepareValuePath() throws IOException {
        _beforeValue();
        if (depth == 0) {
            _activateContainerPath();
            return;
        }
        if (pendingName != null) {
            _activateNamePath(pendingName);
            pendingName = null;
            return;
        }
        int idx = depth - 1;
        int state = containerStateStack[idx];
        if (state < 0) {
            _activateContainerPath();
            return;
        }
        containerStateStack[idx] = -state - 4;
        _activateIndexPath(state);
    }

    private void _valueDone() {
        if (depth > 0 && containerStateStack[depth - 1] == OBJECT_VALUE) {
            containerStateStack[depth - 1] = OBJECT_NEXT_NAME;
        }
    }

    private Number _readNumberValue() throws IOException {
        bufferedToken = null;
        _prepareValuePath();
        try {
            _scanNumber(true);
            Number value = Numbers.parseNumber(new String(numberBuffer, 0, numberLength));
            _checkValueEnd();
            return value;
        } catch (IOException e) {
            throw e;
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("invalid number literal", _path(), e);
        } finally {
            _clearActivePath();
            _valueDone();
            _releaseNumberBuffer();
        }
    }

    private long _readLongValue(long min, long max, String error) throws IOException {
        bufferedToken = null;
        _prepareValuePath();
        try {
            _scanNumber(false);
            if (numberFraction || numberExponent || numberOverflow || numberValue < min || numberValue > max) {
                throw new BindingException(error, _path());
            }
            _checkValueEnd();
            return numberValue;
        } finally {
            _clearActivePath();
            _valueDone();
            _releaseNumberBuffer();
        }
    }

    private double _readDoubleValue(boolean floatValue) throws IOException {
        bufferedToken = null;
        _prepareValuePath();
        try {
            _scanNumber(true);
            // The JDK floating parsers are substantially faster than BigDecimal for
            // primitive conversion. They require a String, so this creates one
            // short-lived text object per float/double value, but avoids creating
            // BigDecimal and its larger internal representation.
            double value = floatValue
                    ? Float.parseFloat(new String(numberBuffer, 0, numberLength))
                    : Double.parseDouble(new String(numberBuffer, 0, numberLength));
            if (!Double.isFinite(value) || (floatValue && !Float.isFinite((float) value))) {
                throw new BindingException(floatValue ? "invalid float literal" : "invalid double literal", _path());
            }
            _checkValueEnd();
            return value;
        } catch (NumberFormatException e) {
            throw new BindingException(floatValue ? "invalid float literal" : "invalid double literal", _path(), e);
        } finally {
            _clearActivePath();
            _valueDone();
            _releaseNumberBuffer();
        }
    }

    private BigInteger _readBigIntegerValue() throws IOException {
        bufferedToken = null;
        _prepareValuePath();
        try {
            _scanNumber(true);
            if (numberFraction || numberExponent) throw new BindingException("invalid BigInteger literal", _path());
            BigInteger value = new BigInteger(new String(numberBuffer, 0, numberLength));
            _checkValueEnd();
            return value;
        } catch (NumberFormatException e) {
            throw new BindingException("invalid BigInteger literal", _path(), e);
        } finally { _clearActivePath(); _valueDone(); _releaseNumberBuffer(); }
    }

    private BigDecimal _readBigDecimalValue() throws IOException {
        bufferedToken = null;
        _prepareValuePath();
        try {
            _scanNumber(true);
            BigDecimal value = new BigDecimal(numberBuffer, 0, numberLength);
            _checkValueEnd();
            return value;
        } catch (NumberFormatException e) {
            throw new BindingException("invalid BigDecimal literal", _path(), e);
        } finally { _clearActivePath(); _valueDone(); _releaseNumberBuffer(); }
    }

    private int _read() throws IOException {
        if (inputPos == inputLimit && !_fillBuffer()) return -1;
        pos++;
        return inputBuffer[inputPos++];
    }

    private int _peek() throws IOException {
        if (inputPos == inputLimit && !_fillBuffer()) return -1;
        return inputBuffer[inputPos];
    }

    private boolean _fillBuffer() throws IOException {
        if (inputEof) return false;
        int read;
        do {
            read = reader.read(inputBuffer, 0, inputBuffer.length);
        } while (read == 0);
        if (read < 0) {
            inputEof = true;
            inputLimit = 0;
            inputPos = 0;
            return false;
        }
        inputLimit = read;
        inputPos = 0;
        return true;
    }

    private void _skipWhitespace() throws IOException {
        int c;
        while ((c = _peek()) != -1) {
            if (!_isJsonWhitespace(c)) return;
            _read();
        }
    }

    /**
     * Validates and normalizes the current container position before token classification.
     *
     * @return whether the current position accepts an object field name
     */
    private boolean _prepareToken() throws IOException {
        _skipWhitespace();
        if (depth == 0) return false;
        int idx = depth - 1;
        int state = containerStateStack[idx];
        int c = _peek();
        if (state == OBJECT_NEXT_NAME || state <= -4) {
            if ((state == OBJECT_NEXT_NAME && c == '}') || (state <= -4 && c == ']')) return false;
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
        return state == OBJECT_FIRST_NAME;
    }

    private void _beforeValue() throws IOException {
        _prepareToken();
        if (depth == 0) return;
        int state = containerStateStack[depth - 1];
        int c = _peek();
        if (state == OBJECT_FIRST_NAME) throw _error("expected field name", c);
        if (state >= 0 && c == ']') throw _error("expected value", c);
    }

    private void _beforeName() throws IOException {
        _prepareToken();
        int c = _peek();
        if (depth == 0 || containerStateStack[depth - 1] != OBJECT_FIRST_NAME || c != '"') {
            throw _error("expected field name", c);
        }
    }

    private void _beforeEnd(boolean object) throws IOException {
        _prepareToken();
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
                if (e == -1) throw _error("unexpected EOF after escape '\\'", e);

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
                        char ch = _readUnicodeEscape("unexpected EOF in unicode escape",
                                "invalid hex digit in \\u escape");

                        // surrogate pair handling
                        if (Character.isHighSurrogate(ch)) {
                            // expect another \\uXXXX
                            int b1 = _peek();
                            if (b1 == '\\') {
                                _read(); // consume '\'
                                int b2 = _read();
                                if (b2 == 'u') {
                                    char low = _readUnicodeEscape("unexpected EOF in second \\u",
                                            "invalid hex digit in second \\u");
                                    if (!Character.isLowSurrogate(low)) {
                                        throw _error("invalid low surrogate", low);
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
                        if (Character.isLowSurrogate(ch)) throw _error("unexpected low surrogate", ch);
                        sb.append(ch);
                        break;
                    default:
                        throw _error("Invalid escape: \\", e);
                }
            } else {
                if (c < 0x20) throw _error("unescaped control character in string", c);
                sb.append((char) c);
            }
        }
        throw _error("unexpected EOF in string", -1);
    }

    private char[] numberBuffer = new char[NUMBER_BUFFER_INITIAL_SIZE];
    private int numberLength;
    private long numberValue;
    private boolean numberOverflow;
    private boolean numberFraction;
    private boolean numberExponent;

    /** Scans JSON's number grammar and accumulates an integral value when possible. */
    private void _scanNumber(boolean retainText) throws IOException {
        numberLength = 0;
        numberValue = 0;
        numberOverflow = false;
        numberFraction = false;
        numberExponent = false;
        int c = _peek();
        boolean negative = c == '-';
        if (c == '-') {
            _numberChar(_read(), retainText);
            c = _peek();
        }
        if (c == '0') {
            _numberDigit(_read(), negative, retainText);
            c = _peek();
            if (c >= '0' && c <= '9') throw _error("leading zero in number", c);
        } else if (c >= '1' && c <= '9') {
            do {
                _numberDigit(_read(), negative, retainText);
                c = _peek();
            } while (c >= '0' && c <= '9');
        } else {
            throw _error("invalid number", c);
        }
        if (c == '.') {
            numberFraction = true;
            _numberChar(_read(), retainText);
            c = _peek();
            if (c < '0' || c > '9') throw _error("invalid fraction", c);
            do {
                _numberChar(_read(), retainText);
                c = _peek();
            } while (c >= '0' && c <= '9');
        }
        if (c == 'e' || c == 'E') {
            numberExponent = true;
            _numberChar(_read(), retainText);
            c = _peek();
            if (c == '+' || c == '-') {
                _numberChar(_read(), retainText);
                c = _peek();
            }
            if (c < '0' || c > '9') throw _error("invalid exponent", c);
            do {
                _numberChar(_read(), retainText);
                c = _peek();
            } while (c >= '0' && c <= '9');
        }
        if (!negative && !numberOverflow) {
            // The negative accumulator represents both Long.MIN_VALUE and the
            // positive value one past Long.MAX_VALUE; only the latter overflows.
            if (numberValue == Long.MIN_VALUE) numberOverflow = true;
            else numberValue = -numberValue;
        }
    }

    private void _numberDigit(int c, boolean negative, boolean retainText) {
        _numberChar(c, retainText);
        int digit = c - '0';
        if (numberValue < (Long.MIN_VALUE + digit) / 10) {
            numberOverflow = true;
        } else if (!numberOverflow) {
            numberValue = numberValue * 10 - digit;
        }
    }

    private void _numberChar(int c, boolean retainText) {
        if (!retainText) return;
        if (numberLength == numberBuffer.length) {
            char[] next = new char[numberLength << 1];
            System.arraycopy(numberBuffer, 0, next, 0, numberLength);
            numberBuffer = next;
        }
        numberBuffer[numberLength++] = (char) c;
    }

    private void _releaseNumberBuffer() {
        numberLength = 0;
        if (numberBuffer.length > NUMBER_BUFFER_RETAIN_CAP) {
            numberBuffer = new char[NUMBER_BUFFER_INITIAL_SIZE];
        }
    }

    private boolean _readBoolean() throws IOException {
        int c = _peek();
        if (c == 't') {
            if (!(_read() == 't' && _read() == 'r' && _read() == 'u' && _read() == 'e')) {
                throw _error("expected 'true'", _peek());
            }
            return true;
        } else if (c == 'f') {
            if (!(_read() == 'f' && _read() == 'a' && _read() == 'l' && _read() == 's' && _read() == 'e')) {
                throw _error("expected 'false'", _peek());
            }
            return false;
        } else {
            throw _error("expected 'true' or 'false'", c);
        }
    }

    private void _readNull() throws IOException {
        if (!(_read() == 'n' && _read() == 'u' && _read() == 'l' && _read() == 'l')) {
            throw _error("expected 'null'",  _peek());
        }
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
        return new BindingException(msg + ", but got " + c + " at position " + pos, _path());
    }

    private PathSegment _path() {
        PathSegment path = PathSegment.Root.INSTANCE;
        for (int i = 0; i < activePathDepth; i++) {
            if (pathKindStack[i] == 1) path = new PathSegment.Name(path, pathNameStack[i]);
            else if (pathKindStack[i] == 2) path = new PathSegment.Index(path, pathIndexStack[i]);
        }
        if (activePathKind == 1) return new PathSegment.Name(path, activePathName);
        if (activePathKind == 2) return new PathSegment.Index(path, activePathIndex);
        return path;
    }

    private void _skipString() throws IOException {
        int c = _read(); // consume opening "
        if (c != '"') throw _error("expected '\"'", c);
        while ((c = _read()) != -1) {
            if (c == '"') {
                _checkValueEnd();
                return;
            }
            if (c < 0x20) throw _error("unescaped control character in string", c);
            if (c == '\\') { // escape
                int e = _read();
                if (e == -1) throw _error("unexpected EOF in escape", e);
                if (e == 'u') {
                    char ch = _readUnicodeEscape("unexpected EOF in unicode escape",
                            "invalid hex digit in \\u escape");
                    if (Character.isHighSurrogate(ch)) {
                        int b1 = _peek();
                        if (b1 != '\\') throw _error("expected '\\u' for surrogate pair", b1);
                        _read();
                        int b2 = _read();
                        if (b2 != 'u') {
                            throw _error("expected 'u' after '\\' for surrogate pair", b2);
                        }
                        char low = _readUnicodeEscape("unexpected EOF in second \\u",
                                "invalid hex digit in second \\u");
                        if (!Character.isLowSurrogate(low)) throw _error("invalid low surrogate", low);
                    } else if (Character.isLowSurrogate(ch)) {
                        throw _error("unexpected low surrogate", ch);
                    }
                } else if (e != '"' && e != '\\' && e != '/' && e != 'b' && e != 'f'
                        && e != 'n' && e != 'r' && e != 't') {
                    throw _error("invalid escape", e);
                }
            }
        }
        throw _error("unexpected EOF in string", -1);
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
            if (c >= '0' && c <= '9') throw _error("leading zero in number", c);
        } else if (c >= '1' && c <= '9') {
            do {
                _read();
                c = _peek();
            } while (c >= '0' && c <= '9');
        } else {
            throw _error("invalid number", c);
        }
        if (c == '.') {
            _read();
            c = _peek();
            if (c < '0' || c > '9') throw _error("invalid fraction", c);
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
            if (c < '0' || c > '9') throw _error("invalid exponent", c);
            do {
                _read();
                c = _peek();
            } while (c >= '0' && c <= '9');
        }
        _checkValueEnd();
    }

    private void _skipLiteral(String literal) throws IOException {
        for (int i = 0; i < literal.length(); i++) {
            int c = _read();
            if (c != literal.charAt(i)) throw _error("invalid literal", c);
        }
        _checkValueEnd();
    }

    private void _checkValueEnd() throws IOException {
        int c = _peek();
        if (_isJsonWhitespace(c) || c == -1) return;
        if (depth > 0) {
            int state = containerStateStack[depth - 1];
            if (c == ',' || (state == OBJECT_VALUE && c == '}') || (state <= -4 && c == ']')) return;
        }
        throw _error("invalid character after value", c);
    }

    private void _skipObject() throws IOException {
        int c = _read();
        if (c != '{') throw _error("expected '{'", c);
        _pushContainer(true);
        _clearActivePath();
        _skipWhitespace();
        if (_peek() == '}') { // empty object
            _activateContainerPath();
            _read();
            _popContainer();
            _clearActivePath();
            return;
        }
        while (true) {
            _skipWhitespace();
            _activateContainerPath();
            if (_peek() != '"') throw _error("expected '\"' for object key", _peek());
            String key = _readString();
            _activateNamePath(key);
            _skipWhitespace();
            c = _read();
            if (c != ':') throw _error("expected ':'", c);
            containerStateStack[depth - 1] = OBJECT_VALUE;
            _skipValue();
            _valueDone();
            _skipWhitespace();
            _activateContainerPath();
            c = _read();
            if (c == ',') {
                continue;
            } else if (c == '}') {
                _popContainer();
                _clearActivePath();
                return;
            } else {
                throw _error("expected ',' or '}'", c);
            }
        }
    }

    private void _skipArray() throws IOException {
        int c = _read();
        if (c != '[') throw _error("expected '['", c);
        _pushContainer(false);
        _clearActivePath();
        _skipWhitespace();
        if (_peek() == ']') { // empty array
            _activateContainerPath();
            _read();
            _popContainer();
            _clearActivePath();
            return;
        }
        while (true) {
            int idx = depth - 1;
            int state = containerStateStack[idx];
            int nextIndex = state >= 0 ? state : -state - 3;
            containerStateStack[idx] = -nextIndex - 4;
            _activateIndexPath(nextIndex);
            _skipValue();
            _skipWhitespace();
            _activateContainerPath();
            c = _read();
            if (c == ',') {
                continue;
            } else if (c == ']') {
                _popContainer();
                _clearActivePath();
                return;
            } else {
                throw _error("expected ',' or ']'", c);
            }
        }
    }

    private void _skipValue() throws IOException {
        _skipWhitespace();
        int c = _peek();
        if (c == -1) throw _error("expected value", c);
        switch (c) {
            case '"':
                _skipString();
                return;
            case '{':
                _skipObject();
                return;
            case '[':
                _skipArray();
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
                throw _error("unexpected token", c);
        }
    }
}
