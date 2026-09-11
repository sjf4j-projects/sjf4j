package org.sjf4j.jdk17;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Users {
    public String requestId;
    public long generatedAt;
    public String source;
    public User[] users;
}

@SerdeableGenerated
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class User {
    public long id;
    public String name;
    public String email;
    public int age;
    public boolean active;
    public double score;
    public double balance;
    public long createdAt;
    public long updatedAt;
    public int loginCount;
    public int rank;
    public boolean verified;
    public String department;
    public String title;
    public String phone;
    public String website;
    public String locale;
    public String timeZone;
    public String status;
    public String note;
    public Address address;
    public String[] tags;
    public Friend[] friends;
}

@SerdeableGenerated
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Address {
    public String street;
    public String city;
    public String state;
    public String postalCode;
    public String country;
    public double latitude;
    public double longitude;
}

@SerdeableGenerated
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Friend {
    public long id;
    public String name;
    public String email;
    public int age;
    public boolean active;
    public double score;
    public long since;
}
