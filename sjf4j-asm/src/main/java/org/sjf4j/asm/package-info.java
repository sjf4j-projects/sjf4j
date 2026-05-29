/**
 * Optional ASM-backed runtime compiled-path support for SJF4J.
 * <p>
 * This package provides a {@link org.sjf4j.compiled.PathCompiler} implementation
 * that generates {@link org.sjf4j.compiled.BytecodePath} classes at runtime for
 * supported typed {@link org.sjf4j.path.JsonPath} read and write operations.
 * It remains usable for applications that can accept runtime class generation
 * and the ASM dependency.
 * <p>
 * New compiled node/path generation work is expected to focus on the
 * annotation-processing (APT) direction in {@code sjf4j-processor}, which emits
 * source at build time and keeps runtime behavior simpler.
 */
package org.sjf4j.asm;
