package org.sjf4j.processor.schema;

import org.sjf4j.processor.code.GeneratedClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Emits aggregate diagnostics for independently evaluated, statically typed
 * schema assertions. The actual predicates are emitted by SchemaEmitter, so
 * boolean, void and result methods use identical comparisons.
 *
 * <p>Applicators and dynamic object shape/evaluated tracking are deliberately
 * left to SchemaPlan: they require scoped error suppression or evaluated-state
 * handling that cannot be reproduced by collecting branch failures.</p>
 */
final class SchemaResultEmitter {
    private final GeneratedClass generated;
    private final String prefix;
    private final SchemaEmitter checks;
    private int sequence;

    SchemaResultEmitter(GeneratedClass generated, String prefix) {
        this.generated = generated;
        this.prefix = prefix;
        this.checks = new SchemaEmitter(generated, prefix);
    }

    static boolean supports(SchemaCheck check, SchemaValue value) {
        if (check == null || check.hasFallback()) return false;
        switch (check.kind) {
            case PASS:
            case REJECT:
            case TYPE:
            case LITERAL:
                return true;
            case ALL:
                for (SchemaCheck child : ((SchemaCheck.AllCheck) check).checks) {
                    if (!supports(child, value)) return false;
                }
                return true;
            case OBJECT:
                SchemaCheck.ObjectCheck object = (SchemaCheck.ObjectCheck) check;
                if (object.dynamic || value.shape != SchemaValue.Shape.OTHER) return false;
                for (SchemaCheck.PropertyCheck property : object.properties) {
                    if (!supports(property.check, property.value)) return false;
                }
                return true;
            case STRING:
                return value.shape == SchemaValue.Shape.STRING
                        && value.type != null
                        && "java.lang.String".equals(value.type.toString());
            case NUMBER:
                return value.shape == SchemaValue.Shape.NUMBER;
            case ARRAY:
                SchemaCheck.ArrayCheck array = (SchemaCheck.ArrayCheck) check;
                return value.isArrayLike() && (array.items == null
                        || supports(array.items, array.itemValue));
            default:
                return false;
        }
    }

    List<String> emitRoot(SchemaCheck check, SchemaValue value, String variable,
                          SchemaFallbackEmitter.Plan plan, String strictFormat) {
        List<String> lines = new ArrayList<String>();
        lines.add("java.util.List<org.sjf4j.schema.ValidationResult.Message> _errors = new java.util.ArrayList<>();");
        emit(lines, check, value, variable, "org.sjf4j.path.PathSegment.Root.INSTANCE",
                plan, strictFormat);
        lines.add("return _errors.isEmpty() ? org.sjf4j.schema.ValidationResult.SUCCESS"
                + " : new org.sjf4j.schema.ValidationResult(false, _errors);");
        return lines;
    }

    private void emit(List<String> out, SchemaCheck check, SchemaValue value,
                      String expression, String instancePath,
                      SchemaFallbackEmitter.Plan plan, String strictFormat) {
        switch (check.kind) {
            case PASS:
                return;
            case ALL:
                for (SchemaCheck child : ((SchemaCheck.AllCheck) check).checks) {
                    emit(out, child, value, expression, instancePath, plan, strictFormat);
                }
                return;
            case REJECT:
                assertion(out, check, value, expression, instancePath, plan,
                        strictFormat, check.diagnostic, "schema 'false' always fails");
                return;
            case TYPE:
            case LITERAL:
                assertion(out, check, value, expression, instancePath, plan,
                        strictFormat, check.diagnostic, "value violates " + check.diagnostic.keyword);
                return;
            case OBJECT:
                emitObject(out, (SchemaCheck.ObjectCheck) check, value,
                        expression, instancePath, plan, strictFormat);
                return;
            case STRING:
                emitString(out, (SchemaCheck.StringCheck) check, value,
                        expression, instancePath, plan, strictFormat);
                return;
            case NUMBER:
                emitNumber(out, (SchemaCheck.NumberCheck) check, value,
                        expression, instancePath, plan, strictFormat);
                return;
            case ARRAY:
                emitArray(out, (SchemaCheck.ArrayCheck) check, value,
                        expression, instancePath, plan, strictFormat);
                return;
            default:
                throw new IllegalArgumentException("unsupported result check " + check.kind);
        }
    }

