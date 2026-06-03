package org.sjf4j.jdk17.processor.mapper.comparison.dto;

public class ProductDTO {
    
	private String name;

    public ProductDTO() {

    }

    public ProductDTO(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}