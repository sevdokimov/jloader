package com.ess.jloader.utils;

import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Evdokimov
 */
public class Key<T> {

    private final String name;

    private Key(@Nullable String name) {
        this.name = name;
    }

    public static <T> Key<T> create(@Nullable String name) {
        return new Key<T>(name);
    }
}
