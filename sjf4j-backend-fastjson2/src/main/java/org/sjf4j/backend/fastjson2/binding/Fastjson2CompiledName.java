package org.sjf4j.backend.fastjson2.binding;

import org.sjf4j.binding.CompiledName;

/** Pre-escaped Fastjson2 object property name for its raw-name writer path. */
final class Fastjson2CompiledName extends CompiledName {

    final char[] rawName;

    Fastjson2CompiledName(String name) {
        super(name);
        int length = name.length();
        rawName = new char[length + 3];
        rawName[0] = '"';
        name.getChars(0, length, rawName, 1);
        rawName[length + 1] = '"';
        rawName[length + 2] = ':';
    }
}
