package com.ess.jloader.packer.consts;

import org.objectweb.asm.ClassWriter;

/**
 * @author Sergey Evdokimov
 */
public class ConstFloat extends ConstConst<Float> {

    public static final int TAG = 4;

    public ConstFloat(float value) {
        super(TAG, value);
    }
}
