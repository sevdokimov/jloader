package com.ess.jloader.packer.consts;

import org.objectweb.asm.ClassWriter;

/**
 * @author Sergey Evdokimov
 */
public class ConstDouble extends ConstConst<Double> {

    public static final int TAG = 6;

    public ConstDouble(double value) {
        super(TAG, value);
    }
}
