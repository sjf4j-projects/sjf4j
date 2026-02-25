package org.sjf4j.schema;


import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.path.PathSegment;

import java.util.Collections;

/**
 * Boolean schema representation (true/false).
 */
@NodeValue
public final class BooleanSchema implements JsonSchema {

    public static final BooleanSchema TRUE = new BooleanSchema(true);
    public static final BooleanSchema FALSE = new BooleanSchema(false);

    private final boolean booleanValue;
    private BooleanSchema(boolean booleanValue) {
        this.booleanValue = booleanValue;
    }

    /**
     * Boolean schema has no compile step.
     */
    @Override
    public void compile(SchemaStore outer) {}

    /**
     * Encodes BooleanSchema to raw boolean.
     */
    @ValueToRaw
    public boolean value2Raw() {
        return booleanValue;
    }

    /**
     * Decodes raw boolean to shared BooleanSchema instance.
     */
    @RawToValue
    public static BooleanSchema raw2Value(boolean booleanValue) {
        return booleanValue ? TRUE : FALSE;
    }


    // validate
    /**
     * Validates using boolean-schema semantics.
     */
    @Override
    public ValidationResult validate(Object node, ValidationOptions options) {
        if (booleanValue) {
            return ValidationResult.VALID;
        } else {
            ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.ERROR,
                    PathSegment.Root.INSTANCE, null, "Schema 'false' always fails");
            return new ValidationResult(false, null, msg);
        }
    }

    // evaluate
    /**
     * Evaluates using boolean-schema semantics.
     */
    @Override
    public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
        if (booleanValue) {
            return true;
        } else {
            ctx.addError(ps, "false", "Schema 'false' always fails");
            return false;
        }
    }

}
