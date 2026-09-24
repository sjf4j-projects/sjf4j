package org.sjf4j.processor.binding;

import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.type.TypeMirror;
import java.util.Objects;

/** An analyzed @CompiledBinder operation before recursive value compilation. */
final class BindingPlan {

    enum Direction {
        READ_FROM,
        WRITE_TO
    }

    enum ReadInput {
        STRING,
        BYTES,
        INPUT_STREAM,
        READER
    }

    enum WriteOutput {
        STRING,
        BYTES,
        OUTPUT_STREAM,
        WRITER
    }

    private final ResolvedMethod method;
    private final Direction direction;
    private final TypeMirror valueType;
    private final ReadInput readInput;
    private final WriteOutput writeOutput;

    private BindingPlan(
            ResolvedMethod method,
            Direction direction,
            TypeMirror valueType,
            ReadInput readInput,
            WriteOutput writeOutput) {

        this.method = Objects.requireNonNull(method, "method");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        this.readInput = readInput;
        this.writeOutput = writeOutput;
    }

    static BindingPlan readFrom(
            ResolvedMethod method,
            TypeMirror valueType,
            ReadInput input) {

        return new BindingPlan(
                method,
                Direction.READ_FROM,
                valueType,
                Objects.requireNonNull(input, "input"),
                null);
    }

    static BindingPlan writeTo(
            ResolvedMethod method,
            TypeMirror valueType,
            WriteOutput output) {

        return new BindingPlan(
                method,
                Direction.WRITE_TO,
                valueType,
                null,
                Objects.requireNonNull(output, "output"));
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

    ReadInput readInput() {
        return readInput;
    }

    WriteOutput writeOutput() {
        return writeOutput;
    }
}
