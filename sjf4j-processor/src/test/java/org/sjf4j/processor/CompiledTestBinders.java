package org.sjf4j.processor;

import org.sjf4j.CompiledInstances;
import org.sjf4j.NodeConfig;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.backend.snake.binding.SnakeBinder;

/** Exercise concrete backend injection without changing compiled binding policy. */
final class CompiledTestBinders {
    private CompiledTestBinders() {}

    static <T> T create(Class<T> type, String backend, NodeConfig config) {
        Binder binder;
        switch (backend) {
            case "SIMPLE": binder = new SimpleJsonBinder(config); break;
            case "FASTJSON2": binder = new Fastjson2Binder(config); break;
            case "GSON": binder = new GsonBinder(config); break;
            case "JACKSON2": binder = new Jackson2Binder(config); break;
            case "JACKSON3": binder = new Jackson3Binder(config); break;
            case "JSONP": binder = new JsonpBinder(config); break;
            case "SNAKE": binder = new SnakeBinder(config); break;
            default: throw new IllegalArgumentException(backend);
        }
        return CompiledInstances.create(type, binder);
    }
}
