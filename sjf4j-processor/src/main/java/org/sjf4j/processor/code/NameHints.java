package org.sjf4j.processor.code;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;

/** Semantic identifier hints; uniqueness and Java legality belong to NameAllocator. */
public final class NameHints {

    private NameHints() {}

    /** Erased simple name, preserving array dimensions. */
    public static String simpleType(TypeMirror type) {
        switch (type.getKind()) {
            case ARRAY:
                return simpleType(((ArrayType) type).getComponentType()) + "Array";
            case DECLARED:
            case ERROR:
                return ((TypeElement) ((DeclaredType) type).asElement()).getSimpleName().toString();
            default:
                return capitalize(type.toString());
        }
    }

    /** Structural helper hint, including generic arguments in declaration order. */
    public static String type(TypeMirror type) {
        switch (type.getKind()) {
            case ARRAY:
                return type(((ArrayType) type).getComponentType()) + "Array";
            case DECLARED:
            case ERROR:
                DeclaredType declared = (DeclaredType) type;
                StringBuilder hint = new StringBuilder();
                for (int i = 0; i < declared.getTypeArguments().size(); i++) {
                    TypeMirror argument = declared.getTypeArguments().get(i);
                    // String keys are implicit for JSON object maps.
                    if (declared.getTypeArguments().size() == 2 && hint.length() == 0
                            && i == 0
                            && "java.lang.String".equals(argument.toString())
                            && "Map".equals(simpleType(type))) continue;
                    hint.append(type(argument));
                }
                return hint.append(simpleType(type)).toString();
            case WILDCARD:
                WildcardType wildcard = (WildcardType) type;
                if (wildcard.getExtendsBound() != null) return "Extends" + type(wildcard.getExtendsBound());
                if (wildcard.getSuperBound() != null) return "Super" + type(wildcard.getSuperBound());
                return "Any";
            default:
                return capitalize(type.toString());
        }
    }

    public static String capitalize(String name) {
        return name.isEmpty() ? "Value" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Converts property and column hints to readable Java local names. */
    public static String lowerCamel(String name) {
        StringBuilder hint = new StringBuilder();
        boolean boundary = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                boundary = hint.length() != 0;
                continue;
            }
            if (hint.length() == 0) hint.append(Character.toLowerCase(c));
            else if (boundary) hint.append(Character.toUpperCase(c));
            else if (Character.isUpperCase(c) && Character.isUpperCase(name.charAt(i - 1))
                    && (i + 1 == name.length() || !Character.isLowerCase(name.charAt(i + 1)))) {
                hint.append(Character.toLowerCase(c));
            } else hint.append(c);
            boundary = false;
        }
        return hint.length() == 0 ? "value" : hint.toString();
    }

    public static String upperSnake(String text) {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                if (name.length() != 0 && name.charAt(name.length() - 1) != '_') name.append('_');
                continue;
            }
            if (Character.isUpperCase(c) && i > 0
                    && name.length() > 0 && name.charAt(name.length() - 1) != '_'
                    && (Character.isLowerCase(text.charAt(i - 1))
                        || Character.isDigit(text.charAt(i - 1)))) {
                name.append('_');
            }
            name.append(Character.toUpperCase(c));
        }
        return name.length() == 0 ? "VALUE" : name.toString();
    }
}
