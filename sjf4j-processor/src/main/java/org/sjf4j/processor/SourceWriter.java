package org.sjf4j.processor;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

/**
 * Minimal indentation-aware writer for generated Java source files.
 */
public final class SourceWriter implements Closeable {

    private static final Pattern QUALIFIED_TYPE = Pattern.compile(
            "(?<![A-Za-z0-9_$])(?:[a-z_$][A-Za-z0-9_$]*\\.)+[A-Z_$][A-Za-z0-9_$]*(?:\\.[A-Z_$][A-Za-z0-9_$]*)*");
    private static final Pattern SIMPLE_TYPE = Pattern.compile(
            "(?<![A-Za-z0-9_$.])[A-Z_$][A-Za-z0-9_$]*(?![A-Za-z0-9_$.])");

    private final Writer writer;
    private final String packageName;
    private final String simpleName;
    private final List<String> lines = new ArrayList<>();
    private int indent;

    /**
     * Opens a generated source file for the supplied qualified class name.
     */
    public SourceWriter(ProcessorContext ctx, Element origin, String qualifiedName) throws IOException {
        JavaFileObject file = ctx.filer.createSourceFile(qualifiedName, origin);
        this.writer = file.openWriter();
        int dot = qualifiedName.lastIndexOf('.');
        this.packageName = dot < 0 ? "" : qualifiedName.substring(0, dot);
        this.simpleName = dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
    }

    /**
     * Increases indentation for subsequent lines.
     */
    public void indent() { indent++; }

    /**
     * Decreases indentation for subsequent lines.
     */
    public void dedent() { indent--; }

    /**
     * Writes one source line using the current indentation level.
     */
    public void line(String line) {
        StringBuilder b = new StringBuilder(line.length() + indent * 4);
        for (int i = 0; i < indent; i++) b.append("    ");
        b.append(line);
        lines.add(b.toString());
    }

    private Map<String, String> imports() {
        Map<String, String> simpleToImport = new HashMap<>();
        Map<String, String> conflicts = new HashMap<>();
        simpleToImport.put(simpleName, null);

        for (String line : lines) {
            String code = codePrefix(line);
            Matcher simple = SIMPLE_TYPE.matcher(code);
            while (simple.find()) {
                if (!simpleToImport.containsKey(simple.group())) simpleToImport.put(simple.group(), null);
            }
            Matcher m = QUALIFIED_TYPE.matcher(code);
            while (m.find()) {
                ShortType type = shortType(m.group());
                if (type == null) continue;
                String existing = simpleToImport.get(type.simpleName);
                if (existing == null && simpleToImport.containsKey(type.simpleName)) {
                    if (type.importName != null) conflicts.put(type.simpleName, type.simpleName);
                    continue;
                }
                if (existing != null && !existing.equals(type.importName)) {
                    conflicts.put(type.simpleName, type.simpleName);
                    continue;
                }
                simpleToImport.put(type.simpleName, type.importName);
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        List<Map.Entry<String, String>> entries = new ArrayList<>(simpleToImport.entrySet());
        Collections.sort(entries, (a, b) -> {
            String av = a.getValue();
            String bv = b.getValue();
            if (av == null) return bv == null ? a.getKey().compareTo(b.getKey()) : -1;
            if (bv == null) return 1;
            int c = av.compareTo(bv);
            return c != 0 ? c : a.getKey().compareTo(b.getKey());
        });
        for (Map.Entry<String, String> e : entries) {
            if (conflicts.containsKey(e.getKey())) continue;
            if (e.getValue() != null) result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    private String transform(String line, Map<String, String> imports) {
        StringBuilder out = new StringBuilder(line.length());
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '"' || c == '\'') {
                int end = quotedEnd(line, i, c);
                out.append(line, i, end);
                i = end;
            } else if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                int end = blockCommentEnd(line, i);
                out.append(line, i, end);
                i = end;
            } else if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                out.append(line, i, line.length());
                break;
            } else {
                int end = nextSpecial(line, i);
                out.append(transformCode(line.substring(i, end), imports));
                i = end;
            }
        }
        return out.toString();
    }

