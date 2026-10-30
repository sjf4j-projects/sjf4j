package org.sjf4j.testbench.model;

import lombok.Data;

import java.net.URI;
import java.util.List;

@Data
public class User {
    // Shared input fixture for binding benchmarks (POJO and untyped Map).
    public static final String JSON_DATA = "{\n" +
            "  \"id\": 839201, \"createdAt\": 1700000000123, \"updatedAt\": 1701234567890,\n" +
            "  \"reputation\": 9876543210, \"loginCount\": 421, \"age\": 34,\n" +
            "  \"active\": true, \"verified\": true, \"admin\": false, \"suspended\": false,\n" +
            "  \"score\": 98.75, \"latitude\": 37.7749, \"longitude\": -122.4194,\n" +
            "  \"username\": \"alice.builder\", \"email\": null,\n" +
            "  \"displayName\": \"Alice \\\"the Builder\\\" n Line\", \"passwordHash\": \"$2a$12$abcdef\",\n" +
            "  \"bio\": \"Builds fast JSON with control chars\", \"website\": \"https://example.test/a?b=1\",\n" +
            "  \"department\": \"Platform Engineering\",\n" +
            "  \"address\": {\"street\": \"1 Main St\", \"city\": \"San Francisco\", \"state\": \"CA\", \"zip\": \"94105\", \"country\": \"US\"},\n" +
            "  \"tags\": [\"tag-0-value\", null],\n" +
            "  \"friends\": [\n" +
            "    {\"id\": 1000, \"name\": \"BillBackslash\", \"since\": 1600000000000, \"close\": true},\n" +
            "    null,\n" +
            "    {\"id\": 1001, \"name\": \"Cindy Line\", \"since\": 1600000000001, \"close\": false}\n" +
            "  ]\n" +
            "}\n";

    private long id;
    private long createdAt;
    private long updatedAt;
    private long reputation;
    private int loginCount;
    private int age;
    private boolean active;
    private boolean verified;
    private boolean admin;
    private boolean suspended;
    private double score;
    private double latitude;
    private double longitude;
    private String username;
    private String email;
    private String displayName;
    private String passwordHash;
    private String bio;
    private URI website;
    private String department;
    private Address address;
    private List<String> tags;
    private List<Friend> friends;

}
