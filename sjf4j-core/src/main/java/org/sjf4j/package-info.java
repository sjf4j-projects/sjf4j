/**
 * Core public API for SJF4J.
 *
 * <p>OBNT (Object-Based Node Tree) is SJF4J's runtime structural model. An OBNT
 * node is a native Java object; OBNT has no separate AST. Each node has one
 * JSON-semantic shape:
 *
 * <ul>
 *     <li>An object node has named members: {@link JsonObject}, {@link java.util.Map},
 *     POJO, JOJO, or a supported facade-native representation.</li>
 *     <li>An array node has elements: {@link JsonArray}, {@link java.util.List},
 *     Java arrays, {@link java.util.Set}, JAJO, or a supported facade-native
 *     representation. Its representation determines whether ordering and indexed
 *     operations are supported.</li>
 *     <li>A value node is not a container: {@link String}, {@link Number},
 *     {@link Boolean}, {@code null}, or a supported facade-native representation.</li>
 * </ul>
 *
 * <p>POJO, JOJO, and JAJO are Java representation categories, not node shapes:
 * <ul>
 *     <li>POJO: a regular Java object bound through discovered properties,
 *     getters/setters, or creators</li>
 *     <li>JOJO: a {@link JsonObject} subtype with discovered Java properties and
 *     dynamic properties</li>
 *     <li>JAJO: a {@link JsonArray} subtype with typed Java properties and array
 *     behavior</li>
 * </ul>
 *
 * <p>{@link JsonType} describes JSON-semantic shape; {@link NodeKind} is the
 * lower-level runtime representation and dispatch classification. A
 * {@link org.sjf4j.annotation.node.NodeValue @NodeValue} type is a logical
 * value node. Its configured value binding may be a
 * {@link org.sjf4j.value.ValueCodec} or annotated conversion methods. Facade
 * writes encode it to raw OBNT and facade reads decode it; schema validation
 * encodes only. The raw representation may have object node, array node, or
 * value node shape. This does not automatically expand a value in every OBNT API: JsonPath, Patch, and
 * {@link Nodes} operate on the supplied representation unless their own API
 * invokes value binding.
 *
 * <p>{@link org.sjf4j.external.ExternalNode ExternalNode} adapters classify external
 * Java representations and define their available direct operations.
 *
 * <p>{@link org.sjf4j.Sjf4j} is the main entry point for parsing and writing.
 * {@link Nodes} provides conversion helpers, {@link NodeStream} provides stream
 * processing with JSONPath helpers, {@link TypeReference} captures generic
 * target types, and {@link CompiledInstances} provides compile-time generated
 * interface instances.
 */
package org.sjf4j;
