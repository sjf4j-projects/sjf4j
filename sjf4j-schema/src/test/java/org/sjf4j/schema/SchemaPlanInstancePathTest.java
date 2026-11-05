package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaPlanInstancePathTest {
    private static PathSegment prefix() {
        return new PathSegment.Name(
                new PathSegment.Name(PathSegment.Root.INSTANCE, "user"), "address");
    }

    @Test
    void falseSchemaFailurePreservesEnclosingInstancePath() {
        SchemaPlan plan = BooleanSchema.FALSE.createPlan();
        ValidationException exception = assertThrows(ValidationException.class,
                () -> CompiledSupport.requireValid(plan, Map.of("zip", 123), prefix(), false));
        assertEquals("/user/address",
                exception.getResult().getLastError().getInstancePs().rootedPointerExpr());
    }

    @Test
    void nestedObjectErrorUsesFullInstancePathWithoutRebasing() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson(
                "{\"$id\":\"https://example.test/address.json\","
                + "\"properties\":{\"zip\":{\"type\":\"string\"}}}");
        SchemaPlan plan = schema.createPlan();
        ValidationException exception = assertThrows(ValidationException.class,
                () -> CompiledSupport.requireValid(plan, Map.of("zip", 123), prefix(), false));
        ValidationResult.Detail error = exception.getResult().getLastError();
        assertEquals("/user/address/zip", error.getInstancePs().rootedPointerExpr());
        assertEquals("/properties/zip/type", error.getKeywordPs().rootedPointerExpr());
        assertEquals(URI.create("https://example.test/address.json"), error.getSchemaUri());
    }

    @Test
    void compiledFailureFactoryRetainsSeparateResourceAndKeywordPointer() {
        PathSegment path = new PathSegment.Index(prefix(), 2);
        ValidationException exception = ValidationException.failure(
                "oneOf", "sjf4j:/example", "/properties/items/oneOf", path);
        ValidationResult.Detail error = exception.getResult().getLastError();
        assertEquals("/user/address/2", error.getInstancePs().rootedPointerExpr());
        assertEquals("/properties/items/oneOf", error.getKeywordPs().rootedPointerExpr());
        assertEquals(URI.create("sjf4j:/example"), error.getSchemaUri());
    }
}
