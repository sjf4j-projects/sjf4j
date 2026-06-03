package org.sjf4j.jdk17.processor.mapper.comparison;

import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.mapper.Mappings;
import org.sjf4j.jdk17.processor.mapper.comparison.dto.OrderDTO;
import org.sjf4j.jdk17.processor.mapper.comparison.dto.ProductDTO;
import org.sjf4j.jdk17.processor.mapper.comparison.entity.Order;
import org.sjf4j.jdk17.processor.mapper.comparison.entity.Product;

@CompiledMapper
public interface Sjf4jOrderMapper {

    @Mappings({
            @Mapping(target = "customerName", source = "$.customer.name"),
            @Mapping(target = "billingStreetAddress", source = "$.customer.billingAddress.street"),
            @Mapping(target = "billingCity", source = "$.customer.billingAddress.city"),
            @Mapping(target = "shippingStreetAddress", source = "$.customer.shippingAddress.street"),
            @Mapping(target = "shippingCity", source = "$.customer.shippingAddress.city"),
    })
    OrderDTO map(Order source);

    @Mapping(source = "name", target = "name")
    ProductDTO productToProductDTO(Product product);
}