/**
 * Code generators and generation-time models for {@code @CompiledMapper}
 * interfaces.
 *
 * <p>The mapper generator precomputes source reads, target construction plans,
 * conversions, path writes, and nested mapper calls while the compiler has full
 * type information.  The emitted implementation is intentionally straightforward
 * Java assignment/conversion code so mapper calls do not pay reflection or
 * annotation-scanning costs at runtime.</p>
 *
 * <p>Model classes in this package describe generation decisions only; they are
 * not runtime API.  Keeping them package-private where possible helps preserve a
 * small public surface for the processor module.</p>
 */
package org.sjf4j.processor.mapper;
