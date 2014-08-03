package com.ess.jloader.packer;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Sergey Evdokimov
 */
public abstract class Attribute {

    private String name;

    public Attribute(String name) {
        this.name = name;
    }

    public abstract void writeTo(DataOutputStream out, ClassDescriptor descriptor) throws IOException;

    public String getName() {
        return name;
    }
}
