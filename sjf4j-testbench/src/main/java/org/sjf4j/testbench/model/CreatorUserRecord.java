package org.sjf4j.testbench.model;

import java.net.URI;
import java.util.List;

/**
 * Immutable counterpart to the full-size User benchmark fixture. JSON member
 * names and value types match User so CreatorReadBenchmark can reuse the
 * existing CompiledReadBenchmark payload unchanged.
 *
 * The record (including nested records) is also shared by testbench tests.
 *
 * @param id user identifier
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 * @param reputation user reputation
 * @param loginCount number of logins
 * @param age user age
 * @param active whether the user is active
 * @param verified whether the user is verified
 * @param admin whether the user is an administrator
 * @param suspended whether the user is suspended
 * @param score user score
 * @param latitude latitude
 * @param longitude longitude
 * @param username username
 * @param email email address
 * @param displayName display name
 * @param passwordHash password hash
 * @param bio biography
 * @param website website URI
 * @param department department
 * @param address postal address
 * @param tags user tags
 * @param friends user's friends
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

    /**
     * Address fixture used by the creator benchmark.
     *
     * @param street street address
     * @param city city
     * @param state state
     * @param zip postal code
     * @param country country
     */
    public record AddressRecord(String street, String city, String state,
                                String zip, String country) {}

    /**
     * Friend fixture used by the creator benchmark.
     *
     * @param id friend identifier
     * @param name friend name
     * @param since friendship start timestamp
     * @param close whether the friend is close
     */
    public record FriendRecord(long id, String name, long since, boolean close) {}
}
