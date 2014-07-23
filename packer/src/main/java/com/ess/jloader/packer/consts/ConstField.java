package com.ess.jloader.packer.consts;

import org.objectweb.asm.ClassWriter;

/**
 * @author Sergey Evdokimov
 */
public class ConstField extends ConstAbstractRef {

    public static final int TAG = 9;

    ConstField(Resolver resolver, int pos) {
        super(TAG, resolver, pos);
    }

    public ConstField(String className, String name, String descr) {
        super(TAG, className, name, descr);
    }

    @Override
    public void toWriter(ClassWriter cw) {
        cw.newField(getOwner().getInternalName(), getName(), getDescr());
    }

}
