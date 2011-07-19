package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;
import com.ess.jloader.packer.InvalidClassException;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
@SuppressWarnings({"unchecked"})
public class CRef<T extends Const> {

    private int index;

    private T c;

    private final Class<T> constClazz;

    public CRef(Class<T> constClazz, int index) {
        this.constClazz = constClazz;
        this.index = index;
    }

    public void resolve(AClass aClass) throws InvalidClassException {
        assert index != -1;
        assert c == null;

        c = aClass.getConst(index, constClazz);
        if (c == null) {
            throw new InvalidClassException();
        }

        index = -1;
    }

    public void writeTo(DataOutput out) throws IOException {
        out.writeShort(index);
    }

    @NotNull
    public T get() {
        assert index == -1;
        return c;
    }

}
