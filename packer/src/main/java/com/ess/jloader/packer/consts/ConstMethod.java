package com.ess.jloader.packer.consts;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;

/**
 * @author Sergey Evdokimov
 */
public class ConstMethod extends ConstAbstractRef {

    public static final int TAG = 10;

    ConstMethod(Resolver resolver, int pos) {
        super(TAG, resolver, pos);
    }

    public ConstMethod(@NotNull String className, @NotNull String name, @NotNull String descr) {
        super(TAG, className, name, descr);
    }

    @Override
    public void toWriter(ClassWriter cw) {
        cw.newMethod(getOwner().getInternalName(), getName(), getDescr(), false);
    }

}