    private void emitObject(List<String> out, SchemaCheck.ObjectCheck check,
                            SchemaValue value, String expression, String path,
                            SchemaFallbackEmitter.Plan plan, String strict) {
        // Object-specific keywords do not apply to null unless type:object
        // is explicitly asserted. Guard nested POJOs before member access.
        if (value.nullable) {
            out.add("if (" + expression + " == null) {");
            if (check.typeRequired) error(out, check.typeDiagnostic, path, "expected object");
            out.add("} else {");
        }
        // All fields on a static POJO are present, and ObjectCompiler has
        // already proved additionalProperties:false against the Java shape.
        if (check.minProperties != null && check.staticPropertyCount < check.minProperties) {
            error(out, check.minPropertiesDiagnostic, path, "too few object properties");
        }
        if (check.maxProperties != null && check.staticPropertyCount > check.maxProperties) {
            error(out, check.maxPropertiesDiagnostic, path, "too many object properties");
        }
        int n = 0;
        for (SchemaCheck.PropertyCheck property : check.properties) {
            String local = "_resultProperty" + sequence++;
            String access = expression + "." + property.staticAccess;
            String childPath = "new org.sjf4j.path.PathSegment.Name(" + path + ", "
                    + SchemaEmitter.javaLiteral(property.name) + ")";
            out.add(property.value.localType() + " " + local + " = " + access + ";");
            emit(out, property.check, property.value, local, childPath, plan, strict);
            n++;
        }
        if (value.nullable) out.add("}");
    }

    private void emitString(List<String> out, SchemaCheck.StringCheck check,
                            SchemaValue value, String expression, String path,
                            SchemaFallbackEmitter.Plan plan, String strict) {
        if (value.nullable) {
            out.add("if (" + expression + " == null) {");
            error(out, check.typeDiagnostic, path, "expected string");
            out.add("} else {");
        }
        if (check.minLength != null) {
            SchemaCheck.StringCheck atom = stringAtom(check.minLength, null, null, null, null);
            assertion(out, atom, value, expression, path, plan, strict,
                    check.minLengthDiagnostic, "string shorter than minLength");
        }
        if (check.maxLength != null) {
            SchemaCheck.StringCheck atom = stringAtom(null, check.maxLength, null, null, null);
            assertion(out, atom, value, expression, path, plan, strict,
                    check.maxLengthDiagnostic, "string longer than maxLength");
        }
        if (check.pattern != null) {
            assertion(out, stringAtom(null, null, null, null, check.pattern),
                    value, expression, path, plan, strict,
                    check.patternDiagnostic, "string does not match pattern");
        }
        if (check.format != null) {
            assertion(out, stringAtom(null, null, null, check.format, null),
                    value, expression, path, plan, strict,
                    check.formatDiagnostic, "string does not match format");
        }
        if (check.choices != null) {
            assertion(out, stringAtom(null, null, check.choices, null, null),
                    value, expression, path, plan, strict,
                    check.enumDiagnostic, "value not in enum");
        }
        if (value.nullable) out.add("}");
    }

    private static SchemaCheck.StringCheck stringAtom(Integer min, Integer max,
                                                       List<String> choices,
                                                       String format, String pattern) {
        return new SchemaCheck.StringCheck(min, max, choices, format, pattern,
                null, null, null, null, null, null);
    }

