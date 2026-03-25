/**
 * Core public API for SJF4J.
 *
 * <p>SJF4J operates on a single structural model: OBNT (Object-Based Node Tree).
 * In OBNT, JSON-like data is represented directly as native Java objects instead
 * of a dedicated AST.
 *
 * <p>Main object forms in OBNT:
 * <ul>
 *     <li>{@link org.sjf4j.JsonObject} and {@link java.util.Map} for object nodes</li>
 *     <li>{@link org.sjf4j.JsonArray} and {@link java.util.List} for array nodes</li>
 *     <li>{@link java.lang.String}, {@link java.lang.Number}, {@link java.lang.Boolean}, and {@code null} for value nodes</li>
 * </ul>
 *
 * <p>Common modeling terms used by the public API:
 * <ul>
 *     <li>POJO: a regular Java object bound by declared fields, getters/setters, or creators</li>
 *     <li>JOJO: a {@link org.sjf4j.JsonObject} subtype that combines declared Java fields with dynamic JSON properties</li>
 *     <li>JAJO: a {@link org.sjf4j.JsonArray} subtype that combines a typed Java class with JSON-array behavior</li>
 * </ul>
 *
 * <p>{@link org.sjf4j.Sjf4j} is the main entry point for parsing and writing,
 * while {@link org.sjf4j.node.Nodes} provides structural conversion helpers on
 * top of the same OBNT model.
 */
package org.sjf4j;
