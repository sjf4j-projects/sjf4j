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

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
public class ReflectionBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{ReflectionBenchmark.class.getName()});
    }

    // --------- Sample POJO ------------
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
    private static MethodHandle ctorMethodHandler = pi.creatorInfo.noArgsCtorHandle;
    private static Supplier<?> ctorLambda = pi.creatorInfo.noArgsCtorLambda;

    private static NodeRegistry.FieldInfo fi = NodeRegistry.getFieldInfo(Person.class, "name");
    private static MethodHandle getterMethodHandler = fi.getter;
    private static Function<Object, Object> getterLambda = fi.lambdaGetter;
    private static MethodHandle setterMethodHandler = fi.setter;
    private static BiConsumer<Object, Object> setterLambda = fi.lambdaSetter;

    private static Constructor<Person> personCtor;
    private static Method getterMethod;
    private static Method setterMethod;

    static {
        try {
            personCtor = Person.class.getConstructor();
            getterMethod = Person.class.getMethod("getName");
            setterMethod = Person.class.getMethod("setName", String.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ----- Constructor pathways -----
    @Benchmark
    public Object reflection_ctor_native() {
        return new Person();
    }

    @Benchmark
    public Object reflection_ctor_reflect() {
        try {
            return personCtor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public Object reflection_ctor_methodHandler() {
        try {
            return ctorMethodHandler.invoke();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public Object reflection_ctor_lambda() {
        return ctorLambda.get();
    }


    // ----- Getter pathways -----
    @Benchmark
    public Object reflection_getter_native() {
        Person p = new Person();
        return p.getName();
    }

    @Benchmark
    public Object reflection_getter_reflect() {
        Person p = new Person();
        try {
            return getterMethod.invoke(p);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public Object reflection_getter_methodHandler() {
        Person p = new Person();
        try {
            return getterMethodHandler.invoke(p);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public Object reflection_getter_lambda() {
        Person p = new Person();
        return getterLambda.apply(p);
    }



    // ----- Setter pathways -----
    @Benchmark
    public Object reflection_setter_native() {
        Person p = new Person();
        p.setName("hahaha");
        return p;
    }

    @Benchmark
    public Object reflection_setter_reflect() {
        Person p = new Person();
        try {
            setterMethod.invoke(p, "hahaha");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return p;
    }

    @Benchmark
    public Object reflection_setter_methodHandler() {
        Person p = new Person();
        try {
            setterMethodHandler.invoke(p, "hahaha");
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return p;
    }

    @Benchmark
    public Object reflection_setter_lambda() {
        Person p = new Person();
        setterLambda.accept(p, "hahaha");
        return p;
    }

}
