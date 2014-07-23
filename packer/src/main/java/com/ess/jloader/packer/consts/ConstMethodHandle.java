package com.ess.jloader.packer.consts;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Sergey Evdokimov
 */
public class ConstMethodHandle extends AbstractConst {

    public static final int TAG = 15;

    private final int kind;
    private final String className;
    private final String name;
    private final String descr;

    public ConstMethodHandle(int kind, @NotNull String className, @NotNull String name, @NotNull String descr) {
        super(TAG);
        this.kind = kind;
        this.className = className;
        this.name = name;
        this.descr = descr;
    }

    @Override
    public void toWriter(ClassWriter cw) {
        cw.newHandle(kind, className, name, descr);
    }

    public int getKind() {
        return kind;
    }

    public String getClassName() {
        return className;
    }

    public String getName() {
        return name;
    }

    public String getDescr() {
        return descr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConstMethodHandle that = (ConstMethodHandle) o;

        if (kind != that.kind) return false;
        if (!className.equals(that.className)) return false;
        if (!descr.equals(that.descr)) return false;
        if (!name.equals(that.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = kind;
        result = 31 * result + className.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + descr.hashCode();
        return result;
    }
}
