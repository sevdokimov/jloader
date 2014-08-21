package com.ess.jloader.packer;

import com.ess.jloader.utils.Key;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class PropertiesHolder {

    private final Map<Key, Object> properties = new HashMap<Key, Object>();

    public <T> T putProperty(Key<T> key, T value) {
        return (T) properties.put(key, value);
    }

    public <T> T getProperty(Key<T> key) {
        return (T) properties.get(key);
    }

    public <T> T removeProperty(Key<T> key) {
        return (T) properties.remove(key);
    }

}
