/**
 * Compile-time JSON path annotations.
 *
 * <p>Annotate an interface with {@link CompiledNavigator}
 * and annotate each abstract method with exactly one path operation annotation
 * to generate small direct accessors and mutators. {@link FindByPath} supports
 * multi-target reads; {@link GetByPath} and write operations require one target.
 * Unsupported path shapes produce compile-time diagnostics.</p>
 */
package org.sjf4j.annotation.path;
