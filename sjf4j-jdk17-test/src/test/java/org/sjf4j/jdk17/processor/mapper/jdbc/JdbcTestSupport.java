package org.sjf4j.jdk17.processor.mapper.jdbc;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

final class JdbcTestSupport {
    private JdbcTestSupport() {
    }

    static ResultSet result(String[] columns, Object[]... rows) {
        int[] row = {-1};
        Object[] last = {null};
        return (ResultSet) Proxy.newProxyInstance(JdbcTestSupport.class.getClassLoader(),
                new Class[]{ResultSet.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("next")) {
                        return ++row[0] < rows.length;
                    }
                    if (method.getName().equals("getObject") || method.getName().equals("getString")) {
                        int column = column(columns, arguments[0]);
                        return last[0] = rows[row[0]][column - 1];
                    }
                    if (method.getName().equals("getInt")) {
                        int column = column(columns, arguments[0]);
                        last[0] = rows[row[0]][column - 1];
                        return last[0] == null ? 0 : ((Number) last[0]).intValue();
                    }
                    if (method.getName().equals("wasNull")) {
                        return last[0] == null;
                    }
                    if (method.getName().equals("findColumn")) {
                        return List.of(columns).indexOf(arguments[0]) + 1;
                    }
                    if (method.getName().equals("getMetaData")) {
                        return metadata(columns);
                    }
                    return null;
                });
    }

    static ResultSet currentRowResult(String[] columns, int[] nextCalls, Object... values) {
        Object[] last = {null};
        return (ResultSet) Proxy.newProxyInstance(JdbcTestSupport.class.getClassLoader(),
                new Class[]{ResultSet.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("next")) {
                        nextCalls[0]++;
                        throw new AssertionError("current-row mapper must not advance the ResultSet");
                    }
                    if (method.getName().equals("getObject") || method.getName().equals("getString")) {
                        int column = column(columns, arguments[0]);
                        return last[0] = values[column - 1];
                    }
                    if (method.getName().equals("getInt")) {
                        int column = column(columns, arguments[0]);
                        last[0] = values[column - 1];
                        return last[0] == null ? 0 : ((Number) last[0]).intValue();
                    }
                    if (method.getName().equals("wasNull")) return last[0] == null;
                    if (method.getName().equals("getMetaData")) return metadata(columns);
                    return null;
                });
    }

    static ResultSet brokenCurrentRowResult(String brokenMethod) {
        return (ResultSet) Proxy.newProxyInstance(JdbcTestSupport.class.getClassLoader(),
                new Class[]{ResultSet.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("next")) {
                        throw new AssertionError("current-row mapper must not advance the ResultSet");
                    }
                    if (method.getName().equals(brokenMethod)) throw new SQLException("broken");
                    return null;
                });
    }

    static ResultSet indexedResult(String[] columns, int[] findColumns, Object[]... rows) {
        int[] row = {-1};
        return (ResultSet) Proxy.newProxyInstance(JdbcTestSupport.class.getClassLoader(),
                new Class[]{ResultSet.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("next")) {
                        return ++row[0] < rows.length;
                    }
                    if (method.getName().equals("findColumn")) {
                        findColumns[0]++;
                        return List.of(columns).indexOf(arguments[0]) + 1;
                    }
                    if (method.getName().equals("getObject") || method.getName().equals("getString")) {
                        int column = indexedColumn(arguments[0]);
                        return rows[row[0]][column - 1];
                    }
                    if (method.getName().equals("getInt")) {
                        int column = indexedColumn(arguments[0]);
                        return ((Number) rows[row[0]][column - 1]).intValue();
                    }
                    if (method.getName().equals("wasNull")) {
                        return false;
                    }
                    return null;
                });
    }

    static ResultSet cachedMetadataResult(String[] columns, int[] metadataCalls, Object[]... rows) {
        int[] row = {-1};
        return (ResultSet) Proxy.newProxyInstance(JdbcTestSupport.class.getClassLoader(),
                new Class[]{ResultSet.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("next")) {
                        return ++row[0] < rows.length;
                    }
                    if (method.getName().equals("getMetaData")) {
                        metadataCalls[0]++;
                        return metadata(columns);
                    }
                    if (method.getName().equals("findColumn")) {
                        return List.of(columns).indexOf(arguments[0]) + 1;
                    }
                    if (method.getName().equals("getObject")) {
                        int column = indexedColumn(arguments[0]);
                        return rows[row[0]][column - 1];
                    }
                    if (method.getName().equals("getInt")) {
                        int column = indexedColumn(arguments[0]);
                        return ((Number) rows[row[0]][column - 1]).intValue();
                    }
                    if (method.getName().equals("wasNull")) {
                        return false;
                    }
                    return null;
                });
    }

    static ResultSet brokenResult() {
        return broken("next");
    }

    static ResultSet brokenFindColumnResult() {
        return broken("findColumn");
    }

    private static int column(String[] columns, Object argument) {
        return argument instanceof Integer ? (Integer) argument : List.of(columns).indexOf(argument) + 1;
    }

    private static int indexedColumn(Object argument) {
        if (!(argument instanceof Integer)) {
            throw new AssertionError("expected column index");
        }
        return (Integer) argument;
    }

    private static ResultSet broken(String brokenMethod) {
        return (ResultSet) Proxy.newProxyInstance(JdbcTestSupport.class.getClassLoader(),
                new Class[]{ResultSet.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("next") && brokenMethod.equals("next")) {
                        throw new SQLException("broken");
                    }
                    if (method.getName().equals("next")) {
                        return true;
                    }
                    if (method.getName().equals("findColumn") && brokenMethod.equals("findColumn")) {
                        throw new SQLException("broken");
                    }
                    if (method.getName().equals("findColumn")) {
                        return 1;
                    }
                    return null;
                });
    }

    private static ResultSetMetaData metadata(String[] columns) {
        return (ResultSetMetaData) Proxy.newProxyInstance(JdbcTestSupport.class.getClassLoader(),
                new Class[]{ResultSetMetaData.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getColumnCount")) {
                        return columns.length;
                    }
                    if (method.getName().equals("getColumnLabel")) {
                        return columns[(Integer) arguments[0] - 1];
                    }
                    return null;
                });
    }
}
