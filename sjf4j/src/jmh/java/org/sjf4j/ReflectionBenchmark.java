package org.sjf4j;


import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.node.NodeRegistry;

import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
public class ReflectionBenchmark {

    // --------- 模拟的 POJO ------------
    public static class Person extends JsonObject {
        public String name;
        public String nick;
        public int age;
        public Info info;
        public List<Baby> babies;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNick() {
            return nick;
        }

        public void setNick(String nick) {
            this.nick = nick;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public Info getInfo() {
            return info;
        }

        public void setInfo(Info info) {
            this.info = info;
        }

        public List<Baby> getBabies() {
            return babies;
        }

        public void setBabies(List<Baby> babies) {
            this.babies = babies;
        }
    }

    public static class Info extends JsonObject {
        public String email;
        public String city;
    }

    public static class Baby extends JsonObject {
        public String name;
        public int age;
    }

    // Cache PojoInfo/FieldInfo once so the benchmark focuses on invocation overhead instead of lookup cost.
    private static NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(Person.class);
    private static NodeRegistry.FieldInfo fi = NodeRegistry.getFieldInfo(Person.class, "name");

    // ----- Constructor pathways -----
    @Benchmark
    public Object reflection_ctor_native() {
        return new Person();
    }

//    @Benchmark
//    public Object reflection_ctor_methodHandler() {
//        return pi.newInstance2();
//    }

    @Benchmark
    public Object reflection_ctor_lambda() {
        return pi.newPojoNoArgs();
    }

    // ----- Getter pathways -----
    @Benchmark
    public Object reflection_getter_native() {
        Person p = new Person();
        return p.getName();
    }

    @Benchmark
    public Object reflection_getter_methodHandler() {
        Person p = new Person();
        return fi.invokeGetter2(p);
    }

    @Benchmark
    public Object reflection_getter_lambda() {
        Person p = new Person();
        return fi.invokeGetter(p);
    }

    // ----- Setter pathways -----
    @Benchmark
    public Object reflection_setter_native() {
        Person p = new Person();
        p.setName("hahaha");
        return p;
    }

    @Benchmark
    public Object reflection_setter_methodHandler() {
        Person p = new Person();
        fi.invokeSetter2(p, "hahaha");
        return p;
    }

    @Benchmark
    public Object reflection_setter_lambda() {
        Person p = new Person();
        fi.invokeSetter(p, "hahaha");
        return p;
    }

}
