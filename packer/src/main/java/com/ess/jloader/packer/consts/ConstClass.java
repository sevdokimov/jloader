package com.ess.jloader.packer.consts;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey Evdokimov
 */
public class ConstClass extends ConstConst<Type> {

    public static final int TAG = 7;

    public ConstClass(String className) {
        this(Type.getObjectType(className));
    }

    public ConstClass(Type type) {
        super(TAG, type);
    }

    public String getType() {
        return getValue().getInternalName();
    }

    @Override
    public void toWriter(ClassWriter cw) {
        cw.newClass(getType());
    }

    @Override
    public String toString() {
        return getType();
    }
}
