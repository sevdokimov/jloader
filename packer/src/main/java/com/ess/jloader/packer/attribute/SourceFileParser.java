package com.ess.jloader.packer.attribute;

import com.ess.jloader.packer.AClass;
import com.ess.jloader.packer.consts.CRef;
import com.ess.jloader.packer.consts.Const;
import com.ess.jloader.packer.consts.ConstUft;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class SourceFileParser implements AttributeParser<CRef<ConstUft>> {

    @NotNull
    @Override
    public CRef<ConstUft> parse(AClass aClass, int length, DataInput in) throws IOException {
        return aClass.createRef(ConstUft.class, in);
    }
}
