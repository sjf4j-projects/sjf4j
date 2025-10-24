package org.sjf4j;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

public class BaseTest {

    @Test
    public void testBase1() {
        double d = 1.0000;
        System.out.println("1.0000 : " + d);

        // Double -> long (可能丢失精度，但不会异常)
        Double largeDouble = 1.8e19; // 远超过 long 最大值
        System.out.println("Double -> long: " + largeDouble.longValue()); // 9223372036854775807

        // BigDecimal -> long (超出范围时静默截断)
        BigDecimal huge = new BigDecimal("999999999999999999999999");
        System.out.println("BigDecimal -> long: " + huge.longValue()); // 乱码值，但不异常
    }

    @Test
    public void testBase2() {
        long n = Long.parseLong("9999999999999999999999");
        System.out.println("n : " + n);

    }

}
