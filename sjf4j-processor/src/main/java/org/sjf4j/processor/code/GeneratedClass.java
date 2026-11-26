package org.sjf4j.processor.code;

import org.sjf4j.CompiledInstances;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.util.Asserts;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents one generated implementation class.
 *
 * <p>Feature generators contribute fields, methods and helper members.
 * This class owns source-file creation and top-level class emission.</p>
 */
public final class GeneratedClass {

    public static final String IMPL_SUFFIX = CompiledInstances.IMPL_SUFFIX;

    private final ProcessorContext context;
    private final TypeElement origin;

    private final String packageName;
    private final String simpleName;
    private final String qualifiedName;
    private final String originName;
    private final boolean standardImpl;
    private final GeneratedNames names;

    private final List<Member> fields = new ArrayList<>();
    private final List<Member> methods = new ArrayList<>();
    private final List<Helper> helpers = new ArrayList<>();
    private final List<Member> constructorInitializers = new ArrayList<>();

    private String backendBinderType;

    private boolean valid = true;

    private GeneratedClass(
            ProcessorContext context,
            TypeElement origin,
            String suffix) {

        this.context = Asserts.notNull(context, "context");
        this.origin = Asserts.notNull(origin, "origin");
        Asserts.notNull(suffix, "suffix");
        PackageElement pkg = context.elements.getPackageOf(origin);

        this.packageName = pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();

        /*
         * Use the binary name so nested interfaces remain unique:
         *
         *   com.example.Outer.Inner
         *
         * becomes:
         *
         *   Outer$Inner_Impl
         */
        String binaryName = context.elements.getBinaryName(origin).toString();
        String packagePrefix = packageName.isEmpty() ? "" : packageName + '.';
        String binarySimpleName = packageName.isEmpty()
                ? binaryName : binaryName.substring(packagePrefix.length());
        this.simpleName = binarySimpleName + suffix;
        this.qualifiedName = packageName.isEmpty() ? simpleName : packageName + '.' + simpleName;
        this.originName = origin.getQualifiedName().toString();
        this.standardImpl = IMPL_SUFFIX.equals(suffix);
        this.names = new GeneratedNames(context.elements, origin, simpleName);
    }

    /*
     * --------------------------------------------------------------
     * Factory
     * --------------------------------------------------------------
     */

    /**
     * Creates the default generated implementation for an interface.
     */
    public static GeneratedClass forInterface(
            ProcessorContext context,
            TypeElement origin) {

        return new GeneratedClass(context, origin, IMPL_SUFFIX);
    }

    /**
     * Creates a generated implementation using a custom suffix.
     */
    public static GeneratedClass forInterface(
            ProcessorContext context,
            TypeElement origin,
            String suffix) {

        return new GeneratedClass(context, origin, suffix);
    }

    /*
     * --------------------------------------------------------------
     * Metadata
     * --------------------------------------------------------------
     */

    public TypeElement origin() {
        return origin;
    }

    public String packageName() {
        return packageName;
    }

    public String simpleName() {
        return simpleName;
    }

    public String qualifiedName() {
        return qualifiedName;
    }

    public String originName() {
        return originName;
    }

    /** Identifier allocator shared by contributors to this generated class. */
    public GeneratedNames names() {
        return names;
    }

    /*
     * --------------------------------------------------------------
     * Members
     * --------------------------------------------------------------
     */

    public void addField(Member member) {
        fields.add(Asserts.notNull(member, "member"));
    }

    public void addMethod(Member member) {
        methods.add(Asserts.notNull(member, "member"));
    }

    public void addHelper(Member member) {
        helpers.add(new Helper(null, Asserts.notNull(member, "member")));
    }

    /**
     * Registers one private helper with its allocated name. Only helpers
     * reachable from retained members are emitted. Unnamed member groups
     * remain roots; this is opt-in, not general Java dead-code elimination.
     */
    public void addHelper(String name, Member member) {
        helpers.add(new Helper(Asserts.notNull(name, "name"), Asserts.notNull(member, "member")));
    }

    /** Adds a constructor accepting the concrete backend Binder for a CompiledBinder. */
    public void enableBackendBinder(String binderType) {
        this.backendBinderType = Asserts.notNull(binderType, "binderType");
    }

    /** Adds an assignment to the generated constructor. */
    public void addConstructorInitializer(Member member) {
        constructorInitializers.add(Asserts.notNull(member, "member"));
    }

