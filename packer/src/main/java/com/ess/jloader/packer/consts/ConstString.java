package com.ess.jloader.packer.consts;

import org.objectweb.asm.ClassWriter;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey Evdokimov
 */
public class ConstString extends ConstConst<String> {

    public static final int TAG = 8;

    public ConstString(String value) {
        super(TAG, value);
    }
}
