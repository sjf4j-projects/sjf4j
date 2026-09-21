/**
 * Annotation-processing infrastructure shared by SJF4J's compiled path and
 * compiled mapper generators.
 *
 * <p>The processor favors small, direct generated Java over runtime reflection
 * or interpreted path/mapping execution.  Shared classes in this package keep
 * compiler services, source-file emission, type lookup, and generated-name
 * allocation in one place so the feature-specific generators can stay focused
 * on validation and code shape.</p>
 *
 * <p>Most types are deliberately lightweight helpers rather than reusable
 * framework abstractions.  They run only at compile time, but they still try to
 * keep generated output deterministic and compact because generated code is part
 * of the user's build artifacts.</p>
 */
package org.sjf4j.processor;
