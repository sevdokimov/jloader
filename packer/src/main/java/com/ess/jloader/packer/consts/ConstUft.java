package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;

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

    public String getText() {
        return text;
    }

    @Override
    public byte getCode() {
        return 1;
    }
}
