package com.ess.jloader.packer.consts;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey Evdokimov
 */
public class ConstMethodTypeInfo extends AbstractConst {

    public static final int TAG = 16;

    private final String methodDescr;

    public ConstMethodTypeInfo(@NotNull String methodDescr) {
        super(TAG);
        this.methodDescr = methodDescr;
    }

    public String getMethodDescr() {
        return methodDescr;
    }

    @Override
    public void toWriter(ClassWriter cw) {
        cw.newMethodType(methodDescr);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConstMethodTypeInfo that = (ConstMethodTypeInfo) o;

        if (!methodDescr.equals(that.methodDescr)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return TAG * 31 + methodDescr.hashCode();
    }
}
