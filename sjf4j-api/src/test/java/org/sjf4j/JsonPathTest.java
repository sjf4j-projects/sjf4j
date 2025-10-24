package org.sjf4j;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class JsonPathTest {

    @Test
    public void testParse1() {
        String s1 = "$.a.b[0].c";
        JsonPath path1 = JsonPath.parse(s1);
        log.info("path1: {}", path1);
        assertEquals("$.a.b[0].c", path1.toString());

        String s2 = "$.a[*].b['c'].d";
        JsonPath path2 = JsonPath.parse(s2);
        log.info("path2: {}", path2);
        assertEquals("$.a[*].b.c.d", path2.toString());

        String s3 = "$['x.y'].a['[['][-1].*[*]";
        JsonPath path3 = JsonPath.parse(s3);
        log.info("path3: {}", path3);
        assertEquals("$['x.y'].a['[['][-1].*[*]", path3.toString());

        String s4 = "$.a[''].b['\\''].c";
        JsonPath path4 = JsonPath.parse(s4);
        log.info("path4: {}", path4);
        assertEquals("$.a[''].b['\\''].c", path4.toString());
    }



}
