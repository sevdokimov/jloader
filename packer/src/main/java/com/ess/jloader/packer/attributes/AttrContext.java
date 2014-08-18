package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
import com.ess.jloader.utils.Key;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class AttrContext {

    private final Map<Key, Object> properties = new HashMap<Key, Object>();

    private final ClassDescriptor classDescriptor;

    public AttrContext(ClassDescriptor classDescriptor) {
        this.classDescriptor = classDescriptor;
    }

    public <T> T putProperty(Key<T> key, T value) {
        return (T) properties.put(key, value);
    }

    public <T> T getProperty(Key<T> key) {
        return (T) properties.get(key);
    }

    public ClassDescriptor getClassDescriptor() {
        return classDescriptor;
    }
}
