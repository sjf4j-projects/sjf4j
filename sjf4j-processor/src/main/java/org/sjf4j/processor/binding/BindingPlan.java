package org.sjf4j.processor.binding;

import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.type.TypeMirror;
import java.util.Objects;

/**
 * An analyzed @CompiledBinder operation before recursive value compilation.
 */
final class BindingPlan {

    enum Direction {
        READ_FROM,
        WRITE_TO
    }

    private final ResolvedMethod method;
    private final Direction direction;
    private final TypeMirror valueType;
    private final int readerWriterParameter;
    private final int valueParameter;

    private BindingPlan(
            ResolvedMethod method,
            Direction direction,
            TypeMirror valueType,
            int readerWriterParameter,
            int valueParameter) {

        this.method = Objects.requireNonNull(method, "method");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        this.readerWriterParameter = readerWriterParameter;
        this.valueParameter = valueParameter;
    }

    static BindingPlan readFrom(
            ResolvedMethod method,
            TypeMirror valueType,
            int readerParameter) {

        return new BindingPlan(
                method,
                Direction.READ_FROM,
                valueType,
                readerParameter,
                -1);
    }

    static BindingPlan writeTo(
            ResolvedMethod method,
            TypeMirror valueType,
            int valueParameter,
            int writerParameter) {

        return new BindingPlan(
                method,
                Direction.WRITE_TO,
                valueType,
                writerParameter,
                valueParameter);
    }

    ResolvedMethod method() {
        return method;
    }

    Direction direction() {
        return direction;
    }

    TypeMirror valueType() {
        return valueType;
    }

    int readerWriterParameter() {
        return readerWriterParameter;
    }

    int valueParameter() {
        return valueParameter;
    }
}
