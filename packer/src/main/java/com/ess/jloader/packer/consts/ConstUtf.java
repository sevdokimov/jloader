package com.ess.jloader.packer.consts;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;

/**
 * @author Sergey Evdokimov
 */
public class ConstUtf extends AbstractConst {

    public static final int TAG = 1;

    private final String value;

    public ConstUtf(@NotNull String value) {
        super(TAG);
        this.value = value;
    }

    @Override
    public void toWriter(ClassWriter cw) {
        cw.newUTF8(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConstUtf constUtf = (ConstUtf) o;

        if (!value.equals(constUtf.value)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
