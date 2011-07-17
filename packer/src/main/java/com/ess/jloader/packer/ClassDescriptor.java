package com.ess.jloader.packer;

/**
 * @author Sergey Evdokimov
 */
public class ClassDescriptor {

    private final String name;

//    private final String

    public ClassDescriptor(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
