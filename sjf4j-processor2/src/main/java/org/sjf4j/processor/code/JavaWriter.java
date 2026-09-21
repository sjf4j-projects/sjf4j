package org.sjf4j.processor.code;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal writer for generated Java source code.
 *
 * <p>The writer buffers source lines until close so fully qualified type names
 * can be shortened and imported automatically when there is no naming
 * conflict.</p>
 */
public final class JavaWriter implements Closeable {

    private static final Pattern QUALIFIED_TYPE = Pattern.compile(
            "(?<![A-Za-z0-9_$])" +
                    "(?:[a-z_$][A-Za-z0-9_$]*\\.)+" +
                    "[A-Z_$][A-Za-z0-9_$]*" +
                    "(?:\\.[A-Z_$][A-Za-z0-9_$]*)*");

    private static final Pattern SIMPLE_TYPE = Pattern.compile(
            "(?<![A-Za-z0-9_$.])" +
                    "[A-Z_$][A-Za-z0-9_$]*" +
                    "(?![A-Za-z0-9_$.])");


    private final Writer writer;
    private final String packageName;
    private final String simpleName;

    private final List<String> lines = new ArrayList<String>();

    private int indent;
    private boolean closed;


    public JavaWriter(
            Writer writer,
            String packageName,
            String simpleName) {

        this.writer = Objects.requireNonNull(writer, "writer");
        this.packageName =
                packageName == null ? "" : packageName;
        this.simpleName =
                Objects.requireNonNull(simpleName, "simpleName");
    }


    // -------------------------------------------------------------------------
    // Lines
    // -------------------------------------------------------------------------

    /**
     * Writes one source line using the current indentation.
     */
    public void line(String line) {
        Objects.requireNonNull(line, "line");

        StringBuilder out =
                new StringBuilder(line.length() + indent * 4);

        for (int i = 0; i < indent; i++) {
            out.append("    ");
        }

        out.append(line);
        lines.add(out.toString());
    }

    /**
     * Writes an empty source line.
     */
    public void blank() {
        lines.add("");
    }


    // -------------------------------------------------------------------------
    // Indentation
    // -------------------------------------------------------------------------

    public void indent() {
        indent++;
    }

    public void dedent() {
        if (indent == 0) {
            throw new IllegalStateException(
                    "Cannot dedent below zero");
        }

        indent--;
    }

    /**
     * Writes {@code header + " {"} and enters the block.
     */
    public void beginBlock(String header) {
        line(header + " {");
        indent();
    }

    /**
     * Leaves the current block and writes {@code }}.
     */
    public void endBlock() {
        dedent();
        line("}");
    }

    /**
     * Leaves the current block and writes {@code }suffix}.
     *
     * <p>Useful for forms such as {@code } else { }.</p>
     */
    public void endBlock(String suffix) {
        dedent();
        line("}" + suffix);
    }


    // -------------------------------------------------------------------------
    // Literals
    // -------------------------------------------------------------------------

