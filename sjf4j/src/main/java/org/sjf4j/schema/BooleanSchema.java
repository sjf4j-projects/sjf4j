package org.sjf4j.schema;


import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.path.PathSegment;

import java.util.Collections;

@NodeValue
public final class BooleanSchema implements JsonSchema {

    public static final BooleanSchema TRUE = new BooleanSchema(true);
    public static final BooleanSchema FALSE = new BooleanSchema(false);

    private final boolean booleanValue;
    private BooleanSchema(boolean booleanValue) {
        this.booleanValue = booleanValue;
    }

    @Override
    public void compile(SchemaStore outer) {}

    @ValueToRaw
    public boolean value2Raw() {
        return booleanValue;
    }

    @RawToValue
    public static BooleanSchema raw2Value(boolean booleanValue) {
        return booleanValue ? TRUE : FALSE;
    }


    // validate
    @Override
    public ValidationResult validate(Object node, ValidationOptions options) {
        if (booleanValue) {
            return new ValidationResult(true, null);
        } else {
            ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.ERROR, null, null,
                    "Schema 'false' always fails");
            return new ValidationResult(false, Collections.singletonList(msg));
        }
    }

    // evaluate
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
