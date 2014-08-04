package com.ess.jloader.packer;

import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Sergey Evdokimov
 */
public abstract class Attribute {

    private String name;

    public Attribute(@NotNull String name) {
        this.name = name;
    }

    public abstract void writeTo(DataOutputStream out, ClassDescriptor descriptor) throws IOException;

    @NotNull
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }

}
