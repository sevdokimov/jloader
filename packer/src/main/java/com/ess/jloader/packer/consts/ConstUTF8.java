package com.ess.jloader.packer.consts;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstUTF8 extends Const {

    private String text;

    public ConstUTF8(DataInput in) throws IOException {
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
