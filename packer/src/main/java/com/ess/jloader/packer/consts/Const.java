package com.ess.jloader.packer.consts;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public abstract class Const {

    public boolean isGet2ElementsInPool() {
        return false;
    }

    public abstract byte getCode();

}
