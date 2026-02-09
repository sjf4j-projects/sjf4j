package org.sjf4j.facade.simple;

import org.sjf4j.facade.StreamingReader;
import org.sjf4j.node.Numbers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;

public class SimpleJsonReader implements StreamingReader {

    private final Reader reader;

    private int pos = 0;
    private int lastChar = -2; // -2 means unread
    private Token bufferedToken = null;

    public SimpleJsonReader(Reader input) {
        if (!(input instanceof BufferedReader)) {
            input = new BufferedReader(input);
        }
        this.reader = input;
    }

    private int read() throws IOException {
        if (lastChar != -2) {
            int tmp = lastChar;
            lastChar = -2;
            return tmp;
        }
        pos++;
        return reader.read();
    }

    private int peek() throws IOException {
        if (lastChar == -2) {
            pos++;
            lastChar = reader.read();
        }
        return lastChar;
    }

    private void skipWhitespace() throws IOException {
        int c;
        while ((c = peek()) != -1) {
            if (!Character.isWhitespace(c)) return;
            read();
        }
    }

    private void skipSeparators() throws IOException {
        int c;
        while ((c = peek()) != -1) {
            if (!isSeparator(c)) {
                return;
            }
            read();
        }
    }

    private boolean isSeparator(int c) {
        return Character.isWhitespace(c)
                || c == ','
                || c == ':';
    }


    @Override
    public Token peekToken() throws IOException {
        if (bufferedToken != null) return bufferedToken;

        skipSeparators();
        int c = peek();
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

    @Override
    public void startObject() throws IOException {
        bufferedToken = null;
        skipWhitespace();
        int c = read();
        if (c != '{') throw error("Expected '{'", c);
    }

    @Override
    public void endObject() throws IOException {
        bufferedToken = null;
        skipWhitespace();
        int c = read();
        if (c != '}') throw error("Expected '}'", c);
    }

    @Override
    public void startArray() throws IOException {
        bufferedToken = null;
        skipWhitespace();
        int c = read();
        if (c != '[') throw error("Expected '['", c);
    }

    @Override
    public void endArray() throws IOException {
        bufferedToken = null;
        skipWhitespace();
        int c = read();
        if (c != ']') throw error("Expected ']'", c);
    }

    @Override
    public String nextName() throws IOException {
        skipWhitespace();
        String s = readString();
        skipWhitespace();
        int c = read();
        if (c != ':') throw error("Expected ':'", c);
        bufferedToken = null;
        return s;
    }

    @Override
    public String nextString() throws IOException {
        bufferedToken = null;
        return readString();
    }

    @Override
    public Number nextNumber() throws IOException {
        bufferedToken = null;
        return Numbers.asNumber(readNumberString());
    }
    @Override
    public long nextLong() throws IOException {
        bufferedToken = null;
        return Long.parseLong(readNumberString());
    }
    @Override
    public int nextInt() throws IOException {
        bufferedToken = null;
        return Integer.parseInt(readNumberString());
    }
    @Override
    public short nextShort() throws IOException {
        bufferedToken = null;
        return Short.parseShort(readNumberString());
    }
    @Override
    public byte nextByte() throws IOException {
        bufferedToken = null;
        return Byte.parseByte(readNumberString());
    }
    @Override
    public double nextDouble() throws IOException {
        bufferedToken = null;
        return Double.parseDouble(readNumberString());
    }
    @Override
    public float nextFloat() throws IOException {
        bufferedToken = null;
        return Float.parseFloat(readNumberString());
    }
    @Override
    public BigInteger nextBigInteger() throws IOException {
        bufferedToken = null;
        return new BigInteger(readNumberString());
    }
    @Override
    public BigDecimal nextBigDecimal() throws IOException {
        bufferedToken = null;
        return new BigDecimal(readNumberString());
    }

    @Override
    public boolean nextBoolean() throws IOException {
        bufferedToken = null;
        return readBoolean();
    }

    @Override
    public void nextNull() throws IOException {
        bufferedToken = null;
        readNull();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    /// Read

    private String readString() throws IOException {
        int c = read();
        if (c != '"') throw error("Expected '\"'", c);

        StringBuilder sb = new StringBuilder();
        while ((c = read()) != -1) {
            if (c == '"') return sb.toString();
            if (c == '\\') {
                int e = read();
                if (e == -1) throw error("Unexpected EOF after escape '\\'", e);

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
                        char[] hex = new char[4];
                        for (int i = 0; i < 4; i++) {
                            int h = read();
                            if (h == -1) throw error("Unexpected EOF in unicode escape", h);
                            if (!isHexDigit(h)) throw error("Invalid hex digit in \\u escape", h);
                            hex[i] = (char) h;
                        }
                        char ch = (char) Integer.parseInt(new String(hex), 16);

                        // surrogate pair handling
                        if (Character.isHighSurrogate(ch)) {
                            // expect another \\uXXXX
                            int b1 = peek();
                            if (b1 == '\\') {
                                read(); // consume '\'
                                int b2 = read();
                                if (b2 == 'u') {
                                    char[] hex2 = new char[4];
                                    for (int i = 0; i < 4; i++) {
                                        int h = read();
                                        if (h == -1) throw error("Unexpected EOF in second \\u", h);
                                        if (!isHexDigit(h)) throw error("Invalid hex digit in second \\u", h);
                                        hex2[i] = (char) h;
                                    }
                                    char low = (char) Integer.parseInt(new String(hex2), 16);
                                    if (!Character.isLowSurrogate(low)) {
                                        throw error("Invalid low surrogate", low);
                                    }
                                    sb.append(Character.toChars(Character.toCodePoint(ch, low)));
                                    break;
                                } else {
                                    throw error("Expected 'u' after '\\' for surrogate pair", b2);
                                }
                            }
                        }

                        sb.append(ch);
                        break;
                    default:
                        throw error("Invalid escape: \\", e);
                }
            } else {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }

    private String readNumberString() throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = peek()) != -1) {
            if (Character.isDigit(c) || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                sb.append((char) read());
            } else break;
        }
        return sb.toString();
    }

