package com.ess.jloader.packer.consts;

import org.objectweb.asm.ClassWriter;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey Evdokimov
 */
public abstract class AbstractConst {

    protected final int tag;

    public AbstractConst(int tag) {
        this.tag = tag;
    }

    public abstract void toWriter(ClassWriter cw);

    public int getTag() {
        return tag;
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();
}
