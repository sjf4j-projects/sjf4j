package org.sjf4j.testbench.binding.fabien;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sjf4j.CompiledInstances;
import org.sjf4j.RuntimeContext;
import org.sjf4j.testbench.model.fabien.Clients;
import org.sjf4j.testbench.model.fabien.Users;

import java.security.MessageDigest;
import java.util.Iterator;

/**
 * Semantic regression probe for baseline/staticFlag Jackson3 generated helper.
 * Not part of JMH measurements. Tests object null omission vs array null
 * preservation, and prints SHA-256 so different JMH Jars can be compared.
 */
public final class Jackson3StringMemberNullProbe {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Jackson3StringMemberNullProbe() {}

    public static void main(String[] args) throws Exception {
        for (boolean includeNulls : new boolean[]{true, false}) {
            RuntimeContext ctx = new RuntimeContext(includeNulls);
            FabienBindings.Jackson3 compiled =
                    CompiledInstances.create(FabienBindings.Jackson3.class, ctx);

            Users users = Users.sample();
            byte[] userBytes = compiled.writeUsers(users);
            JsonNode userTree = MAPPER.readTree(userBytes);
            JsonNode first = userTree.path("users").path(0);
            if (includeNulls) {
                if (!first.has("favoriteFruit") || !first.get("favoriteFruit").isNull())
                    throw new IllegalStateException("includeNulls=true lost null object field");
            } else {
                if (first.has("favoriteFruit"))
                    throw new IllegalStateException("includeNulls=false retained null object field");
                assertNoNullObjectFields(userTree);
            }
            // Null-valued array elements must remain, independent of includeNulls.
            if (!first.path("tags").path(2).isNull())
                throw new IllegalStateException("null array member was omitted");
            print("users", includeNulls, userBytes);

            Clients clients = Clients.sample();
            byte[] clientBytes = compiled.writeClients(clients);
            JsonNode clientTree = MAPPER.readTree(clientBytes);
            if (!includeNulls) assertNoNullObjectFields(clientTree);
            print("clients", includeNulls, clientBytes);
        }
    }

    private static void assertNoNullObjectFields(JsonNode node) {
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                JsonNode child = node.get(name);
                if (child.isNull())
                    throw new IllegalStateException("unexpected null object property: " + name);
                assertNoNullObjectFields(child);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) if (!child.isNull()) assertNoNullObjectFields(child);
        }
    }

    private static void print(String model, boolean includeNulls, byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hash = new StringBuilder(digest.length * 2);
        for (byte b : digest) hash.append(String.format("%02x", b & 0xff));
        System.out.println("NULL_PROBE model=" + model + " includeNulls=" + includeNulls
                + " bytes=" + bytes.length + " sha256=" + hash);
    }
}
