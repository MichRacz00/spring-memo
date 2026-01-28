package com.example.memo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum Type {
    STICKY_NOTE("StickyNote"),
    BIG_SHEET("BigSheet");

    private final String displayName;
    private static final Map<String, Type> MAP;

    static {
        MAP = Arrays.stream(values())
                .collect(Collectors.toMap(t -> t.displayName.toLowerCase(), t -> t));
    }

    Type(String displayName) { this.displayName = displayName; }

    @JsonCreator
    public static Type fromString(String name) {
        if (name == null) return null;
        Type t = MAP.get(name.trim().toLowerCase());
        if (t == null) throw new IllegalArgumentException(
                "Unknown type: " + name + ". Allowed: " + MAP.keySet());
        return t;
    }

    @Override
    @JsonValue
    public String toString() { return displayName; }
}
