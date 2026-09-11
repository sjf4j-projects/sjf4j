package org.sjf4j.handwritten;

import lombok.Data;
import lombok.Getter;

@Data
public class Address {
    private String street;
    private String city;
    private String state;
    private String zip;
    private String country;

    public Address() {
    }

    public Address(
            String street,
            String city,
            String state,
            String zip,
            String country) {
        this.street = street;
        this.city = city;
        this.state = state;
        this.zip = zip;
        this.country = country;
    }

}
