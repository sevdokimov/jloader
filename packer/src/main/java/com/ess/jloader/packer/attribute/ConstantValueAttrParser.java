package com.ess.jloader.packer.attribute;

import com.ess.jloader.packer.AClass;
import com.ess.jloader.packer.consts.CRef;
import com.ess.jloader.packer.consts.Const;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstantValueAttrParser implements AttributeParser<CRef> {

    public static final ConstantValueAttrParser INSTANCE = new ConstantValueAttrParser();

    @NotNull
    @Override
    public CRef parse(AClass aClass, int length, DataInput in) throws IOException {
        return aClass.createRef(Const.class, in);
    }
}
