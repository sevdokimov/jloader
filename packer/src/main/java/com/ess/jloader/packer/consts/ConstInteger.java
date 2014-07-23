package com.ess.jloader.packer.consts;

import org.objectweb.asm.ClassWriter;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey Evdokimov
 */
public class ConstInteger extends ConstConst<Integer> {

    public static final int TAG = 3;

    public ConstInteger(int value) {
        super(TAG, value);
    }
}
