package org.sjf4j;


import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
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
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 8, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 12, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 2)
@Threads(1)
public class ReflectionBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{ReflectionBenchmark.class.getName()});
    }

    @State(Scope.Thread)
    public static class Holder {
        Person person;

        @Setup(Level.Iteration)
        public void setup() {
            person = new Person();
            person.setName("hahaha");
        }
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
    private final static NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(Person.class);
    private final static MethodHandle ctorMethodHandler = pi.creatorInfo.noArgsCtorHandle;
    private final static Supplier<?> ctorLambda = pi.creatorInfo.noArgsCtorLambda;

    private final static NodeRegistry.FieldInfo fi = NodeRegistry.getFieldInfo(Person.class, "name");
    private final static MethodHandle getterMethodHandler = fi.getter;
    private final static Function<Object, Object> getterLambda = fi.lambdaGetter;
    private final static MethodHandle setterMethodHandler = fi.setter;
    private final static BiConsumer<Object, Object> setterLambda = fi.lambdaSetter;

    private final static Constructor<Person> personCtor;
    private final static Method getterMethod;
    private final static Method setterMethod;

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
    public Object reflection_getter_native(Holder h) {
        return h.person.getName();
    }

    @Benchmark
    public Object reflection_getter_reflect(Holder h) {
        try {
            return getterMethod.invoke(h.person);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public Object reflection_getter_methodHandler(Holder h) {
        try {
            return getterMethodHandler.invoke(h.person);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public Object reflection_getter_lambda(Holder h) {
        return getterLambda.apply(h.person);
    }



    // ----- Setter pathways -----
    @Benchmark
    public Object reflection_setter_native(Holder h) {
        h.person.setName("hahaha");
        return h.person;
    }

    @Benchmark
    public Object reflection_setter_reflect(Holder h) {
        try {
            setterMethod.invoke(h.person, "hahaha");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return h.person;
    }

    @Benchmark
    public Object reflection_setter_methodHandler(Holder h) {
        try {
            setterMethodHandler.invoke(h.person, "hahaha");
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return h.person;
    }

    @Benchmark
    public Object reflection_setter_lambda(Holder h) {
        setterLambda.accept(h.person, "hahaha");
        return h.person;
    }

}
