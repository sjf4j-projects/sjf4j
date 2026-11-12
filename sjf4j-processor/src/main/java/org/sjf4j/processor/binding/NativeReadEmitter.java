package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;

/**
 * Emits backend-native container cursor operations at compile time.
 *
 * <p>This is a code generation contract, not a runtime parser abstraction.
 * Generated code calls the concrete backend APIs directly.</p>
 */
interface NativeReadEmitter {

    void emitObjectStart(JavaWriter out, boolean insideObject);

    void emitArrayStart(JavaWriter out);

    void emitArrayLoopStart(JavaWriter out, BindingValue element);

    String arrayElementReadExpression(BindingValue element, String normalRead);
}
