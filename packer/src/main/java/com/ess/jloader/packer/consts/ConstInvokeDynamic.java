package com.ess.jloader.packer.consts;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Sergey Evdokimov
 */
public class ConstInvokeDynamic extends AbstractConst {

    public static final int TAG = 18;

    private final String name;
    private final String descr;

    public ConstInvokeDynamic(@NotNull String name, @NotNull String descr) {
        super(TAG);
        throw new UnsupportedOperationException();
    }

    public String getName() {
        return name;
    }

    public String getDescr() {
        return descr;
    }

    @Override
    public void toWriter(ClassWriter cw) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConstInvokeDynamic that = (ConstInvokeDynamic) o;

        if (!descr.equals(that.descr)) return false;
        if (!name.equals(that.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + descr.hashCode();
        return result;
    }
}
