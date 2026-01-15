package org.sjf4j.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.path.JsonPointer;


public class JsonSchema extends JsonObject {

    private String id;
    private String anchor;
    private String dynamicAnchor;

    public JsonSchema() {
        super();
    }

    public JsonSchema(Object node) {
        super(node);
    }


    private transient volatile Evaluator[] evaluators;

    public void compile() {
        if (evaluators == null) {
            evaluators = SchemaUtil.compile(this);
        }
    }

    public boolean validate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
        if (evaluators == null) {
            compile();
        }
        //TODO
        return false;
    }

}
