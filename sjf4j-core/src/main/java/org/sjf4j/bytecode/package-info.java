/**
 * Optional runtime-compiled JSON-path accessors.
 *
 * <p>{@link BytecodePath#compile(String, Class, Class)} uses a compiler supplied
 * by an optional runtime module such as {@code sjf4j-asm}. Core also provides
 * {@link FallbackBytecodePath} for reflective path access. For compile-time
 * generated accessors, see {@link org.sjf4j.annotation.path.CompiledNavigator}.</p>
 */
package org.sjf4j.bytecode;
