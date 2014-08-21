package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.PropertiesHolder;
import com.ess.jloader.packer.ClassDescriptor;

/**
 * @author Sergey Evdokimov
 */
public class AttrContext extends PropertiesHolder {

    private final ClassDescriptor classDescriptor;

    public AttrContext(ClassDescriptor classDescriptor) {
        this.classDescriptor = classDescriptor;
    }

    public ClassDescriptor getClassDescriptor() {
        return classDescriptor;
    }
}