    public boolean isEmpty() {
        return fields.isEmpty()
                && methods.isEmpty()
                && helpers.isEmpty();
    }

    /*
     * --------------------------------------------------------------
     * Validation
     * --------------------------------------------------------------
     */

    /**
     * Prevents source emission for this generated class.
     */
    public void invalidate() {
        valid = false;
    }

    public boolean isValid() {
        return valid;
    }

    /*
     * --------------------------------------------------------------
     * Write
     * --------------------------------------------------------------
     */

    /**
     * Writes the generated Java implementation.
     */
    public void write() {
        if (!valid) return;

        try {
            JavaFileObject file = context.filer.createSourceFile(qualifiedName, origin);
            Writer writer = file.openWriter();

            try (JavaWriter out = new JavaWriter(writer, packageName, simpleName)) {
                writeClass(out);
            }
            if (standardImpl) context.recordCompiledInterface(origin);

        } catch (IOException e) {
            context.error(
                    origin,
                    "Failed to generate " +
                            qualifiedName +
                            ": " +
                            e.getMessage());
        }
    }

    private void writeClass(JavaWriter out) {
        out.line("// Generated by SJF4J");

        if (!packageName.isEmpty()) {
            out.line("package " + packageName + ";");
            out.blank();
        }

        out.beginBlock("public final class " + simpleName + " implements " + originName);

        writeMembers(out);

        out.endBlock();
    }

    private void writeMembers(JavaWriter out) {
        boolean selective = false;
        for (Helper helper : helpers) {
            if (helper.name != null) {
                selective = true;
                break;
            }
        }
        if (!selective) {
            writeRoots(out);
            for (Helper helper : helpers) {
                out.blank();
                helper.member.write(out);
            }
            return;
        }

        // Emit each member once, in its original order. Emitters may allocate
        // names or maintain backend-specific state while writing source.
        List<String> roots = out.capture(() -> writeRoots(out));
        out.append(roots);
        for (List<String> source : reachableHelpers(out, roots)) {
            out.blank();
            out.append(source);
        }
    }

    private void writeRoots(JavaWriter out) {
        if (!fields.isEmpty()) {
            writeGroup(out, fields);
            out.blank();
        }

        out.beginBlock("public " + simpleName + "()");
        if (backendBinderType == null) {
            for (Member initializer : constructorInitializers) initializer.write(out);
        } else {
            out.line("this(new " + backendBinderType + "());");
        }
        out.endBlock();
        if (backendBinderType != null) {
            out.blank();
            out.beginBlock("public " + simpleName + "(" + backendBinderType + " backendBinder)");
            out.line("this.binder = org.sjf4j.util.Asserts.notNull(backendBinder, \"backendBinder\").withExactNumbers(false);");
            for (Member initializer : constructorInitializers) initializer.write(out);
            out.endBlock();
        }

        if (!methods.isEmpty()) {
            out.blank();
            writeGroup(out, methods);
        }
    }

    private List<List<String>> reachableHelpers(JavaWriter out, List<String> roots) {
        List<List<String>> source = new ArrayList<>(helpers.size());
        Map<String, Integer> byName = new HashMap<>();
        Set<Integer> reachable = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(out.identifiers(roots));
        for (int i = 0; i < helpers.size(); i++) {
            Helper helper = helpers.get(i);
            List<String> body = out.capture(() -> helper.member.write(out));
            source.add(body);
            if (helper.name == null) {
                reachable.add(i);
                pending.addAll(out.identifiers(body));
            } else if (byName.put(helper.name, i) != null) {
                throw new IllegalStateException("Duplicate generated helper " + helper.name);
            }
        }
        while (!pending.isEmpty()) {
            Integer index = byName.get(pending.removeFirst());
            if (index != null && reachable.add(index)) {
                pending.addAll(out.identifiers(source.get(index)));
            }
        }
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            if (reachable.contains(i)) result.add(source.get(i));
        }
        return result;
    }

    private static final class Helper {
        final String name;
        final Member member;

        Helper(String name, Member member) {
            this.name = name;
            this.member = member;
        }
    }

    private static void writeGroup(JavaWriter out, List<Member> members) {
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) {
                out.blank();
            }

            members.get(i).write(out);
        }
    }

    /**
     * Emits one class member.
     *
     * <p>Keeping this functional interface nested avoids introducing a
     * separate GeneratedMember abstraction into the processor model.</p>
     */
    @FunctionalInterface
    public interface Member {

        void write(JavaWriter out);
    }
}
