/**
 * Code generators for {@code @CompiledPath} interfaces.
 *
 * <p>Path annotations are validated during annotation processing, then emitted
 * as ordinary Java methods that navigate objects, maps, lists, arrays, and SJF4J
 * containers directly.  Static path segments are resolved as much as possible at
 * compile time; generated methods therefore avoid reparsing or interpreting the
 * path on each call.</p>
 *
 * <p>Unsupported path features fail with compile-time diagnostics unless an
 * annotation explicitly allows a runtime fallback.  This keeps the default
 * behavior predictable and makes performance trade-offs visible in source.</p>
 */
package org.sjf4j.processor.path;
