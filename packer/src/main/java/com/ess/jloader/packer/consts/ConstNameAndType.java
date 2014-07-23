package com.ess.jloader.packer.consts;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Sergey Evdokimov
 */
public class ConstNameAndType extends AbstractConst {

    public static final int TAG = 12;

    private final String name;
    private final String descr;

    public ConstNameAndType(@NotNull String name, @NotNull String descr) {
        super(TAG);
        this.name = name;
        this.descr = descr;
    }

    public String getName() {
        return name;
    }

    public String getDescr() {
        return descr;
    }

    @Override
    public void toWriter(ClassWriter cw) {
        cw.newNameType(name, descr);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConstNameAndType that = (ConstNameAndType) o;

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