    /**
     * Returns a source-safe Java string literal.
     */
    public static String stringLiteral(String value) {
        Objects.requireNonNull(value, "value");

        StringBuilder out =
                new StringBuilder(value.length() + 2);

        out.append('"');

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '"':
                    out.append("\\\"");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        appendUnicodeEscape(out, c);
                    } else {
                        out.append(c);
                    }
            }
        }

        out.append('"');

        return out.toString();
    }

    private static void appendUnicodeEscape(
            StringBuilder out,
            char c) {

        out.append("\\u");

        for (int shift = 12; shift >= 0; shift -= 4) {
            int digit = (c >> shift) & 0xF;

            out.append(
                    (char) (digit < 10
                            ? '0' + digit
                            : 'a' + digit - 10));
        }
    }


    // -------------------------------------------------------------------------
    // Imports
    // -------------------------------------------------------------------------

    private Map<String, String> resolveImports() {
        Map<String, String> names =
                new HashMap<String, String>();

        Map<String, Boolean> conflicts =
                new HashMap<String, Boolean>();

        /*
         * The generated class itself always owns its simple name.
         */
        names.put(simpleName, null);

        /*
         * First reserve explicitly used simple type names.
         */
        for (String line : lines) {
            String code = codeOnly(line);

            Matcher matcher =
                    SIMPLE_TYPE.matcher(code);

            while (matcher.find()) {
                String name = matcher.group();

                if (!names.containsKey(name)) {
                    names.put(name, null);
                }
            }
        }

        /*
         * Then discover fully qualified type names.
         */
        for (String line : lines) {
            String code = codeOnly(line);

            Matcher matcher =
                    QUALIFIED_TYPE.matcher(code);

            while (matcher.find()) {
                ShortType type =
                        shortType(matcher.group());

                if (type == null) {
                    continue;
                }

                if (!names.containsKey(type.simpleName)) {
                    names.put(
                            type.simpleName,
                            type.importName);
                    continue;
                }

                String existing =
                        names.get(type.simpleName);

                /*
                 * A simple name already exists in generated source.
                 * A different imported type cannot take that name.
                 */
                if (existing == null) {
                    if (type.importName != null) {
                        conflicts.put(
                                type.simpleName,
                                Boolean.TRUE);
                    }
                    continue;
                }

                if (!existing.equals(type.importName)) {
                    conflicts.put(
                            type.simpleName,
                            Boolean.TRUE);
                }
            }
        }

        List<Map.Entry<String, String>> entries =
                new ArrayList<Map.Entry<String, String>>(
                        names.entrySet());

        Collections.sort(
                entries,
                (a, b) -> {
                    String av = a.getValue();
                    String bv = b.getValue();

                    if (av == null) {
                        return bv == null
                                ? a.getKey().compareTo(b.getKey())
                                : -1;
                    }

                    if (bv == null) {
                        return 1;
                    }

                    int compare = av.compareTo(bv);

                    return compare != 0
                            ? compare
                            : a.getKey().compareTo(b.getKey());
                });

        Map<String, String> result =
                new LinkedHashMap<String, String>();

        for (Map.Entry<String, String> entry : entries) {
            if (entry.getValue() == null) {
                continue;
            }

            if (conflicts.containsKey(entry.getKey())) {
                continue;
            }

            result.put(
                    entry.getKey(),
                    entry.getValue());
        }

        return result;
    }


    /**
     * Converts one fully qualified reference into its import and use form.
     *
     * <p>For example:</p>
     *
     * <pre>
     * java.util.Map.Entry
     *     import: java.util.Map
     *     use:    Map.Entry
     * </pre>
     */
    private ShortType shortType(String qualifiedName) {
        String[] parts = qualifiedName.split("\\.");

        int classIndex = -1;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            if (part.isEmpty()) {
                return null;
            }

            char first = part.charAt(0);

            if (Character.isUpperCase(first)
                    || first == '$'
                    || first == '_') {

                classIndex = i;
                break;
            }
        }

        if (classIndex <= 0) {
            return null;
        }

        StringBuilder packageBuilder =
                new StringBuilder();

        for (int i = 0; i < classIndex; i++) {
            if (i > 0) {
                packageBuilder.append('.');
            }

            packageBuilder.append(parts[i]);
        }

        StringBuilder useBuilder =
                new StringBuilder();

        for (int i = classIndex; i < parts.length; i++) {
            if (i > classIndex) {
                useBuilder.append('.');
            }

            useBuilder.append(parts[i]);
        }

        String typePackage =
                packageBuilder.toString();

        String topLevelType =
                parts[classIndex];

        String importName =
                typePackage + '.' + topLevelType;

        if ("java.lang".equals(typePackage)
                || packageName.equals(typePackage)) {

            importName = null;
        }

        return new ShortType(
                topLevelType,
                importName,
                useBuilder.toString());
    }


    // -------------------------------------------------------------------------
    // Transformation
    // -------------------------------------------------------------------------

    private String transform(
            String line,
            Map<String, String> imports) {

        StringBuilder out =
                new StringBuilder(line.length());

        int index = 0;

        while (index < line.length()) {
            char c = line.charAt(index);

            if (c == '"' || c == '\'') {
                int end =
                        quotedEnd(line, index, c);

                out.append(line, index, end);
                index = end;
                continue;
            }

            if (c == '/'
                    && index + 1 < line.length()
                    && line.charAt(index + 1) == '/') {

                out.append(
                        line,
                        index,
                        line.length());
                break;
            }

            if (c == '/'
                    && index + 1 < line.length()
                    && line.charAt(index + 1) == '*') {

                int end =
                        blockCommentEnd(line, index);

                out.append(line, index, end);
                index = end;
                continue;
            }

            int end =
                    nextSpecial(line, index);

            out.append(
                    transformCode(
                            line.substring(index, end),
                            imports));

            index = end;
        }

        return out.toString();
    }

    private String transformCode(
            String code,
            Map<String, String> imports) {

        Matcher matcher =
                QUALIFIED_TYPE.matcher(code);

        StringBuffer out =
                new StringBuffer(code.length());

        while (matcher.find()) {
            ShortType type =
                    shortType(matcher.group());

            if (type == null) {
                continue;
            }

            String imported =
                    imports.get(type.simpleName);

            if (type.importName == null
                    || type.importName.equals(imported)) {

                matcher.appendReplacement(
                        out,
                        Matcher.quoteReplacement(
                                type.useName));
            }
        }

        matcher.appendTail(out);

        return out.toString();
    }


    /**
     * Returns code portions of the line for import discovery.
     */
    private String codeOnly(String line) {
        StringBuilder out =
                new StringBuilder(line.length());

        int index = 0;

        while (index < line.length()) {
            char c = line.charAt(index);

            if (c == '"' || c == '\'') {
                int end =
                        quotedEnd(line, index, c);

                appendSpaces(
                        out,
                        end - index);

                index = end;
                continue;
            }

            if (c == '/'
                    && index + 1 < line.length()
                    && line.charAt(index + 1) == '/') {

                break;
            }

            if (c == '/'
                    && index + 1 < line.length()
                    && line.charAt(index + 1) == '*') {

                int end =
                        blockCommentEnd(line, index);

                appendSpaces(
                        out,
                        end - index);

                index = end;
                continue;
            }

            out.append(c);
            index++;
        }

        return out.toString();
    }

    private static void appendSpaces(
            StringBuilder out,
            int count) {

        for (int i = 0; i < count; i++) {
            out.append(' ');
        }
    }

    private static int quotedEnd(
            String line,
            int start,
            char quote) {

        int index = start + 1;
        boolean escaped = false;

        while (index < line.length()) {
            char c = line.charAt(index++);

            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == quote) {
                break;
            }
        }

        return index;
    }

    private static int blockCommentEnd(
            String line,
            int start) {

        int end =
                line.indexOf("*/", start + 2);

        return end < 0
                ? line.length()
                : end + 2;
    }

    private static int nextSpecial(
            String line,
            int start) {

        int index = start;

        while (index < line.length()) {
            char c = line.charAt(index);

            if (c == '"' || c == '\'') {
                return index;
            }

            if (c == '/'
                    && index + 1 < line.length()) {

                char next =
                        line.charAt(index + 1);

                if (next == '/' || next == '*') {
                    return index;
                }
            }

            index++;
        }

        return index;
    }


    // -------------------------------------------------------------------------
    // Output
    // -------------------------------------------------------------------------

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;

        try {
            writeSource();
        } finally {
            writer.close();
        }
    }

    private void writeSource() throws IOException {
        Map<String, String> imports =
                resolveImports();

        int packageLine = -1;

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("package ")) {
                packageLine = i;
                break;
            }
        }

        /*
         * Unnamed package: imports must appear before generated declarations.
         */
        if (packageLine < 0 && !imports.isEmpty()) {
            writeImports(imports);
        }

        for (int i = 0; i < lines.size(); i++) {
            String line =
                    transform(lines.get(i), imports);

            writer.write(line);
            writer.write('\n');

            /*
             * Named package: imports follow the package declaration.
             */
            if (i == packageLine && !imports.isEmpty()) {
                writer.write('\n');
                writeImports(imports);

                /*
                 * Avoid duplicate blank lines already buffered after package.
                 */
                while (i + 1 < lines.size()
                        && lines.get(i + 1).isEmpty()) {

                    i++;
                }
            }
        }
    }

    private void writeImports(
            Map<String, String> imports)
            throws IOException {

        for (String importName : imports.values()) {
            writer.write("import ");
            writer.write(importName);
            writer.write(";\n");
        }

        writer.write('\n');
    }


    private static final class ShortType {

        final String simpleName;
        final String importName;
        final String useName;

        ShortType(
                String simpleName,
                String importName,
                String useName) {

            this.simpleName = simpleName;
            this.importName = importName;
            this.useName = useName;
        }
    }
}