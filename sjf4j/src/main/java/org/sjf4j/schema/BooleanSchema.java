package org.sjf4j.schema;


final class BooleanSchema implements JsonSchema {

    public static final JsonSchema TRUE = new BooleanSchema(true);
    public static final JsonSchema FALSE = new BooleanSchema(false);


    private final boolean booleanValue;
    private BooleanSchema(boolean booleanValue) {
        this.booleanValue = booleanValue;
    }

    @Override
    public void compile(SchemaStore outer) {}

    @Override
    public ValidationResult validate(Object node, ValidationOptions options) {
        return new ValidationResult(booleanValue, null);
    }

}