    private String transformCode(String code, Map<String, String> imports) {
        Matcher m = QUALIFIED_TYPE.matcher(code);
        StringBuffer b = new StringBuffer(code.length());
        while (m.find()) {
            ShortType type = shortType(m.group());
            if (type == null) continue;
            String importName = imports.get(type.simpleName);
            if (type.importName == null || type.importName.equals(importName)) {
                m.appendReplacement(b, Matcher.quoteReplacement(type.useName));
            }
        }
        m.appendTail(b);
        return b.toString();
    }

    private String codePrefix(String line) {
        StringBuilder out = new StringBuilder(line.length());
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '"' || c == '\'') {
                int end = quotedEnd(line, i, c);
                for (int j = i; j < end; j++) out.append(' ');
                i = end;
            } else if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                int end = blockCommentEnd(line, i);
                for (int j = i; j < end; j++) out.append(' ');
                i = end;
            } else if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                break;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private int nextSpecial(String line, int start) {
        int i = start;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '"' || c == '\'') return i;
            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') return i;
            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*') return i;
            i++;
        }
        return i;
    }

    private int quotedEnd(String line, int start, char quote) {
        int i = start + 1;
        boolean escaped = false;
        while (i < line.length()) {
            char c = line.charAt(i++);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == quote) {
                break;
            }
        }
        return i;
    }

    private int blockCommentEnd(String line, int start) {
        int end = line.indexOf("*/", start + 2);
        return end < 0 ? line.length() : end + 2;
    }

    private ShortType shortType(String qualifiedName) {
        String[] parts = qualifiedName.split("\\.");
        int classAt = -1;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) return null;
            char c = parts[i].charAt(0);
            if (Character.isUpperCase(c) || c == '$' || c == '_') {
                classAt = i;
                break;
            }
        }
        if (classAt <= 0) return null;

        StringBuilder pkg = new StringBuilder();
        for (int i = 0; i < classAt; i++) {
            if (i > 0) pkg.append('.');
            pkg.append(parts[i]);
        }
        StringBuilder use = new StringBuilder();
        for (int i = classAt; i < parts.length; i++) {
            if (i > classAt) use.append('.');
            use.append(parts[i]);
        }

        String typePackage = pkg.toString();
        String top = parts[classAt];
        // Import the top-level type and keep any nested type or static member
        // suffix in the use name, e.g. java.util.Map.Entry -> Map.Entry and
        // org.example.Kind.VALUE -> Kind.VALUE.
        String importName = typePackage + '.' + top;
        if ("java.lang".equals(typePackage) || typePackage.equals(packageName)) importName = null;
        return new ShortType(top, importName, use.toString());
    }

    /**
     * Closes the underlying source writer.
     */
    @Override
    public void close() throws IOException {
        try {
            Map<String, String> imports = imports();
            boolean insertedImports = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = transform(lines.get(i), imports);
                writer.write(line);
                writer.write('\n');
                if (!insertedImports && line.startsWith("package ")) {
                    insertedImports = true;
                    if (!imports.isEmpty()) {
                        writer.write('\n');
                        for (String importName : imports.values()) {
                            writer.write("import ");
                            writer.write(importName);
                            writer.write(";\n");
                        }
                        writer.write('\n');
                        while (i + 1 < lines.size() && lines.get(i + 1).isEmpty()) i++;
                    }
                }
            }
        } finally {
            writer.close();
        }
    }

    private static final class ShortType {
        final String simpleName;
        final String importName;
        final String useName;

        ShortType(String simpleName, String importName, String useName) {
            this.simpleName = simpleName;
            this.importName = importName;
            this.useName = useName;
        }
    }

}
