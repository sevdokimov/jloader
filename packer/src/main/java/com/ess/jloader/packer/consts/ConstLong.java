package com.ess.jloader.packer.consts;

import org.objectweb.asm.ClassWriter;

/**
 * @author Sergey Evdokimov
 */
public class ConstLong extends ConstConst<Long> {

    public static final int TAG = 5;

    public ConstLong(long value) {
        super(TAG, value);
    }

    @Override
    public void toWriter(ClassWriter cw) {
        cw.newConst(getValue());
    }


}
