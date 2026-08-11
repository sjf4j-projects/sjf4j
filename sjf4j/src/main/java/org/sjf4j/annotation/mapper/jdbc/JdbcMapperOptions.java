package org.sjf4j.annotation.mapper.jdbc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures one {@link CompiledJdbcMapper} method.
 *
 * <p>Options are source-retained because they affect only generation of the
 * method currently being compiled.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface JdbcMapperOptions {
    /** Result-cardinality behavior for a single-target JDBC mapper method. */
    SingleResultPolicy singleResult() default SingleResultPolicy.FAIL_ON_MULTIPLE;

    /** Column requirement policy for JDBC POJO methods. */
    ColumnProjectionPolicy columnProjection() default ColumnProjectionPolicy.REQUIRE_ALL;

    /** Duplicate-column policy for {@code Map<String,Object>} JDBC results. */
    DuplicateColumnPolicy duplicateColumn() default DuplicateColumnPolicy.FAIL;
}