    private void emitNumber(List<String> out, SchemaCheck.NumberCheck check,
                            SchemaValue value, String expression, String path,
                            SchemaFallbackEmitter.Plan plan, String strict) {
        if (value.nullable) {
            out.add("if (" + expression + " == null) {");
            error(out, check.typeDiagnostic, path, "expected number");
            out.add("} else {");
        }
        if (check.integer) {
            assertion(out, new SchemaCheck.NumberCheck(true, Collections.emptyList(),
                            null, null, null),
                    value, expression, path, plan, strict,
                    check.typeDiagnostic, "expected integer");
        }
        for (SchemaCheck.NumericBound bound : check.bounds) {
            assertion(out, new SchemaCheck.NumberCheck(false,
                            Collections.singletonList(bound), null, null, null),
                    value, expression, path, plan, strict,
                    bound.diagnostic, "number violates " + bound.keyword);
        }
        if (check.multipleOf != null) {
            assertion(out, new SchemaCheck.NumberCheck(false, Collections.emptyList(),
                            check.multipleOf, null, null),
                    value, expression, path, plan, strict,
                    check.multipleOfDiagnostic, "number violates multipleOf");
        }
        if (value.nullable) out.add("}");
    }

    private void emitArray(List<String> out, SchemaCheck.ArrayCheck check,
                           SchemaValue value, String expression, String path,
                           SchemaFallbackEmitter.Plan plan, String strict) {
        if (value.nullable) {
            out.add("if (" + expression + " == null) {");
            error(out, check.typeDiagnostic, path, "expected array");
            out.add("} else {");
        }
        if (check.minItems != null) {
            assertion(out, new SchemaCheck.ArrayCheck(check.minItems, null, null, null,
                            null, null, null),
                    value, expression, path, plan, strict, check.minItemsDiagnostic,
                    "array shorter than minItems");
        }
        if (check.maxItems != null) {
            assertion(out, new SchemaCheck.ArrayCheck(null, check.maxItems, null, null,
                            null, null, null),
                    value, expression, path, plan, strict, check.maxItemsDiagnostic,
                    "array longer than maxItems");
        }
        if (check.items != null) {
            String index = "_resultIndex" + sequence++;
            String item = "_resultItem" + sequence++;
            String itemType = check.itemValue.type == null
                    ? "Object" : check.itemValue.type.toString();
            out.add("int " + index + " = 0;");
            out.add("for (" + itemType + " " + item + " : " + expression + ") {");
            emit(out, check.items, check.itemValue, item,
                    "new org.sjf4j.path.PathSegment.Index(" + path + ", " + index + ")",
                    plan, strict);
            out.add(index + "++;");
            out.add("}");
        }
        if (value.nullable) out.add("}");
    }

    private void assertion(List<String> out, SchemaCheck check, SchemaValue value,
                           String expression, String path, SchemaFallbackEmitter.Plan plan,
                           String strict, SchemaCheck.Diagnostic diagnostic, String message) {
        String onFailure = errorStatement(diagnostic, path, message);
        List<String> source = checks.emitRoot(check, value, expression, plan, strict, onFailure);
        out.addAll(source);
    }

    private void error(List<String> out, SchemaCheck.Diagnostic diagnostic,
                       String path, String message) {
        out.add(errorStatement(diagnostic, path, message));
    }

    private String errorStatement(SchemaCheck.Diagnostic diagnostic,
                                  String path, String message) {
        if (diagnostic == null) {
            throw new IllegalArgumentException("missing diagnostic for compiled result assertion");
        }
        String pointer = SchemaEmitter.javaLiteral(diagnostic.keywordPointer);
        String segments = "org.sjf4j.path.JsonPointer.parse(" + pointer + ").segments()";
        // Parse keyword paths on failure only, not on the validation hot path.
        // The pointer always has a root segment.
        int last = diagnostic.keywordPointer.isEmpty() ? 0
                : diagnostic.keywordPointer.split("/", -1).length - 1;
        return "_errors.add(new org.sjf4j.schema.ValidationResult.Message("
                + SchemaEmitter.javaLiteral(diagnostic.keyword) + ", "
                + SchemaEmitter.javaLiteral(message) + ", null, "
                + path + ", " + segments + "[" + last + "], "
                + "java.net.URI.create(" + SchemaEmitter.javaLiteral(diagnostic.schemaUri) + ")));";
    }
}
