package com.ess.jloader.packer.consts;

import org.objectweb.asm.ClassWriter;

/**
 * @author Sergey Evdokimov
 */
public class ConstInterface extends ConstAbstractRef {

    public static final int TAG = 11;

    ConstInterface(Resolver resolver, int pos) {
        super(TAG, resolver, pos);
    }

    public ConstInterface(String className, String name, String descr) {
        super(TAG, className, name, descr);
    }

    @Override
    public void toWriter(ClassWriter cw) {
        cw.newMethod(getOwner().getInternalName(), getName(), getDescr(), true);
    }

}
