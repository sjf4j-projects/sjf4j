package org.sjf4j.testbench.binding.fabien;

import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.BindingFormat;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Read;
import org.sjf4j.annotation.binding.Write;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.backend.snake.binding.SnakeBinder;
import org.sjf4j.testbench.model.fabien.Clients;
import org.sjf4j.testbench.model.fabien.Users;

import java.util.Map;

/** Selects one concrete operation per JMH trial, outside the measured path. */
public final class FabienBindings {
    private FabienBindings() {}

    @FunctionalInterface
    public interface Reader {
        Object read(byte[] input);
    }

    @FunctionalInterface
    public interface Writer {
        byte[] write(Object node);
    }

    public static Reader reader(String backend, String implementation, String model, String shape) {
        if ("runtime".equals(implementation)) {
            Binder<?, ?> binder = runtime(backend);
            Class<?> target = "map".equals(shape) ? Map.class
                    : "users".equals(model) ? Users.class : Clients.class;
            return input -> binder.readNode(input, target);
        }
        if (!"compiled".equals(implementation)) {
            throw new IllegalArgumentException("unknown implementation: " + implementation);
        }
        switch (backend) {
            case "jackson2": {
                Jackson2 b = CompiledInstances.create(Jackson2.class);
                if ("map".equals(shape)) return b::readMap;
                return "users".equals(model) ? b::readUsers : b::readClients;
            }
            case "jackson3": {
                Jackson3 b = CompiledInstances.create(Jackson3.class);
                if ("map".equals(shape)) return b::readMap;
                return "users".equals(model) ? b::readUsers : b::readClients;
            }
            case "fastjson2": {
                Fastjson2 b = CompiledInstances.create(Fastjson2.class);
                if ("map".equals(shape)) return b::readMap;
                return "users".equals(model) ? b::readUsers : b::readClients;
            }
            case "gson": {
                Gson b = CompiledInstances.create(Gson.class);
                if ("map".equals(shape)) return b::readMap;
                return "users".equals(model) ? b::readUsers : b::readClients;
            }
            case "jsonp": {
                Jsonp b = CompiledInstances.create(Jsonp.class);
                if ("map".equals(shape)) return b::readMap;
                return "users".equals(model) ? b::readUsers : b::readClients;
            }
            case "simple": {
                Simple b = CompiledInstances.create(Simple.class);
                if ("map".equals(shape)) return b::readMap;
                return "users".equals(model) ? b::readUsers : b::readClients;
            }
            case "snake": {
                Snake b = CompiledInstances.create(Snake.class);
                if ("map".equals(shape)) return b::readMap;
                return "users".equals(model) ? b::readUsers : b::readClients;
            }
            default: throw new IllegalArgumentException("unknown backend: " + backend);
        }
    }

    @SuppressWarnings("unchecked")
    public static Writer writer(String backend, String implementation, String model, String shape) {
        if ("runtime".equals(implementation)) {
            Binder<?, ?> binder = runtime(backend);
            return binder::writeNodeAsBytes;
        }
        if (!"compiled".equals(implementation)) {
            throw new IllegalArgumentException("unknown implementation: " + implementation);
        }
        switch (backend) {
            case "jackson2": {
                Jackson2 b = CompiledInstances.create(Jackson2.class);
                if ("map".equals(shape)) return node -> b.writeMap((Map<String, Object>) node);
                return "users".equals(model)
                        ? node -> b.writeUsers((Users) node)
                        : node -> b.writeClients((Clients) node);
            }
            case "jackson3": {
                Jackson3 b = CompiledInstances.create(Jackson3.class);
                if ("map".equals(shape)) return node -> b.writeMap((Map<String, Object>) node);
                return "users".equals(model)
                        ? node -> b.writeUsers((Users) node)
                        : node -> b.writeClients((Clients) node);
            }
            case "fastjson2": {
                Fastjson2 b = CompiledInstances.create(Fastjson2.class);
                if ("map".equals(shape)) return node -> b.writeMap((Map<String, Object>) node);
                return "users".equals(model)
                        ? node -> b.writeUsers((Users) node)
                        : node -> b.writeClients((Clients) node);
            }
            case "gson": {
                Gson b = CompiledInstances.create(Gson.class);
                if ("map".equals(shape)) return node -> b.writeMap((Map<String, Object>) node);
                return "users".equals(model)
                        ? node -> b.writeUsers((Users) node)
                        : node -> b.writeClients((Clients) node);
            }
            case "jsonp": {
                Jsonp b = CompiledInstances.create(Jsonp.class);
                if ("map".equals(shape)) return node -> b.writeMap((Map<String, Object>) node);
                return "users".equals(model)
                        ? node -> b.writeUsers((Users) node)
                        : node -> b.writeClients((Clients) node);
            }
            case "simple": {
                Simple b = CompiledInstances.create(Simple.class);
                if ("map".equals(shape)) return node -> b.writeMap((Map<String, Object>) node);
                return "users".equals(model)
                        ? node -> b.writeUsers((Users) node)
                        : node -> b.writeClients((Clients) node);
            }
            case "snake": {
                Snake b = CompiledInstances.create(Snake.class);
                if ("map".equals(shape)) return node -> b.writeMap((Map<String, Object>) node);
                return "users".equals(model)
                        ? node -> b.writeUsers((Users) node)
                        : node -> b.writeClients((Clients) node);
            }
            default: throw new IllegalArgumentException("unknown backend: " + backend);
        }
    }

    private static Binder<?, ?> runtime(String backend) {
        switch (backend) {
            case "jackson2": return new Jackson2Binder();
            case "jackson3": return new Jackson3Binder();
            case "fastjson2": return new Fastjson2Binder();
            case "gson": return new GsonBinder();
            case "jsonp": return new JsonpBinder();
            case "simple": return new SimpleJsonBinder();
            case "snake": return new SnakeBinder();
            default: throw new IllegalArgumentException("unknown backend: " + backend);
        }
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2 {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }


    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3 {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }


    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2 {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }


    @CompiledBinder(backend = Backend.GSON)
    public interface Gson {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }


    @CompiledBinder(backend = Backend.JSONP)
    public interface Jsonp {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }


    @CompiledBinder(backend = Backend.SIMPLE)
    public interface Simple {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }


    @CompiledBinder(format = BindingFormat.YAML, backend = Backend.SNAKE)
    public interface Snake {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }

}
