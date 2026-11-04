package org.sjf4j.processor.schema;

import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.GeneratedNames;
import org.sjf4j.processor.code.NameAllocator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns fallback resource sharing, usage tracking and generated SchemaPlan fields.
 * Compiler checks carry only schema URIs; generated Java names are assigned here.
 */
final class SchemaFallbackEmitter {
    private final List<Plan> plans = new ArrayList<Plan>();
    private final Map<String, Plan> bySnapshot = new LinkedHashMap<String, Plan>();
    private final String fallbackType;
    private final NameAllocator planTypes = new NameAllocator();
    private final String registryDir;
    private final String registryBaseUri;

    SchemaFallbackEmitter(GeneratedNames names, String registryDir, String registryBaseUri) {
        this.registryDir = registryDir.trim();
        this.registryBaseUri = registryBaseUri.trim();
        // Reserve the enclosing class once; nested plan names have their own scope.
        this.fallbackType = names.type("SchemaFallbacks");
    }

    Plan bind(SchemaResourceResolver.Snapshot snapshot, ValidJsonSchema annotation,
              String annotationExpression) {
        // The runtime registry/plan depends on the selected schema resource, not
        // the validating Java type or the reflection expression for its annotation.
        String key = snapshotKey(snapshot);
        Plan plan = bySnapshot.get(key);
        if (plan == null) {
            plan = new Plan(fallbackType, planTypes.newName("SchemaPlan"), snapshot,
                    annotation, annotationExpression);
            bySnapshot.put(key, plan);
            plans.add(plan);
        }
        return plan;
    }

    void emit(GeneratedClass generated) {
        generated.addHelper(out -> {
            out.beginBlock("private static final class " + fallbackType);
            for (Plan plan : plans) {
                if (!plan.used()) continue;
                out.beginBlock("private static final class " + plan.name);
                String registry = "REGISTRY";
                out.line("private static final org.sjf4j.schema.SchemaRegistry " + registry
                        + " = new org.sjf4j.schema.SchemaRegistry();");
                out.line("static final org.sjf4j.schema.SchemaPlan ROOT = _load();");
                for (Subplan sub : plan.subplans.values()) {
                    out.line("static final org.sjf4j.schema.SchemaPlan " + sub.name
                            + " = " + registry + ".resolve(java.net.URI.create("
                            + SchemaEmitter.javaLiteral(sub.uri) + "));");
                }
                out.beginBlock("private static org.sjf4j.schema.SchemaPlan _load()");
                out.beginBlock("try");
                out.line("org.sjf4j.schema.SchemaLoader loader = new org.sjf4j.schema.SchemaLoader.Local(org.sjf4j.schema.SchemaRegistry.DEFAULT_JSON_SCHEMA_DIR);");
                for (int i = 0; i < plan.snapshot.uris.length; i++) {
                    // Registry-backed documents are loaded by indexDir below;
                    // the default SchemaLoader must never fetch their logical HTTP URIs.
                    if (plan.snapshot.fromRegistry[i]) continue;
                    String uri = "java.net.URI.create(" + SchemaEmitter.javaLiteral(plan.snapshot.uris[i]) + ")";
                    if (i == 0 && !plan.annotation.value().isEmpty()) {
                        out.line(registry + ".index(" + uri
                                + ", org.sjf4j.schema.JsonSchema.fromJson("
                                + plan.annotationExpression + ".value()));");
                    } else {
                        out.line(registry + ".index(" + uri + ", loader.load(" + uri + "));");
                    }
                }
                if (!registryDir.isEmpty()) {
                    if (registryBaseUri.isEmpty()) {
                        out.line(registry + ".indexDir(java.net.URI.create("
                                + SchemaEmitter.javaLiteral(registryDir) + "));");
                    } else {
                        out.line(registry + ".indexDir(java.net.URI.create("
                                + SchemaEmitter.javaLiteral(registryDir) + "), java.net.URI.create("
                                + SchemaEmitter.javaLiteral(registryBaseUri) + "));");
                    }
                }
                out.line("return " + registry + ".resolve(java.net.URI.create("
                        + SchemaEmitter.javaLiteral(plan.snapshot.selectedUri) + "));");
                out.endBlock();
                out.beginBlock("catch (Exception e)");
                out.line("throw new ExceptionInInitializerError(e);");
                out.endBlock();
                out.endBlock();
                out.endBlock();
            }
            out.endBlock();
        });
    }

    static final class Plan {
        private final String name;
        private final String fallbackType;
        private final NameAllocator subplanNames = new NameAllocator()
                .reserve("REGISTRY").reserve("ROOT").reserve("_load");
        final SchemaResourceResolver.Snapshot snapshot;
        private final ValidJsonSchema annotation;
        private final String annotationExpression;
        private final Map<String, Subplan> subplans = new LinkedHashMap<String, Subplan>();
        private boolean rootUsed;

        private Plan(String fallbackType, String name, SchemaResourceResolver.Snapshot snapshot,
                     ValidJsonSchema annotation, String annotationExpression) {
            this.fallbackType = fallbackType;
            this.name = name;
            this.snapshot = snapshot;
            this.annotation = annotation;
            this.annotationExpression = annotationExpression;
        }

        String rootReference() {
            return fallbackType + "." + name + ".ROOT";
        }

        void useRoot() {
            rootUsed = true;
        }

        Subplan subplan(String uri, String suggestedName) {
            Subplan sub = subplans.get(uri);
            if (sub == null) {
                sub = new Subplan(this, subplanNames.newName("SUBSCHEMA"), uri);
                subplans.put(uri, sub);
            }
            return sub;
        }

        private boolean used() {
            return rootUsed || !subplans.isEmpty();
        }
    }

    static final class Subplan {
        private final Plan owner;
        private final String name;
        private final String uri;

        private Subplan(Plan owner, String name, String uri) {
            this.owner = owner;
            this.name = name;
            this.uri = uri;
        }

        String reference() {
            return owner.fallbackType + "." + owner.name + "." + name;
        }
    }

    private static String snapshotKey(SchemaResourceResolver.Snapshot snapshot) {
        StringBuilder key = new StringBuilder();
        key.append(snapshot.selectedUri).append('\u0000');
        for (int i = 0; i < snapshot.uris.length; i++) {
            key.append(snapshot.uris[i]).append('\u0000')
                    .append(snapshot.documents[i]).append('\u0000');
        }
        return key.toString();
    }

}
