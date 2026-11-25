package org.sjf4j.processor.code;

import org.sjf4j.CompiledInstances;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.util.Asserts;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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
    private final List<Member> configInitializers = new ArrayList<>();

    private boolean nodeConfigConstructor;
    private String backendBinderType;
    private boolean compiledBinderConfig;

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

    /**
     * Gives the generated implementation immutable per-instance runtime settings.
     * The no-arg constructor preserves the default settings contract.
     */
    public void enableNodeConfig() {
        nodeConfigConstructor = true;
    }

    /** Adds a constructor accepting the concrete backend Binder for a CompiledBinder. */
    public void enableBackendBinder(String binderType) {
        this.backendBinderType = Asserts.notNull(binderType, "binderType");
    }

    /**
     * Compiled Binders retain the NodeConfig injection constructor but ignore
     * its argument. One static config handles null omission; null inclusion
     * uses NodeConfig.DEFAULT. This does not affect other compiled APIs.
     */
    public void useCompiledBinderConfig() {
        compiledBinderConfig = true;
    }

    /** Adds an assignment after the generated constructor's config field is set. */
    public void addConfigInitializer(Member member) {
        configInitializers.add(Asserts.notNull(member, "member"));
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
        // Render once, in the original emission order. Some emitters allocate
        // names or update backend-specific state while emitting members.
        List<List<String>> fieldSource = captureGroup(out, fields);
        List<List<String>> initializerSource = captureGroup(out, configInitializers);
        List<List<String>> methodSource = captureGroup(out, methods);
        List<List<String>> helperSource = reachableHelpers(out, fieldSource, initializerSource, methodSource);

        boolean configUsed = false;
        for (List<List<String>> group : java.util.Arrays.asList(
                fieldSource, initializerSource, methodSource, helperSource)) {
            for (List<String> member : group) {
                // Instance field reads use this.config. A bare config in a
                // static validator helper or constructor is its parameter.
                // Binder's annotation-fixed static field may also be unqualified.
                if (compiledBinderConfig ? out.identifiers(member).contains("config")
                        : out.referencesInstanceConfig(member)) configUsed = true;
            }
        }
        boolean written = false;

        if (nodeConfigConstructor && configUsed) {
            if (!compiledBinderConfig) {
                out.line("private final org.sjf4j.NodeConfig config;");
            } else {
                out.line("private static final org.sjf4j.NodeConfig config = "
                        + "new org.sjf4j.NodeConfig(false);");
            }
            written = true;
        }

        if (!fields.isEmpty()) {
            if (written) out.blank();
            writeGroup(out, fieldSource);
            written = true;
        }

        if (nodeConfigConstructor) {
            if (written) out.blank();
            out.beginBlock("public " + simpleName + "()");
            out.line(backendBinderType == null
                    ? "this(org.sjf4j.NodeConfig.DEFAULT);"
                    : "this(new " + backendBinderType + "());");
            out.endBlock();
            out.blank();
            out.beginBlock("public " + simpleName + "(org.sjf4j.NodeConfig config)");
            if (backendBinderType == null) {
                out.line((configUsed ? "this.config = " : "")
                        + "org.sjf4j.util.Asserts.notNull(config, \"config\");");
                for (List<String> initializer : initializerSource) out.append(initializer);
            } else {
                out.line("this(new " + backendBinderType + "());");
            }
            out.endBlock();
            if (backendBinderType != null) {
                out.blank();
                out.beginBlock("public " + simpleName + "(" + backendBinderType + " backendBinder)");
                out.line("this.binder = org.sjf4j.util.Asserts.notNull(backendBinder, \"backendBinder\");");
                if (!compiledBinderConfig && configUsed) {
                    out.line("this.config = this.binder.config();");
                }
                for (List<String> initializer : initializerSource) out.append(initializer);
                out.endBlock();
            }
            written = true;
        }

        if (!methods.isEmpty()) {
            if (written) {
                out.blank();
            }

            writeGroup(out, methodSource);
            written = true;
        }

        if (!helperSource.isEmpty()) {
            if (written) out.blank();
            writeGroup(out, helperSource);
        }
    }

    private List<List<String>> reachableHelpers(JavaWriter out, List<List<String>> fieldSource,
                                                List<List<String>> initializerSource,
                                                List<List<String>> methodSource) {
        List<List<String>> source = new ArrayList<>();
        Map<String, Integer> byName = new HashMap<>();
        Set<Integer> reachable = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        for (List<List<String>> group : java.util.Arrays.asList(fieldSource, initializerSource, methodSource)) {
            for (List<String> member : group) pending.addAll(out.identifiers(member));
        }
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
            if (index != null && reachable.add(index)) pending.addAll(out.identifiers(source.get(index)));
        }
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            if (reachable.contains(i)) result.add(source.get(i));
        }
        return result;
    }

    private static List<List<String>> captureGroup(JavaWriter out, List<Member> members) {
        List<List<String>> source = new ArrayList<>(members.size());
        for (Member member : members) source.add(out.capture(() -> member.write(out)));
        return source;
    }

    private static void writeGroup(JavaWriter out, List<List<String>> members) {
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) out.blank();
            out.append(members.get(i));
        }
    }

    private static final class Helper {
        final String name;
        final Member member;

        Helper(String name, Member member) {
            this.name = name;
            this.member = member;
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
