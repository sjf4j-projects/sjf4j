package org.sjf4j.processor.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts compiled boolean fail-fast guards to void validation methods.
 *
 * <p>The successful path performs exactly the same native assertions as
 * {@link SchemaEmitter}. On failure, diagnostic materialization is delegated
 * to the lazily initialized SchemaPlan so keyword and instance paths remain
 * identical to runtime validation. Generated boolean branch predicates are
 * not rewritten: only the inlined root statements are adapted.</p>
 */
final class SchemaRequireEmitter {
    private static final String REJECT = "return false;";

    private SchemaRequireEmitter() {
    }

    static List<String> throwOnFailure(List<String> statements, String failure) {
        List<String> rewritten = new ArrayList<String>(statements.size());
        for (String statement : statements) {
            if (statement.endsWith(REJECT)) {
                rewritten.add(statement.substring(0, statement.length() - REJECT.length()) + failure);
            } else {
                rewritten.add(statement);
            }
        }
        return rewritten;
    }
}
