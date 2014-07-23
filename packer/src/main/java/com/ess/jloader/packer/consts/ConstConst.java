package com.ess.jloader.packer.consts;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;

/**
 * @author Sergey Evdokimov
 */
public class ConstConst<T> extends AbstractConst {

    private T value;

    public ConstConst(int tag, @NotNull T value) {
        super(tag);
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    @Override
    public void toWriter(ClassWriter cw) {
        cw.newConst(value);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        return value.equals(((ConstConst)o).getValue());
    }

    @Override
    public int hashCode() {
        return tag * 31 + value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
