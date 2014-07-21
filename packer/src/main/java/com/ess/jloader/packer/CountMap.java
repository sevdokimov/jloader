package com.ess.jloader.packer;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Sergey Evdokimov
 */
public class CountMap<T> {

    private Map<T, AtomicInteger> map;

    public CountMap() {
        this(new HashMap<T, AtomicInteger>());
    }

    public CountMap(Map<T, AtomicInteger> map) {
        this.map = map;
    }

    public int size() {
        return map.size();
    }

    public int incrementAndGet(T key) {
        return incrementAndGet(key, 1);
    }

    public int incrementAndGet(T key, int d) {
        AtomicInteger integer = map.get(key);
        if (integer == null) {
            integer = new AtomicInteger(d);
            map.put(key, integer);
            return d;
        }
        else {
            return integer.addAndGet(d);
        }
    }

    public Set<T> keySet() {
        return map.keySet();
    }

    @Nullable
    public AtomicInteger getCounter(T key) {
        return map.get(key);
    }

    public AtomicInteger putCounter(T key, AtomicInteger counter) {
        return map.put(key, counter);
    }

    public int get(T key) {
        AtomicInteger res = map.get(key);
        if (res == null) {
            return 0;
        }

        return res.get();
    }
}
