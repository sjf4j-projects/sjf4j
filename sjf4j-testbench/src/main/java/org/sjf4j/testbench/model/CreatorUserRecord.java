package org.sjf4j.testbench.model;

import java.net.URI;
import java.util.List;

/**
 * Immutable counterpart to the full-size User benchmark fixture. JSON field
 * names and value types match User so CreatorReadBenchmark can reuse the
 * existing CompiledReadBenchmark payload unchanged.
 *
 * The record (including nested records) is also shared by testbench tests.
 */
public record CreatorUserRecord(
        long id,
        long createdAt,
        long updatedAt,
        long reputation,
        int loginCount,
        int age,
        boolean active,
        boolean verified,
        boolean admin,
        boolean suspended,
        double score,
        double latitude,
        double longitude,
        String username,
        String email,
        String displayName,
        String passwordHash,
        String bio,
        URI website,
        String department,
        AddressRecord address,
        List<String> tags,
        List<FriendRecord> friends) {

    /** Address fixture used by the creator benchmark. */
    public record AddressRecord(String street, String city, String state,
                                String zip, String country) {}

    /** Friend fixture used by the creator benchmark. */
    public record FriendRecord(long id, String name, long since, boolean close) {}
}
