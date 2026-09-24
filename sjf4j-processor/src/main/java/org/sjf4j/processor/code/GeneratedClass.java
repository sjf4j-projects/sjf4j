package org.sjf4j.processor.code;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.util.Asserts;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents one generated implementation class.
 *
 * <p>Feature generators contribute fields, methods and helper members.
 * This class owns source-file creation and top-level class emission.</p>
 */
public final class GeneratedClass {

    public static final String IMPL_SUFFIX = "_Impl";


    private final ProcessorContext context;
    private final TypeElement origin;

    private final String packageName;
    private final String simpleName;
    private final String qualifiedName;
    private final String originName;

    private final List<Member> fields =
            new ArrayList<Member>();

    private final List<Member> methods =
            new ArrayList<Member>();

    private final List<Member> helpers =
            new ArrayList<Member>();

    private boolean valid = true;


    private GeneratedClass(
            ProcessorContext context,
            TypeElement origin,
            String suffix) {

        this.context =
                Asserts.notNull(
                        context,
                        "context");

        this.origin =
                Asserts.notNull(
                        origin,
                        "origin");

        Asserts.notNull(
                suffix,
                "suffix");

        PackageElement pkg =
                context.elements.getPackageOf(origin);

        this.packageName =
                pkg.isUnnamed()
                        ? ""
                        : pkg.getQualifiedName().toString();

        /*
         * Use the binary name so nested interfaces remain unique:
         *
         *   com.example.Outer.Inner
         *
         * becomes:
         *
         *   Outer$Inner_Impl
         */
        String binaryName =
                context.elements
                        .getBinaryName(origin)
                        .toString();

        String packagePrefix =
                packageName.isEmpty()
                        ? ""
                        : packageName + '.';

        String binarySimpleName =
                packageName.isEmpty()
                        ? binaryName
                        : binaryName.substring(
                        packagePrefix.length());

        this.simpleName =
                binarySimpleName + suffix;

        this.qualifiedName =
                packageName.isEmpty()
                        ? simpleName
                        : packageName + '.' + simpleName;

        this.originName =
                origin.getQualifiedName().toString();
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

        return new GeneratedClass(
                context,
                origin,
                IMPL_SUFFIX);
    }

    /**
     * Creates a generated implementation using a custom suffix.
     */
    public static GeneratedClass forInterface(
            ProcessorContext context,
            TypeElement origin,
            String suffix) {

        return new GeneratedClass(
                context,
                origin,
                suffix);
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


    /*
     * --------------------------------------------------------------
     * Members
     * --------------------------------------------------------------
     */

    public void addField(Member member) {
        fields.add(
                Asserts.notNull(
                        member,
                        "member"));
    }

    public void addMethod(Member member) {
        methods.add(
                Asserts.notNull(
                        member,
                        "member"));
    }

    public void addHelper(Member member) {
        helpers.add(
                Asserts.notNull(
                        member,
                        "member"));
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
        if (!valid) {
            return;
        }

        try {
            JavaFileObject file =
                    context.filer.createSourceFile(
                            qualifiedName,
                            origin);

            Writer writer =
                    file.openWriter();

            try (JavaWriter out =
                         new JavaWriter(
                                 writer,
                                 packageName,
                                 simpleName)) {

                writeClass(out);
            }

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
            out.line(
                    "package " +
                            packageName +
                            ";");

            out.blank();
        }

        out.beginBlock(
                "public final class " +
                        simpleName +
                        " implements " +
                        originName);

        writeMembers(out);

        out.endBlock();
    }


    private void writeMembers(JavaWriter out) {
        boolean written = false;

        if (!fields.isEmpty()) {
            writeGroup(out, fields);
            written = true;
        }

        if (!methods.isEmpty()) {
            if (written) {
                out.blank();
            }

            writeGroup(out, methods);
            written = true;
        }

        if (!helpers.isEmpty()) {
            if (written) {
                out.blank();
            }

            writeGroup(out, helpers);
        }
    }


    private static void writeGroup(
            JavaWriter out,
            List<Member> members) {

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
