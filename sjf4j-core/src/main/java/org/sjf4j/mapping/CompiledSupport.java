package org.sjf4j.mapping;

import org.sjf4j.annotation.mapping.jdbc.ColumnNamingPolicy;
import org.sjf4j.exception.BindingException;
import org.sjf4j.util.Strings;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * Runtime support for generated JDBC mapping implementations.
 *
 * <p>Resolves JDBC column labels to mapped property indexes. Prepared row
 * mappers capture the resolved indexes explicitly and retain no JDBC resources.</p>
 */
public final class CompiledSupport {
    private CompiledSupport() {
    }

    public static int[] resolve(ResultSetMetaData metadata, String[] names,
                                ColumnNamingPolicy policy, boolean required) throws SQLException {
        int count = metadata.getColumnCount();
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            labels[i] = metadata.getColumnLabel(i + 1);
        }

        int[] indexes = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            int index = _match(labels, name, 0);
            if (index == 0 && policy != ColumnNamingPolicy.EXACT) {
                index = _match(labels, name, 1);
            }
            if (index == 0 && policy == ColumnNamingPolicy.INSENSITIVE_SNAKE) {
                index = _match(labels, Strings.toSnakeCase(name), 1);
            }
            if (index == 0 && required) {
                throw new BindingException("missing JDBC column for property: " + name);
            }
            indexes[i] = index;
        }
        return indexes;
    }

    private static int _match(String[] labels, String candidate, int mode) {
        int found = 0;
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            if (label == null) continue;
            boolean matches = mode == 0 ? label.equals(candidate) : label.equalsIgnoreCase(candidate);
            if (!matches) continue;
            if (found != 0) {
                throw new BindingException("ambiguous JDBC column label for '" + candidate
                        + "' at positions " + found + " and " + (i + 1));
            }
            found = i + 1;
        }
        return found;
    }


}
