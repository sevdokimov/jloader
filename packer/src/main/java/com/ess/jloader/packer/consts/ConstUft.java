package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstUft extends Const {

    private String text;

    public ConstUft(AClass aClass, DataInput in) throws IOException {
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
    public String toString() {
        return text;
    }
}