    private Boolean readBoolean() throws IOException {
        int c = peek();
        if (c == 't') {
            if (!(read() == 't' && read() == 'r' && read() == 'u' && read() == 'e')) {
                throw error("Expected 'true'", lastChar);
            }
            return Boolean.TRUE;
        } else if (c == 'f') {
            if (!(read() == 'f' && read() == 'a' && read() == 'l' && read() == 's' && read() == 'e')) {
                throw error("Expected 'false'", lastChar);
            }
            return Boolean.FALSE;
        } else {
            throw error("Expected 'true' or 'false'", c);
        }
    }

    private void readNull() throws IOException {
        if (!(read() == 'n' && read() == 'u' && read() == 'l' && read() == 'l')) {
            throw error("Expected 'null'",  lastChar);
        }
    }

    private boolean isHexDigit(int c) {
        return (c >= '0' && c <= '9')
                || (c >= 'A' && c <= 'F')
                || (c >= 'a' && c <= 'f');
    }

    private IOException error(String msg, int ch) {
        String c = (ch == -1) ? "EOF" : ("'" + (char) ch + "'");
        return new IOException(msg + ", but got " + c + " at position " + pos);
    }

    /// Skip
    @Override
    public void nextSkip() throws IOException {
        bufferedToken = null;
        skipWhitespace();
        int c = peek();
        if (c == -1) return;
        switch (c) {
            case '"':
                skipString();
                return;
            case '{':
                skipObject();
                return;
            case '[':
                skipArray();
                return;
            case 't':
                skipLiteral("true");
                return;
            case 'f':
                skipLiteral("false");
                return;
            case 'n':
                skipLiteral("null");
                return;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    skipNumber();
                    return;
                }
                throw error("Unexpected token", c);
        }
    }

    private void skipString() throws IOException {
        int c = read(); // consume opening "
        if (c != '"') throw error("Expected '\"'", c);
        while ((c = read()) != -1) {
            if (c == '"') return; // end string
            if (c == '\\') { // escape
                int e = read();
                if (e == -1) throw error("Unexpected EOF in escape", e);
                if (e == 'u') {
                    for (int i = 0; i < 4; i++) {
                        int h = read();
                        if (h == -1) throw error("Unexpected EOF in unicode escape", h);
                        if (!isHexDigit(h)) throw error("Invalid hex digit in \\u escape", h);
                    }
                }
            }
        }
        throw error("Unexpected EOF in string", -1);
    }

    private void skipNumber() throws IOException {
        int c = read();
        while (true) {
            c = read();
            if (c == -1) return;
            if (c >= '0' && c <= '9') continue;
            if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') continue;
            lastChar = c;
            return;
        }
    }

    private void skipLiteral(String literal) throws IOException {
        for (int i = 0; i < literal.length(); i++) {
            read();
        }
    }

    private void skipObject() throws IOException {
        int c = read();
        if (c != '{') throw error("Expected '{'", c);
        skipWhitespace();
        if (peek() == '}') { // empty object
            read();
            return;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') throw error("Expected '\"' for object key", peek());
            skipString(); // key
            skipWhitespace();
            c = read();
            if (c != ':') throw error("Expected ':'", c);
            nextSkip();   // value
            skipWhitespace();
            c = read();
            if (c == ',') {
                continue;
            } else if (c == '}') {
                return;
            } else {
                throw error("Expected ',' or '}'", c);
            }
        }
    }

    private void skipArray() throws IOException {
        int c = read();
        if (c != '[') throw error("Expected '['", c);
        skipWhitespace();
        if (peek() == ']') { // empty array
            read();
            return;
        }
        while (true) {
            nextSkip();   // element
            skipWhitespace();
            c = read();
            if (c == ',') {
                continue;
            } else if (c == ']') {
                return;
            } else {
                throw error("Expected ',' or ']'", c);
            }
        }
    }
}
