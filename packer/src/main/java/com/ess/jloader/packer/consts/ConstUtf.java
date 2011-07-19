package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstUtf extends Const {

    private String text;

    public ConstUtf(AClass aClass, DataInput in) throws IOException {
        text = in.readUTF();
    }

    @NotNull
    public String getText() {
        return text;
    }

    @Override
    public byte getCode() {
        return 1;
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeUTF(text);
    }

    @Override
    public String toString() {
        return text;
    }
}
